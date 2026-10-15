package org.novasos.healthysv2;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.sql.Timestamp;
import java.time.*;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.novasos.healthysv2.profilecontract.api.ContractAccess;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Import;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.jwt.JwtDecoder;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;

/**
 * Real HTTP/security/domain/database cash journey. Business services, profiles and entitlements are
 * persisted; contract signatures are tested separately by profilecontract tests.
 */
@ActiveProfiles("test")
@SpringBootTest(
    properties = {
      "spring.security.oauth2.resourceserver.jwt.issuer-uri=https://keycloak.example/realms/healthys",
      "spring.security.oauth2.resourceserver.jwt.audiences=healthys-backend-apps",
      "healthys.security.api-client-id=healthys-backend-apps",
      "healthys.ai.enabled=false",
      "STAFF_ACCOUNT_PROVISIONING_ENABLED=false",
      "DATA_DELETION_WORKER_ENABLED=false",
      "DATA_RETENTION_PURGE_ENABLED=false",
      "healthys.ai.service-token=integration-test-private-token-32-characters",
      "healthys.ai.poll-interval-ms=3600000",
      "healthys.ai.audio-sweep-interval-ms=3600000"
    })
@AutoConfigureMockMvc
@Import({TestcontainersConfiguration.class, CashCareJourneyIntegrationTests.Tokens.class})
class CashCareJourneyIntegrationTests {
  @Autowired MockMvc mvc;
  @Autowired ObjectMapper json;
  @Autowired JdbcTemplate db;
  @MockitoBean ContractAccess contracts;
  private static final Map<String, Actor> ACTORS = new ConcurrentHashMap<>();
  private UUID org, otherOrg, patient, specialty, doctor, secondDoctor, consultationTariff;
  private String nurse, cashier, physician, secondPhysician, foreignNurse, admin;
  private Instant slot;

  @BeforeEach
  void fixture() {
    org = organization();
    otherOrg = organization();
    specialty = UUID.randomUUID();
    db.update(
        "insert into catalog.speciality_catalog(id,code,name,active) values(?,?,?,true)",
        specialty,
        "J-" + specialty,
        "Journey speciality");
    consultationTariff = UUID.randomUUID();
    db.update(
        "insert into"
            + " billing.care_tariff(id,organization_id,code,description,currency,unit_price,tax_rate)"
            + " values(?,?,?,'Consultation','XAF',100,0)",
        consultationTariff,
        org,
        "CONS-" + consultationTariff);
    db.update(
        "insert into"
            + " care.speciality_policy(organization_id,speciality_id,tariff_id,follow_up_seconds)"
            + " values(?,?,?,604800)",
        org,
        specialty,
        consultationTariff);
    nurse = actor(org, "NURSE");
    cashier = actor(org, "CASHIER");
    admin = actor(org, "HOSPITAL_ADMIN");
    physician = actor(org, "DOCTOR");
    secondPhysician = actor(org, "DOCTOR");
    foreignNurse = actor(otherOrg, "NURSE");
    doctor = professional(physician);
    secondDoctor = professional(secondPhysician);
    professional(nurse);
    professional(foreignNurse);
    UUID person = person();
    patient = UUID.randomUUID();
    db.update(
        "insert into patient.patient(id,person_id,patient_number) values(?,?,?)",
        patient,
        person,
        "P-" + patient);
    slot = Instant.now().plusSeconds(60).truncatedTo(java.time.temporal.ChronoUnit.MILLIS);
  }

