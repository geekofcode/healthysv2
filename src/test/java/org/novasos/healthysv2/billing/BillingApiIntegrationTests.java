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

@ActiveProfiles("test")
@SpringBootTest(
    properties = {
      "spring.security.oauth2.resourceserver.jwt.issuer-uri=https://keycloak.example/realms/healthys",
      "spring.security.oauth2.resourceserver.jwt.audiences=healthys-backend-apps",
      "healthys.security.api-client-id=healthys-backend-apps",
      "healthys.security.cors.allowed-origins=http://localhost:5173"
    })
@AutoConfigureMockMvc
@Import({TestcontainersConfiguration.class, BillingApiIntegrationTests.JwtFixtures.class})
@Transactional
class BillingApiIntegrationTests {
  static final UUID ADMIN = UUID.fromString("71000000-0000-0000-0000-000000000001");
  @Autowired MockMvc mvc;
  @Autowired JdbcTemplate jdbc;
  final ObjectMapper json = new ObjectMapper().findAndRegisterModules();

  @Test
  void consultationInvoiceUsesFrozenEpisodePriceAfterTariffChanges() throws Exception {
    Graph g = graph();
    jdbc.update(
        "update care.care_episode set"
            + " tariff_id=?,tariff_price=100,tariff_currency='CAD',tariff_tax_rate=15 where id=?",
        g.tariff,
        g.episode);
    jdbc.update("update billing.care_tariff set unit_price=500 where id=?", g.tariff);
    mvc.perform(
            post("/api/v1/billing/care/episodes/{id}/invoice", g.episode)
                .header(HttpHeaders.AUTHORIZATION, "Bearer admin-" + g.adminSubject)
                .header("X-Profile-ID", g.adminProfile)
                .header("X-Organization-ID", g.organization)
                .header("Idempotency-Key", "frozen-" + g.episode)
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"items\":[{\"tariffId\":\"" + g.tariff + "\",\"quantity\":1}]}"))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.totalAmount").value(115));
  }

  @Test
  void partialCashThenFinalPaymentUnlocksEpisodeAndReplayIsSafe() throws Exception {
    Graph g = graph();
    JsonNode invoice = create(g);
    UUID id = UUID.fromString(invoice.path("id").asText());
    cash(g, id, "40", "payment-001")
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.operationType").value("PAYMENT"));
    assertThat(
            jdbc.queryForObject(
                "select status from care.care_episode where id=?", String.class, g.episode))
        .isEqualTo("DRAFT");
    cash(g, id, "75", "payment-002").andExpect(status().isOk());
    cash(g, id, "75", "payment-002").andExpect(status().isOk());
    assertThat(
            jdbc.queryForObject(
                "select status from care.care_episode where id=?", String.class, g.episode))
        .isEqualTo("ACTIVE");
    assertThat(
            jdbc.queryForObject(
                "select started_at is null and expires_at is null from care.care_episode where"
                    + " id=?",
                Boolean.class,
                g.episode))
        .isTrue();
    mvc.perform(
            get("/api/v1/invoices/{id}", id)
                .header(HttpHeaders.AUTHORIZATION, "Bearer admin-" + g.adminSubject)
                .header("X-Profile-ID", g.adminProfile)
                .header("X-Organization-ID", g.organization))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.status").value("PAID"))
        .andExpect(jsonPath("$.balance").value(0))
        .andExpect(jsonPath("$.payments.length()").value(2));
    assertThat(
            jdbc.queryForObject(
                "select count(*) from billing.cash_operation where invoice_id=?",
                Integer.class,
                id))
        .isEqualTo(2);
  }

  @Test
  void rejectsOverpaymentAndChangedIdempotentPayload() throws Exception {
    Graph g = graph();
    UUID id = UUID.fromString(create(g).path("id").asText());
    cash(g, id, "116", "payment-001").andExpect(status().isConflict());
    cash(g, id, "40", "payment-002").andExpect(status().isOk());
    cash(g, id, "41", "payment-002")
        .andExpect(status().isConflict())
        .andExpect(jsonPath("$.code").value("IDEMPOTENCY_CONFLICT"));
    assertThat(
            jdbc.queryForObject(
                "select amount_paid from billing.invoice where id=?",
                java.math.BigDecimal.class,
                id))
        .isEqualByComparingTo("40");
  }

  @Test
  void refusesLegacyCashAndElectronicPaymentsWithoutChangingInvoice() throws Exception {
    Graph g = graph();
    UUID id = UUID.fromString(create(g).path("id").asText());
    mvc.perform(
            post("/api/v1/invoices/{id}/payments", id)
                .header(HttpHeaders.AUTHORIZATION, "Bearer admin-" + g.adminSubject)
                .header("X-Profile-ID", g.adminProfile)
                .header("X-Organization-ID", g.organization)
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"amount\":115,\"currency\":\"CAD\",\"paymentMethod\":\"CASH\"}"))
        .andExpect(status().isConflict())
        .andExpect(jsonPath("$.code").value("IDEMPOTENT_CASH_REQUIRED"));
    for (String method : List.of("CARD", "MOBILE_MONEY", "INSURANCE")) {
      mvc.perform(
              post("/api/v1/invoices/{id}/payments", id)
                  .header(HttpHeaders.AUTHORIZATION, "Bearer admin-" + g.adminSubject)
                  .header("X-Profile-ID", g.adminProfile)
                  .header("X-Organization-ID", g.organization)
                  .contentType(MediaType.APPLICATION_JSON)
                  .content(
                      "{\"amount\":115,\"currency\":\"CAD\",\"paymentMethod\":\"" + method + "\"}"))
          .andExpect(status().isUnprocessableContent())
          .andExpect(jsonPath("$.code").value("INVALID_PAYMENT"));
    }
    assertThat(
            jdbc.queryForObject(
                "select amount_paid from billing.invoice where id=?",
                java.math.BigDecimal.class,
                id))
        .isEqualByComparingTo("0");
  }

  @Test
  void patientCanReadOnlyOwnInvoiceAndCareInvoiceCannotBeCancelled() throws Exception {
    Graph own = graph();
    UUID id = UUID.fromString(create(own).path("id").asText());
    mvc.perform(
            get("/api/v1/invoices/{id}", id)
                .header(HttpHeaders.AUTHORIZATION, "Bearer patient-" + own.subject)
                .header("X-Profile-ID", own.patient))
        .andExpect(status().isOk());
    Graph other = graph();
    mvc.perform(
            get("/api/v1/invoices/{id}", id)
                .header(HttpHeaders.AUTHORIZATION, "Bearer patient-" + other.subject)
                .header("X-Profile-ID", other.patient))
        .andExpect(status().isForbidden());
    mvc.perform(
            post("/api/v1/invoices/{id}/cancel", id)
                .header(HttpHeaders.AUTHORIZATION, "Bearer admin-" + own.adminSubject)
                .header("X-Profile-ID", own.adminProfile)
                .header("X-Organization-ID", own.organization))
        .andExpect(status().isConflict())
        .andExpect(jsonPath("$.code").value("CARE_INVOICE_IMMUTABLE"));
  }

  @Test
  void refundIsTracedAndRestoresUnpaidState() throws Exception {
    Graph g = graph();
    UUID id = UUID.fromString(create(g).path("id").asText());
    cash(g, id, "115", "payment-001").andExpect(status().isOk());
    String body = "{\"amount\":15,\"currency\":\"CAD\",\"reason\":\"Correction\"}";
    for (int i = 0; i < 2; i++)
      mvc.perform(
              post("/api/v1/billing/care/invoices/{id}/refunds", id)
                  .header(HttpHeaders.AUTHORIZATION, "Bearer staff-" + g.cashierSubject)
                  .header("X-Profile-ID", g.cashierProfile)
                  .header("X-Organization-ID", g.organization)
                  .header("Idempotency-Key", "refund-001")
                  .contentType(MediaType.APPLICATION_JSON)
                  .content(body))
          .andExpect(status().isOk())
          .andExpect(jsonPath("$.operationType").value("REFUND"));
    assertThat(
            jdbc.queryForObject(
                "select status from care.care_episode where id=?", String.class, g.episode))
        .isEqualTo("DRAFT");
    assertThat(
            jdbc.queryForObject(
                "select amount_paid from billing.invoice where id=?",
                java.math.BigDecimal.class,
                id))
        .isEqualByComparingTo("100");
    assertThat(
            jdbc.queryForObject(
                "select count(*) from billing.cash_operation where invoice_id=?",
                Integer.class,
                id))
        .isEqualTo(2);
  }

  private JsonNode create(Graph g) throws Exception {
    String body = "{\"items\":[{\"tariffId\":\"" + g.tariff + "\",\"quantity\":2}]}";
    return json.readTree(
        mvc.perform(
                post("/api/v1/billing/care/episodes/{id}/invoice", g.episode)
                    .header(HttpHeaders.AUTHORIZATION, "Bearer admin-" + g.adminSubject)
                    .header("X-Profile-ID", g.adminProfile)
                    .header("X-Organization-ID", g.organization)
                    .header("Idempotency-Key", "invoice-" + g.episode)
                    .contentType(MediaType.APPLICATION_JSON)
                    .content(body))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.totalAmount").value(115))
            .andReturn()
            .getResponse()
            .getContentAsString());
  }

  private org.springframework.test.web.servlet.ResultActions cash(
      Graph g, UUID id, String amount, String key) throws Exception {
    return mvc.perform(
        post("/api/v1/billing/care/invoices/{id}/cash", id)
            .header(HttpHeaders.AUTHORIZATION, "Bearer staff-" + g.cashierSubject)
            .header("X-Profile-ID", g.cashierProfile)
            .header("X-Organization-ID", g.organization)
            .header("Idempotency-Key", key)
            .contentType(MediaType.APPLICATION_JSON)
            .content("{\"amount\":" + amount + ",\"currency\":\"CAD\"}"));
  }

  private Graph graph() {
    UUID subject = UUID.randomUUID(),
        person = UUID.randomUUID(),
        patient = UUID.randomUUID(),
        organization = UUID.randomUUID();
    UUID episode = UUID.randomUUID(),
        tariff = UUID.randomUUID(),
        cashier = UUID.randomUUID(),
        cashierSubject = UUID.randomUUID(),
        cashierProfile = UUID.randomUUID(),
        adminPerson = UUID.randomUUID(),
        adminSubject = UUID.randomUUID(),
        adminProfile = UUID.randomUUID();
    jdbc.update(
        "insert into identity.person(id,keycloak_user_id,person_number,first_name,last_name,status)"
            + " values (?,?,?,?,?,?)",
        person,
        subject,
        "PER-" + person,
        "Bill",
        "Patient",
        "ACTIVE");
    jdbc.update(
        "insert into patient.patient(id,person_id,patient_number) values (?,?,?)",
        patient,
        person,
        "PAT-" + patient);
    jdbc.update(
        "insert into organization.organization(id,organization_number,name,status) values"
            + " (?,?,?,'ACTIVE')",
        organization,
        "ORG-" + organization,
        "Billing Clinic");
    jdbc.update(
        "insert into licensing.subscription(organization_id,status,period_start,period_end) values"
            + " (?,'active',now()-interval '1 day',now()+interval '1 month')",
        organization);
    jdbc.update(
        "insert into identity.person(id,keycloak_user_id,person_number,first_name,last_name,status)"
            + " values (?,?,?,?,?,?)",
        cashier,
        cashierSubject,
        "PER-" + cashier,
        "Cash",
        "Agent",
        "ACTIVE");
    jdbc.update(
        "insert into licensing.member(id,organization_id,person_id,role,status) values"
            + " (?,?,?,'CASHIER','ACTIVE')",
        cashierProfile,
        organization,
        cashier);
    jdbc.update(
        "insert into identity.person(id,keycloak_user_id,person_number,first_name,last_name,status)"
            + " values (?,?,?,?,?,'ACTIVE')",
        adminPerson,
        adminSubject,
        "PER-" + adminPerson,
        "Hospital",
        "Admin");
    jdbc.update(
        "insert into licensing.member(id,organization_id,person_id,role,status) values"
            + " (?,?,?,'HOSPITAL_ADMIN','ACTIVE')",
        adminProfile,
        organization,
        adminPerson);
    signed(person, patient, "PATIENT", "PATIENT", null);
    signed(cashier, cashierProfile, "CASHIER", "ORGANIZATION_MEMBERSHIP", organization);
    signed(adminPerson, adminProfile, "HOSPITAL_ADMIN", "ORGANIZATION_MEMBERSHIP", organization);
    jdbc.update(
        "insert into"
            + " care.care_episode(id,patient_id,organization_id,reason,status,follow_up_seconds,created_at,retention_expires_at)"
            + " values (?,?,?,'Reception','DRAFT',172800,now(),now()+interval '30 years')",
        episode,
        patient,
        organization);
    jdbc.update(
        "insert into"
            + " billing.care_tariff(id,organization_id,code,description,currency,unit_price,tax_rate)"
            + " values (?,?,?,'Consultation','CAD',50,15)",
        tariff,
        organization,
        "CONS-" + tariff);
    return new Graph(
        person,
        patient,
        organization,
        subject,
        episode,
        tariff,
        cashierSubject,
        cashierProfile,
        adminSubject,
        adminProfile);
  }

  private void signed(UUID actor, UUID profile, String type, String source, UUID organization) {
    String category =
        type.equals("PATIENT") ? "PATIENT" : type.equals("HOSPITAL_ADMIN") ? "HOSPITAL" : "STAFF";
    UUID template = UUID.randomUUID();
    Integer version =
        jdbc.queryForObject(
            "select coalesce(max(version),0)+1 from profilecontract.template where category=?",
            Integer.class,
            category);
    jdbc.update(
        "insert into"
            + " profilecontract.template(id,category,version,terms_url,terms_version,body_text,created_by,published_at)"
            + " values(?,?,?,'https://example.test/terms','1','Integration agreement',?,now())",
        template,
        category,
        version,
        actor);
    jdbc.update(
        """
        insert into profilecontract.agreement(actor_person_id,profile_id,template_id,template_version,category,profile_type,source_type,source_id,organization_id,profile_label,organization_name,patient_name,signer_name,signer_email,terms_url,terms_version,body_snapshot,accepted_terms,guardian_acknowledgement,signature_png,signature_sha256,snapshot_sha256)
        values(?,?,?,?,?,?,?,?,?,'Selected profile','Clinic','Patient','Signer','signer@example.test','https://example.test/terms','1','Integration agreement',true,false,?,?,?)
        """,
        actor,
        profile,
        template,
        version,
        category,
        type,
        source,
        profile,
        organization,
        new byte[] {1, 2, 3},
        "a".repeat(64),
        "b".repeat(64));
  }

  record Graph(
      UUID person,
      UUID patient,
      UUID organization,
      UUID subject,
      UUID episode,
      UUID tariff,
      UUID cashierSubject,
      UUID cashierProfile,
      UUID adminSubject,
      UUID adminProfile) {}

  @TestConfiguration(proxyBeanMethods = false)
  static class JwtFixtures {
    @Bean
    JwtDecoder jwtDecoder() {
      return token -> {
        boolean patient = token.startsWith("patient-");
        String subject =
            patient
                ? token.substring(8)
                : token.startsWith("staff-")
                    ? token.substring(6)
                    : token.startsWith("admin-") ? token.substring(6) : ADMIN.toString();
        Instant now = Instant.now();
        return Jwt.withTokenValue(token)
            .header("alg", "RS256")
            .subject(subject)
            .issuer("https://keycloak.example/realms/healthys")
            .audience(List.of("healthys-backend-apps"))
            .issuedAt(now)
            .expiresAt(now.plusSeconds(300))
            .claim(
                "realm_access",
                Map.of(
                    "roles",
                    patient
                        ? List.of("PATIENT")
                        : token.startsWith("staff-")
                            ? List.of("CASHIER")
                            : List.of("HOSPITAL_ADMIN")))
            .claim("resource_access", Map.of())
            .build();
      };
    }
  }
}
