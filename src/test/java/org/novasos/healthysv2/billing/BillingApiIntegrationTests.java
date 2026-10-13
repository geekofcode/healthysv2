package org.novasos.healthysv2.billing;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;
import com.fasterxml.jackson.databind.*;
import java.time.*;
import java.util.*;
import org.junit.jupiter.api.Test;
import org.novasos.healthysv2.TestcontainersConfiguration;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.*;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.context.annotation.*;
import org.springframework.http.*;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.oauth2.jwt.*;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.transaction.annotation.Transactional;

@ActiveProfiles("test") @SpringBootTest(properties={"spring.security.oauth2.resourceserver.jwt.issuer-uri=https://keycloak.example/realms/healthys","spring.security.oauth2.resourceserver.jwt.audiences=healthys-backend-apps","healthys.security.api-client-id=healthys-backend-apps","healthys.security.cors.allowed-origins=http://localhost:5173"})
@AutoConfigureMockMvc @Import({TestcontainersConfiguration.class,BillingApiIntegrationTests.JwtFixtures.class}) @Transactional
class BillingApiIntegrationTests {
    static final UUID ADMIN=UUID.fromString("71000000-0000-0000-0000-000000000001");
    @Autowired MockMvc mvc; @Autowired JdbcTemplate jdbc; final ObjectMapper json=new ObjectMapper().findAndRegisterModules();

    @Test void partialCashThenFinalPaymentUnlocksEpisodeAndReplayIsSafe() throws Exception {
        Graph g=graph(); JsonNode invoice=create(g); UUID id=UUID.fromString(invoice.path("id").asText());
        cash(g,id,"40","payment-001").andExpect(status().isOk()).andExpect(jsonPath("$.operationType").value("PAYMENT"));
        assertThat(jdbc.queryForObject("select status from care.care_episode where id=?",String.class,g.episode)).isEqualTo("AWAITING_PAYMENT");
        cash(g,id,"75","payment-002").andExpect(status().isOk());
        cash(g,id,"75","payment-002").andExpect(status().isOk());
        assertThat(jdbc.queryForObject("select status from care.care_episode where id=?",String.class,g.episode)).isEqualTo("READY");
        mvc.perform(get("/api/v1/invoices/{id}",id).header(HttpHeaders.AUTHORIZATION,"Bearer admin"))
                .andExpect(status().isOk()).andExpect(jsonPath("$.status").value("PAID"))
                .andExpect(jsonPath("$.balance").value(0)).andExpect(jsonPath("$.payments.length()").value(2));
        assertThat(jdbc.queryForObject("select count(*) from billing.cash_operation where invoice_id=?",Integer.class,id)).isEqualTo(2);
    }

    @Test void rejectsOverpaymentAndChangedIdempotentPayload() throws Exception {
        Graph g=graph(); UUID id=UUID.fromString(create(g).path("id").asText());
        cash(g,id,"116","payment-001").andExpect(status().isConflict());
        cash(g,id,"40","payment-002").andExpect(status().isOk());
        cash(g,id,"41","payment-002").andExpect(status().isConflict()).andExpect(jsonPath("$.code").value("IDEMPOTENCY_CONFLICT"));
        assertThat(jdbc.queryForObject("select amount_paid from billing.invoice where id=?",java.math.BigDecimal.class,id)).isEqualByComparingTo("40");
    }

    @Test void refusesLegacyCashAndElectronicPaymentsWithoutChangingInvoice() throws Exception {
        Graph g=graph(); UUID id=UUID.fromString(create(g).path("id").asText());
        mvc.perform(post("/api/v1/invoices/{id}/payments",id).header(HttpHeaders.AUTHORIZATION,"Bearer admin").header("X-Organization-ID",g.organization)
                .contentType(MediaType.APPLICATION_JSON).content("{\"amount\":115,\"currency\":\"CAD\",\"paymentMethod\":\"CASH\"}"))
                .andExpect(status().isConflict()).andExpect(jsonPath("$.code").value("IDEMPOTENT_CASH_REQUIRED"));
        for(String method:List.of("CARD","MOBILE_MONEY","INSURANCE")) {
            mvc.perform(post("/api/v1/invoices/{id}/payments",id).header(HttpHeaders.AUTHORIZATION,"Bearer admin").header("X-Organization-ID",g.organization)
                .contentType(MediaType.APPLICATION_JSON).content("{\"amount\":115,\"currency\":\"CAD\",\"paymentMethod\":\""+method+"\"}"))
                .andExpect(status().isUnprocessableContent()).andExpect(jsonPath("$.code").value("INVALID_PAYMENT"));
        }
        assertThat(jdbc.queryForObject("select amount_paid from billing.invoice where id=?",java.math.BigDecimal.class,id)).isEqualByComparingTo("0");
    }

    @Test void patientCanReadOnlyOwnInvoiceAndCareInvoiceCannotBeCancelled() throws Exception {
        Graph own=graph(); UUID id=UUID.fromString(create(own).path("id").asText());
        mvc.perform(get("/api/v1/invoices/{id}",id).header(HttpHeaders.AUTHORIZATION,"Bearer patient-"+own.subject)).andExpect(status().isOk());
        Graph other=graph();
        mvc.perform(get("/api/v1/invoices/{id}",id).header(HttpHeaders.AUTHORIZATION,"Bearer patient-"+other.subject)).andExpect(status().isForbidden());
        mvc.perform(post("/api/v1/invoices/{id}/cancel",id).header(HttpHeaders.AUTHORIZATION,"Bearer admin"))
            .andExpect(status().isConflict()).andExpect(jsonPath("$.code").value("CARE_INVOICE_IMMUTABLE"));
    }