  @Test
  void receptionCashSchedulingConsultationPrescriptionAndFreeReturnWithoutAi() throws Exception {
    JsonNode episode = open();
    UUID episodeId = id(episode), visit = id(episode.path("visits").get(0));
    assertThat(episode.path("startedAt").isNull()).isTrue();
    assertThat(episode.path("status").asText()).isEqualTo("DRAFT");
    assertThat(episode.path("tariffId").asText()).isEqualTo(consultationTariff.toString());
    assertThat(episode.path("followUpSeconds").asLong()).isEqualTo(604800);
    call(
        nurse,
        put(visitPath(visit) + "/intake"),
        Map.of(
            "observations",
            "Accueil initial",
            "vitals",
            Map.of("temperatureC", 37.2, "systolicBp", 120, "diastolicBp", 80)),
        200);
    UUID invoice = invoice(episodeId);
    cash(invoice, "payment-1", 40);
    assertThat(
            read(nurse, "/api/v1/billing/care/episodes/" + episodeId + "/invoice")
                .path("balance")
                .asInt())
        .isEqualTo(60);
    cash(invoice, "payment-2", 60);
    cash(invoice, "payment-2", 60); // Retry must reuse the same receipt, never debit twice.
    assertThat(read(cashier, "/api/v1/billing/care/invoices/" + invoice + "/receipts").size())
        .isEqualTo(2);
    JsonNode programmed = program(visit, doctor, slot, 0);
    JsonNode calendar =
        read(physician, "/api/v1/care/calendar?date=" + LocalDate.now(ZoneOffset.UTC));
    assertThat(calendar.path("visits").get(0).path("visitId").asText()).isEqualTo(visit.toString());
    JsonNode present =
        call(
            nurse,
            post("/api/v1/care/queue/presence"),
            Map.of(
                "visitId",
                visit,
                "present",
                true,
                "expectedVersion",
                calendar.path("version").asLong()),
            200);
    call(
        physician,
        post("/api/v1/care/queue/next"),
        Map.of(
            "professionalId",
            doctor,
            "date",
            LocalDate.now(ZoneOffset.UTC).toString(),
            "expectedVersion",
            present.path("version").asLong()),
        200);
    JsonNode current = read(nurse, visitPath(visit));
    JsonNode started =
        call(
            physician,
            post(visitPath(visit) + "/start"),
            Map.of("version", current.path("version").asLong()),
            200);
    JsonNode report =
        call(
            physician,
            put(visitPath(visit) + "/report"),
            Map.of(
                "version",
                0,
                "reason",
                "Céphalée",
                "observations",
                "Examen normal",
                "diagnosis",
                "Céphalée simple",
                "treatment",
                "Repos",
                "followUp",
                "Contrôle"),
            200);
    JsonNode finalized =
        call(
            physician,
            post(visitPath(visit) + "/report/finalize"),
            Map.of("version", report.path("version").asLong()),
            200);
    assertThat(finalized.path("status").asText()).isEqualTo("VALIDATED");
    assertThat(finalized.path("revisions").size()).isEqualTo(1);
    JsonNode rx =
        call(
            physician,
            put(visitPath(visit) + "/prescription"),
            Map.of(
                "expectedVersion",
                0,
                "items",
                List.of(
                    Map.of(
                        "medication",
                        "Paracétamol",
                        "dosage",
                        "500 mg",
                        "route",
                        "ORAL",
                        "frequency",
                        "Au besoin",
                        "duration",
                        "2 jours",
                        "instructions",
                        "Respecter la posologie"))),
            200);
    assertThat(
            call(
                    physician,
                    post(visitPath(visit) + "/prescription/finalize"),
                    Map.of("expectedVersion", rx.path("version").asLong()),
                    200)
                .path("status")
                .asText())
        .isEqualTo("VALIDATED");
    Instant followSlot = slot.plusSeconds(86400);
    Map<String, Object> followRequest =
        Map.of(
            "professionalId",
            secondDoctor,
            "scheduledAt",
            followSlot.toString(),
            "reason",
            "Contrôle");
    JsonNode follow =
        call(
            nurse,
            post("/api/v1/care/episodes/" + episodeId + "/follow-ups")
                .header("Idempotency-Key", "free-return"),
            followRequest,
            201);
    assertThat(
            db.queryForObject(
                "select parent_visit_id from care.care_visit where id=?", UUID.class, id(follow)))
        .isEqualTo(visit);
    assertThat(
            id(
                call(
                    nurse,
                    post("/api/v1/care/episodes/" + episodeId + "/follow-ups")
                        .header("Idempotency-Key", "free-return"),
                    followRequest,
                    201)))
        .isEqualTo(id(follow));
    call(
        nurse,
        put(visitPath(id(follow)) + "/intake"),
        Map.of("observations", "Retour de suivi", "vitals", Map.of("temperatureC", 36.8)),
        200);
    assertThat(read(nurse, visitPath(id(follow)) + "/intake").path("observations").asText())
        .isEqualTo("Retour de suivi");
    assertThat(read(physician, visitPath(visit) + "/intake").path("observations").asText())
        .isEqualTo("Accueil initial");
    JsonNode after = read(nurse, "/api/v1/care/episodes/" + episodeId);
    assertThat(
            Duration.between(
                    Instant.parse(after.path("startedAt").asText()),
                    Instant.parse(started.path("episodeStartedAt").asText()))
                .abs())
        .isLessThanOrEqualTo(Duration.ofNanos(1000));
    assertThat(
            Duration.between(
                    Instant.parse(after.path("expiresAt").asText()),
                    Instant.parse(started.path("episodeExpiresAt").asText()))
                .abs())
        .isLessThanOrEqualTo(Duration.ofNanos(1000));
    assertThat(after.path("visits").size()).isEqualTo(2);
    assertThat(
            db.queryForObject(
                "select count(*) from billing.care_invoice where episode_id=?",
                Integer.class,
                episodeId))
        .isEqualTo(1);
    assertThat(
            read(cashier, "/api/v1/billing/care/episodes/" + episodeId + "/invoice")
                .path("balance")
                .asInt())
        .isZero();
    // The manual encounter succeeded above although its subscription excludes AI.
    call(
        secondPhysician,
        post(visitPath(id(follow)) + "/ai/jobs").header("Idempotency-Key", "no-ai"),
        Map.of("kind", "SUMMARY"),
        403);
    assertThat(
            db.queryForObject(
                "select count(*) from care.ai_job where visit_id=?", Integer.class, id(follow)))
        .isZero();
  }

