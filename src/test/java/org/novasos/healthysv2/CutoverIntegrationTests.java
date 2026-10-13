package org.novasos.healthysv2;

import static org.assertj.core.api.Assertions.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.fasterxml.jackson.databind.ObjectMapper;
import java.time.Instant;
import java.util.*;
import org.junit.jupiter.api.*;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.*;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.context.annotation.*;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.oauth2.jwt.*;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;

@SpringBootTest(
    properties = {
      "spring.security.oauth2.resourceserver.jwt.issuer-uri=https://keycloak.example/realms/healthys",
      "spring.security.oauth2.resourceserver.jwt.audiences=healthys-backend-apps",
      "healthys.security.api-client-id=healthys-backend-apps"
    })
@ActiveProfiles("test")
@AutoConfigureMockMvc
@Import({TestcontainersConfiguration.class, CutoverIntegrationTests.Tokens.class})
class CutoverIntegrationTests {
  @Autowired JdbcTemplate db;
  @Autowired MockMvc mvc;
  @Autowired ObjectMapper json;
  UUID person, subject;

  @BeforeEach
  void setup() {
    resetGate();
    person = UUID.randomUUID();
    subject = UUID.randomUUID();
    db.update(
        "insert into identity.person(id,person_number,keycloak_user_id,first_name,last_name)"
            + " values(?,?,?,'Synthetic','Admin')",
        person,
        person.toString(),
        subject);
  }

  @AfterEach
  void resetGate() {
    db.update(
        "update care.cutover_state set"
            + " mode='LEGACY',migration_run_id=null,evidence='{}',approved_by=null,approved_at=null"
            + " where singleton");
  }

  String token() {
    return "Bearer " + subject;
  }

  UUID run(String state, String proof) {
    UUID id = UUID.randomUUID();
    db.update(
        "insert into"
            + " care.migration_run(id,source_fingerprint,status,source_snapshot,report,evidence)"
            + " values(?,?,?,'{}','{\"anomalies\":[]}',?::jsonb)",
        id,
        id.toString(),
        state,
        proof);
    return id;
  }

  @Test
  void completedImportWithoutRestoreEvidenceKeepsHistoricalCommandsAvailable() throws Exception {
    UUID id = run("APPLIED", "{}");
    mvc.perform(
            post("/api/v1/care/cutover/activate")
                .header("Authorization", token())
                .contentType(MediaType.APPLICATION_JSON)
                .content(json.writeValueAsString(Map.of("migrationRunId", id))))
        .andExpect(status().isUnprocessableEntity());
    assertThat(
            db.queryForObject("select mode from care.cutover_state where singleton", String.class))
        .isEqualTo("LEGACY");
  }

  @Test
  void verifiedCutoverMakesLegacyCommandsReadOnlyInHttpAndDatabase() throws Exception {
    Map<String, Object> proof = new HashMap<>();
    proof.put("journey", Map.of("validated", true));
    proof.put("restoration", Map.of("validated", true));
    for (String k :
        List.of(
            "backup_manifest_sha256",
            "restoration_test_sha256",
            "cash_e2e_sha256",
            "migration_review_sha256")) proof.put(k, "a".repeat(64));
    UUID id = run("VERIFIED", json.writeValueAsString(proof));
    mvc.perform(
            post("/api/v1/care/cutover/activate")
                .header("Authorization", token())
                .contentType(MediaType.APPLICATION_JSON)
                .content(json.writeValueAsString(Map.of("migrationRunId", id))))
        .andExpect(status().isOk());
    mvc.perform(
            post("/api/v1/appointments")
                .header("Authorization", token())
                .contentType(MediaType.APPLICATION_JSON)
                .content("{}"))
        .andExpect(status().isUnprocessableEntity());
    UUID org = UUID.randomUUID(), patient = UUID.randomUUID();
    db.update(
        "insert into organization.organization(id,organization_number,name)"
            + " values(?,?,'Synthetic')",
        org,
        org.toString());
    db.update(
        "insert into patient.patient(id,person_id,patient_number) values(?,?,?)",
        patient,
        person,
        patient.toString());
    assertThatThrownBy(
            () ->
                db.update(
                    "insert into"
                        + " appointment.appointment(appointment_number,patient_id,organization_id,type,scheduled_start,scheduled_end)"
                        + " values(?,?,?,'CONSULTATION',now(),now()+interval '1 hour')",
                    UUID.randomUUID().toString(),
                    patient,
                    org))
        .isInstanceOf(org.springframework.dao.DataAccessException.class);
  }

  @Test
  void deletionRequestRemainsAccessibleAfterLocalAccountAnonymization() throws Exception {
    String response =
        mvc.perform(
                post("/api/v1/me/data-deletion")
                    .header("Authorization", token())
                    .header("Idempotency-Key", "synthetic-deletion"))
            .andExpect(status().isOk())
            .andReturn()
            .getResponse()
            .getContentAsString();
    String id = json.readTree(response).path("id").asText();
    db.update("update identity.person set status='INACTIVE' where id=?", person);
    mvc.perform(get("/api/v1/me/data-deletion/" + id).header("Authorization", token()))
        .andExpect(status().isOk());
    mvc.perform(get("/api/v1/care/cutover").header("Authorization", token()))
        .andExpect(status().isForbidden());
  }

  @TestConfiguration(proxyBeanMethods = false)
  static class Tokens {
    @Bean
    JwtDecoder jwtDecoder() {
      return token ->
          Jwt.withTokenValue(token)
              .header("alg", "none")
              .subject(token)
              .issuer("https://keycloak.example/realms/healthys")
              .audience(List.of("healthys-backend-apps"))
              .issuedAt(Instant.now())
              .expiresAt(Instant.now().plusSeconds(300))
              .claim("realm_access", Map.of("roles", List.of("PLATFORM_ADMIN")))
              .build();
    }
  }
}