    @Test void refundIsTracedAndRestoresUnpaidState() throws Exception {
        Graph g=graph(); UUID id=UUID.fromString(create(g).path("id").asText());
        cash(g,id,"115","payment-001").andExpect(status().isOk());
        String body="{\"amount\":15,\"currency\":\"CAD\",\"reason\":\"Correction\"}";
        for(int i=0;i<2;i++) mvc.perform(post("/api/v1/billing/care/invoices/{id}/refunds",id)
            .header(HttpHeaders.AUTHORIZATION,"Bearer staff-"+g.cashierSubject).header("X-Organization-ID",g.organization)
            .header("Idempotency-Key","refund-001").contentType(MediaType.APPLICATION_JSON).content(body))
            .andExpect(status().isOk()).andExpect(jsonPath("$.operationType").value("REFUND"));
        assertThat(jdbc.queryForObject("select status from care.care_episode where id=?",String.class,g.episode)).isEqualTo("AWAITING_PAYMENT");
        assertThat(jdbc.queryForObject("select amount_paid from billing.invoice where id=?",java.math.BigDecimal.class,id)).isEqualByComparingTo("100");
        assertThat(jdbc.queryForObject("select count(*) from billing.cash_operation where invoice_id=?",Integer.class,id)).isEqualTo(2);
    }

    private JsonNode create(Graph g) throws Exception {
        String body="{\"items\":[{\"tariffId\":\""+g.tariff+"\",\"quantity\":2}]}";
        return json.readTree(mvc.perform(post("/api/v1/billing/care/episodes/{id}/invoice",g.episode)
            .header(HttpHeaders.AUTHORIZATION,"Bearer admin").header("X-Organization-ID",g.organization).header("Idempotency-Key","invoice-"+g.episode)
            .contentType(MediaType.APPLICATION_JSON).content(body)).andExpect(status().isOk())
            .andExpect(jsonPath("$.totalAmount").value(115)).andReturn().getResponse().getContentAsString());
    }
    private org.springframework.test.web.servlet.ResultActions cash(Graph g,UUID id,String amount,String key) throws Exception {
        return mvc.perform(post("/api/v1/billing/care/invoices/{id}/cash",id)
            .header(HttpHeaders.AUTHORIZATION,"Bearer staff-"+g.cashierSubject).header("X-Organization-ID",g.organization)
            .header("Idempotency-Key",key).contentType(MediaType.APPLICATION_JSON)
            .content("{\"amount\":"+amount+",\"currency\":\"CAD\"}"));
    }
    private Graph graph() {
        UUID subject=UUID.randomUUID(),person=UUID.randomUUID(),patient=UUID.randomUUID(),organization=UUID.randomUUID();
        UUID episode=UUID.randomUUID(),tariff=UUID.randomUUID(),cashier=UUID.randomUUID(),cashierSubject=UUID.randomUUID();
        jdbc.update("insert into identity.person(id,keycloak_user_id,person_number,first_name,last_name,status) values (?,?,?,?,?,?)",person,subject,"PER-"+person,"Bill","Patient","ACTIVE");
        jdbc.update("insert into patient.patient(id,person_id,patient_number) values (?,?,?)",patient,person,"PAT-"+patient);
        jdbc.update("insert into organization.organization(id,organization_number,name,status) values (?,?,?,'ACTIVE')",organization,"ORG-"+organization,"Billing Clinic");
        jdbc.update("insert into licensing.subscription(organization_id,status,period_start,period_end) values (?,'active',now()-interval '1 day',now()+interval '1 month')",organization);
        jdbc.update("insert into identity.person(id,keycloak_user_id,person_number,first_name,last_name,status) values (?,?,?,?,?,?)",cashier,cashierSubject,"PER-"+cashier,"Cash","Agent","ACTIVE");
        jdbc.update("insert into licensing.member(organization_id,person_id,role,status) values (?,?,'CASHIER','ACTIVE')",organization,cashier);
        jdbc.update("insert into care.care_episode(id,patient_id,organization_id,reason,status,follow_up_seconds,created_at,retention_expires_at) values (?,?,?,'Reception','AWAITING_PAYMENT',172800,now(),now()+interval '30 years')",episode,patient,organization);
        jdbc.update("insert into billing.care_tariff(id,organization_id,code,description,currency,unit_price,tax_rate) values (?,?,?,'Consultation','CAD',50,15)",tariff,organization,"CONS-"+tariff);
        return new Graph(person,patient,organization,subject,episode,tariff,cashierSubject);
    }
    record Graph(UUID person,UUID patient,UUID organization,UUID subject,UUID episode,UUID tariff,UUID cashierSubject){}
    @TestConfiguration(proxyBeanMethods=false) static class JwtFixtures{@Bean JwtDecoder jwtDecoder(){return token->{boolean patient=token.startsWith("patient-");String subject=patient?token.substring(8):token.startsWith("staff-")?token.substring(6):ADMIN.toString();Instant now=Instant.now();return Jwt.withTokenValue(token).header("alg","RS256").subject(subject).issuer("https://keycloak.example/realms/healthys").audience(List.of("healthys-backend-apps")).issuedAt(now).expiresAt(now.plusSeconds(300)).claim("realm_access",Map.of("roles",patient?List.of("PATIENT"):token.startsWith("staff-")?List.of("CASHIER"):List.of("PLATFORM_ADMIN"))).claim("resource_access",Map.of()).build();};}}
}