  @Test
  void laterDateAndDoctorReplacementKeepVisitInvoiceAndRevokeOldDoctorAccess() throws Exception {
    JsonNode episode = open();
    UUID episodeId = id(episode), visit = id(episode.path("visits").get(0));
    UUID invoice = invoice(episodeId);
    cash(invoice, "paid", 100);
    JsonNode first = program(visit, doctor, slot, 0);
    call(physician, get(visitPath(visit) + "/report"), null, 200);
    JsonNode moved =
        program(visit, doctor, slot.plusSeconds(86400), first.path("version").asLong());
    JsonNode replaced =
        program(visit, secondDoctor, slot.plusSeconds(86400), moved.path("version").asLong());
    assertThat(id(replaced)).isEqualTo(visit);
    assertThat(replaced.path("episodeId").asText()).isEqualTo(episodeId.toString());
    assertThat(replaced.path("history").size()).isEqualTo(3);
    assertThat(id(read(cashier, "/api/v1/billing/care/episodes/" + episodeId + "/invoice")))
        .isEqualTo(invoice);
    assertThat(read(cashier, "/api/v1/billing/care/invoices/" + invoice + "/receipts").size())
        .isEqualTo(1);
    call(secondPhysician, get(visitPath(visit) + "/report"), null, 200);
    call(physician, get(visitPath(visit) + "/report"), null, 403);
    call(foreignNurse, get("/api/v1/care/episodes/" + episodeId), null, 404);
    call(
        foreignNurse,
        put(visitPath(visit) + "/schedule"),
        Map.of(
            "professionalId",
            doctor,
            "scheduledAt",
            slot.plusSeconds(172800).toString(),
            "version",
            replaced.path("version").asLong()),
        404);
  }

  @Test
  void revocationOverridesScheduledVisitAndBlocksClinicalRead() throws Exception {
    JsonNode episode = open();
    UUID episodeId = id(episode), visit = id(episode.path("visits").get(0));
    cash(invoice(episodeId), "paid", 100);
    program(visit, doctor, slot, 0);
    call(physician, get(visitPath(visit) + "/report"), null, 200);
    db.update(
        "insert into patient.access_revocation(patient_id,person_id,organization_id,revoked_at)"
            + " values(?,?,?,clock_timestamp())",
        patient,
        ACTORS.get(physician).person,
        org);
    call(physician, get(visitPath(visit) + "/report"), null, 403);
  }

