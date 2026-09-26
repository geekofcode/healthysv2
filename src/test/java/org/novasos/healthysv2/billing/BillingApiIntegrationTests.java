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
import org.springframework.boot.test.context.SpringBootTest;
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

    @Test void createsIssuesAndPaysAnInvoice() throws Exception {
        Graph g=graph(); JsonNode invoice=create(g); UUID id=UUID.fromString(invoice.path("id").asText());
        mvc.perform(post("/api/v1/invoices/{id}/issue",id).header(HttpHeaders.AUTHORIZATION,"Bearer admin"))
                .andExpect(status().isOk()).andExpect(jsonPath("$.status").value("ISSUED"));
        payment(id,"40").andExpect(status().isCreated()).andExpect(jsonPath("$.paymentMethod").value("MOBILE_MONEY"));
        payment(id,"75").andExpect(status().isCreated());
        mvc.perform(get("/api/v1/invoices/{id}",id).header(HttpHeaders.AUTHORIZATION,"Bearer admin"))
                .andExpect(status().isOk()).andExpect(jsonPath("$.status").value("PAID"))
                .andExpect(jsonPath("$.balance").value(0)).andExpect(jsonPath("$.payments.length()").value(2));
        mvc.perform(get("/api/v1/invoices").header(HttpHeaders.AUTHORIZATION,"Bearer admin").param("patientId",g.patient.toString()))
                .andExpect(status().isOk()).andExpect(jsonPath("$.content[0].invoiceNumber").exists());
        assertThat(jdbc.queryForObject("select count(*) from audit.audit_log where module='BILLING' and entity_type='Payment'",Integer.class)).isEqualTo(2);
    }

    @Test void rejectsOverpaymentAndInvalidEncounter() throws Exception {
        Graph g=graph(); UUID id=UUID.fromString(create(g).path("id").asText());
        mvc.perform(post("/api/v1/invoices/{id}/issue",id).header(HttpHeaders.AUTHORIZATION,"Bearer admin")).andExpect(status().isOk());
        payment(id,"116").andExpect(status().isUnprocessableContent()).andExpect(jsonPath("$.code").value("INVALID_PAYMENT"));
        mvc.perform(post("/api/v1/invoices").header(HttpHeaders.AUTHORIZATION,"Bearer admin").contentType(MediaType.APPLICATION_JSON)
                .content(invoiceBody(g,UUID.randomUUID()))).andExpect(status().isUnprocessableContent()).andExpect(jsonPath("$.code").value("INVOICE_ENCOUNTER_MISMATCH"));
    }

    @Test void patientCanReadOnlyOwnInvoice() throws Exception {
        Graph own=graph(); JsonNode invoice=create(own); UUID id=UUID.fromString(invoice.path("id").asText());
        mvc.perform(get("/api/v1/invoices/{id}",id).header(HttpHeaders.AUTHORIZATION,"Bearer patient:"+own.person))
                .andExpect(status().isOk());
        Graph other=graph();
        mvc.perform(get("/api/v1/invoices/{id}",id).header(HttpHeaders.AUTHORIZATION,"Bearer patient:"+other.person))
                .andExpect(status().isForbidden());
    }

    private JsonNode create(Graph g)throws Exception{return json.readTree(mvc.perform(post("/api/v1/invoices").header(HttpHeaders.AUTHORIZATION,"Bearer admin").contentType(MediaType.APPLICATION_JSON).content(invoiceBody(g,null))).andExpect(status().isCreated()).andExpect(jsonPath("$.totalAmount").value(115)).andReturn().getResponse().getContentAsString());}
    private String invoiceBody(Graph g,UUID encounter){return "{\"patientId\":\"%s\",\"organizationId\":\"%s\",%s\"currency\":\"CAD\",\"items\":[{\"itemType\":\"CONSULTATION\",\"description\":\"Medical consultation\",\"quantity\":2,\"unitPrice\":50,\"taxAmount\":15}]}".formatted(g.patient,g.organization,encounter==null?"":"\"encounterId\":\""+encounter+"\",");}
    private org.springframework.test.web.servlet.ResultActions payment(UUID id,String amount)throws Exception{return mvc.perform(post("/api/v1/invoices/{id}/payments",id).header(HttpHeaders.AUTHORIZATION,"Bearer admin").contentType(MediaType.APPLICATION_JSON).content("{\"amount\":"+amount+",\"currency\":\"CAD\",\"paymentMethod\":\"MOBILE_MONEY\",\"provider\":\"MTN\",\"externalTransactionId\":\""+UUID.randomUUID()+"\"}"));}
    private Graph graph(){UUID person=UUID.randomUUID(),patient=UUID.randomUUID(),organization=UUID.randomUUID();jdbc.update("insert into identity.person(id,person_number,first_name,last_name,status) values (?,?,?,?,?)",person,"PER-"+person,"Bill","Patient","ACTIVE");jdbc.update("insert into patient.patient(id,person_id,patient_number) values (?,?,?)",patient,person,"PAT-"+patient);jdbc.update("insert into organization.organization(id,organization_number,name) values (?,?,?)",organization,"ORG-"+organization,"Billing Clinic");return new Graph(person,patient,organization);}
    record Graph(UUID person,UUID patient,UUID organization){}
    @TestConfiguration(proxyBeanMethods=false) static class JwtFixtures{@Bean JwtDecoder jwtDecoder(){return token->{boolean patient=token.startsWith("patient:");String subject=patient?token.substring(8):ADMIN.toString();Instant now=Instant.now();return Jwt.withTokenValue(token).header("alg","RS256").subject(subject).issuer("https://keycloak.example/realms/healthys").audience(List.of("healthys-backend-apps")).issuedAt(now).expiresAt(now.plusSeconds(300)).claim("realm_access",Map.of("roles",List.of(patient?"PATIENT":"PLATFORM_ADMIN"))).claim("resource_access",Map.of()).build();};}}
}