  @Test
  void expiredAccessAndFullQuotaRejectNewWork() throws Exception {
    JsonNode episode = open();
    UUID episodeId = id(episode), visit = id(episode.path("visits").get(0));
    cash(invoice(episodeId), "paid", 100);
    program(visit, doctor, slot, 0);
    JsonNode started =
        call(
            physician,
            post(visitPath(visit) + "/start"),
            Map.of("version", read(nurse, visitPath(visit)).path("version").asLong()),
            200);
    JsonNode draft =
        call(
            physician,
            put(visitPath(visit) + "/report"),
            Map.of("version", 0, "reason", "Motif", "diagnosis", "Diagnostic"),
            200);
    call(
        physician,
        post(visitPath(visit) + "/report/finalize"),
        Map.of("version", draft.path("version").asLong()),
        200);
    db.update(
        "update care.care_episode set"
            + " created_at='2019-01-01T00:00:00Z',started_at='2020-01-01T00:00:00Z',expires_at='2020-01-01T00:00:00Z'::timestamptz"
            + " + follow_up_seconds * interval '1 second' where id=?",
        episodeId);
    call(physician, get(visitPath(visit) + "/report"), null, 403);
    call(
        nurse,
        post("/api/v1/care/episodes/" + episodeId + "/follow-ups")
            .header("Idempotency-Key", "expired-follow-up"),
        Map.of("professionalId", doctor, "scheduledAt", slot.plusSeconds(86400).toString()),
        422);
    db.update("update licensing.subscription set doctor_quota=2 where organization_id=?", org);
    int before =
        db.queryForObject(
            "select count(*) from licensing.member where organization_id=?", Integer.class, org);
    call(
        admin,
        post("/api/v1/licensing/organizations/" + org + "/team"),
        Map.of("email", "new-doctor@example.test", "role", "DOCTOR"),
        400);
    assertThat(
            db.queryForObject(
                "select count(*) from licensing.member where organization_id=?",
                Integer.class,
                org))
        .isEqualTo(before);
  }

  private JsonNode open() throws Exception {
    return call(
        nurse,
        post("/api/v1/care/episodes"),
        Map.of(
            "patientId",
            patient,
            "reason",
            "Céphalée",
            "specialityId",
            specialty,
            "professionalId",
            doctor,
            "scheduledAt",
            slot.toString()),
        201);
  }

  private UUID invoice(UUID episode) throws Exception {
    JsonNode invoice =
        call(
            nurse,
            post("/api/v1/billing/care/episodes/" + episode + "/invoice")
                .header("Idempotency-Key", "invoice-" + episode),
            Map.of("items", List.of(Map.of("tariffId", consultationTariff, "quantity", 1))),
            200);
    assertThat(invoice.path("totalAmount").asInt()).isEqualTo(100);
    return id(invoice);
  }

  private void cash(UUID invoice, String key, int amount) throws Exception {
    call(
        cashier,
        post("/api/v1/billing/care/invoices/" + invoice + "/cash")
            .header("Idempotency-Key", "cash-operation-" + key),
        Map.of("amount", amount, "currency", "XAF"),
        200);
  }

  private JsonNode program(UUID visit, UUID physician, Instant date, long version)
      throws Exception {
    if (version == 0) version = read(nurse, visitPath(visit)).path("version").asLong();
    return call(
        nurse,
        put(visitPath(visit) + "/schedule"),
        Map.of(
            "professionalId",
            physician,
            "scheduledAt",
            date.toString(),
            "version",
            version,
            "reason",
            "Disponibilité"),
        200);
  }

  private JsonNode read(String actor, String path) throws Exception {
    return call(actor, get(path), null, 200);
  }

  private JsonNode call(
      String actor, MockHttpServletRequestBuilder request, Object body, int expected)
      throws Exception {
    Actor selected = Objects.requireNonNull(ACTORS.get(actor));
    request
        .header(HttpHeaders.AUTHORIZATION, "Bearer " + actor)
        .header("X-Profile-ID", selected.profile())
        .header("X-Organization-ID", selected.org());
    if (body != null)
      request.contentType(MediaType.APPLICATION_JSON).content(json.writeValueAsString(body));
    String value =
        mvc.perform(request)
            .andExpect(status().is(expected))
            .andReturn()
            .getResponse()
            .getContentAsString();
    return value.isBlank() ? json.createObjectNode() : json.readTree(value);
  }

  private UUID id(JsonNode node) {
    return UUID.fromString(node.path("id").asText());
  }

  private String visitPath(UUID visit) {
    return "/api/v1/care/visits/" + visit;
  }

  private UUID organization() {
    UUID id = UUID.randomUUID();
    db.update(
        "insert into organization.organization(id,organization_number,name,timezone)"
            + " values(?,?,?,'UTC')",
        id,
        "O-" + id,
        "Journey hospital");
    db.update(
        "insert into"
            + " licensing.subscription(organization_id,status,period_start,period_end,doctor_quota,professional_quota,cashier_quota,accountant_quota,ai_assistant_enabled)"
            + " values(?,'active',clock_timestamp()-interval '1 day',clock_timestamp()+interval '1"
            + " year',10,10,5,5,false)",
        id);
    return id;
  }

  private UUID person() {
    UUID person = UUID.randomUUID(), login = UUID.randomUUID();
    db.update(
        "insert into identity.person(id,person_number,keycloak_user_id,first_name,last_name)"
            + " values(?,?,?,'Journey','Test')",
        person,
        "PER-" + person,
        login);
    return person;
  }

  private String actor(UUID organization, String role) {
    UUID person = person();
    UUID login =
        db.queryForObject(
            "select keycloak_user_id from identity.person where id=?", UUID.class, person);
    UUID profile = UUID.randomUUID();
    db.update(
        "insert into licensing.member(id,organization_id,person_id,role,status)"
            + " values(?,?,?,?,'ACTIVE')",
        profile,
        organization,
        person,
        role);
    String token = UUID.randomUUID().toString();
    ACTORS.put(token, new Actor(person, login, organization, role, profile));
    return token;
  }

  private UUID professional(String actor) {
    Actor context = ACTORS.get(actor);
    UUID professional = UUID.randomUUID(), assignment = UUID.randomUUID();
    db.update(
        "insert into professional.professional(id,person_id,professional_number,professional_type)"
            + " values(?,?,?,?)",
        professional,
        context.person,
        "PRO-" + professional,
        context.role);
    db.update(
        "insert into"
            + " professional.professional_assignment(id,professional_id,organization_id,start_date)"
            + " values(?,?,?,current_date-1)",
        assignment,
        professional,
        context.org);
    if (context.role.equals("DOCTOR")) {
      db.update(
          "insert into professional.professional_speciality(professional_id,speciality_catalog_id)"
              + " values(?,?)",
          professional,
          specialty);
      db.update(
          "insert into"
              + " professional.professional_availability(assignment_id,start_at,end_at,availability_type)"
              + " values(?,?,?,'CONSULTATION')",
          assignment,
          Timestamp.from(Instant.now().minusSeconds(86400)),
          Timestamp.from(Instant.now().plusSeconds(864000)));
    }
    return professional;
  }

  record Actor(UUID person, UUID login, UUID org, String role, UUID profile) {}

  @TestConfiguration(proxyBeanMethods = false)
  static class Tokens {
    @Bean
    JwtDecoder jwtDecoder() {
      return token -> {
        Actor actor = Objects.requireNonNull(ACTORS.get(token), "Unknown journey test token");
        Instant now = Instant.now();
        return Jwt.withTokenValue(token)
            .header("alg", "RS256")
            .subject(actor.login.toString())
            .issuer("https://keycloak.example/realms/healthys")
            .audience(List.of("healthys-backend-apps"))
            .issuedAt(now)
            .expiresAt(now.plusSeconds(300))
            .claim("realm_access", Map.of("roles", List.of(actor.role)))
            .claim("resource_access", Map.of())
            .claim("organization_id", actor.org.toString())
            .build();
      };
    }
  }
}
