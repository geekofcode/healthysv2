package org.novasos.healthysv2.appointment;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneOffset;
import java.time.temporal.ChronoUnit;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.novasos.healthysv2.TestcontainersConfiguration;
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
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.annotation.Propagation;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;

@ActiveProfiles("test")
@SpringBootTest(properties = {
    "spring.security.oauth2.resourceserver.jwt.issuer-uri=https://keycloak.example/realms/healthys",
    "spring.security.oauth2.resourceserver.jwt.audiences=healthys-backend-apps",
    "healthys.security.api-client-id=healthys-backend-apps",
    "healthys.security.cors.allowed-origins=http://localhost:5173"
})
@AutoConfigureMockMvc
@Import({TestcontainersConfiguration.class, PatientAppointmentsApiIntegrationTests.JwtFixtures.class})
@Transactional
class PatientAppointmentsApiIntegrationTests {
    private static final String BASE = "/api/v1/patients/me/appointments";
    private static final UUID SUBJECT = UUID.fromString("85000000-0000-0000-0000-000000000001");
    private static final UUID OTHER = UUID.fromString("85000000-0000-0000-0000-000000000002");
    @Autowired MockMvc mvc;
    @Autowired JdbcTemplate jdbc;

    @Test
    void patientBooksMovesAndCancelsAnActualAvailableSlot() throws Exception {
        Graph graph = graph();
        mvc.perform(get(BASE + "/booking-options").header(HttpHeaders.AUTHORIZATION, "Bearer patient"))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.organizations[0].name").value("Patient hospital"))
            .andExpect(jsonPath("$.professionals[0].name").isNotEmpty());
        mvc.perform(get(BASE + "/availability").header(HttpHeaders.AUTHORIZATION, "Bearer patient")
            .param("organizationId", graph.organization.toString())
            .param("professionalId", graph.professional.toString())
            .param("from", graph.start.toString()).param("to", graph.start.plusSeconds(3600).toString()))
            .andExpect(status().isOk()).andExpect(jsonPath("$.slots.length()").value(2));
        String created = mvc.perform(post(BASE).header(HttpHeaders.AUTHORIZATION, "Bearer patient")
            .contentType(MediaType.APPLICATION_JSON).content(booking(graph, graph.start)))
            .andExpect(status().isCreated())
            .andExpect(jsonPath("$.patientId").value(graph.patient.toString()))
            .andExpect(jsonPath("$.organizationName").value("Patient hospital"))
            .andExpect(jsonPath("$.canCancel").value(true))
            .andReturn().getResponse().getContentAsString();
        UUID appointment = id(created);
        mvc.perform(get(BASE).header(HttpHeaders.AUTHORIZATION, "Bearer patient").param("view", "upcoming"))
            .andExpect(status().isOk()).andExpect(jsonPath("$.content[0].id").value(appointment.toString()));
        mvc.perform(get(BASE + "/{id}", appointment).header(HttpHeaders.AUTHORIZATION, "Bearer patient"))
            .andExpect(status().isOk()).andExpect(jsonPath("$.professionalName").isNotEmpty());
        mvc.perform(post(BASE).header(HttpHeaders.AUTHORIZATION, "Bearer patient")
            .contentType(MediaType.APPLICATION_JSON).content(booking(graph, graph.start)))
            .andExpect(status().isConflict());
        Instant moved = graph.start.plusSeconds(1800);
        mvc.perform(post(BASE + "/{id}/reschedule", appointment).header(HttpHeaders.AUTHORIZATION, "Bearer patient")
            .contentType(MediaType.APPLICATION_JSON).content(times(moved)))
            .andExpect(status().isOk()).andExpect(jsonPath("$.status").value("RESCHEDULED"))
            .andExpect(jsonPath("$.scheduledStart").value(moved.toString()));
        mvc.perform(post(BASE + "/{id}/cancel", appointment).header(HttpHeaders.AUTHORIZATION, "Bearer patient")
            .contentType(MediaType.APPLICATION_JSON).content("{\"reason\":\"Patient request\"}"))
            .andExpect(status().isOk()).andExpect(jsonPath("$.status").value("CANCELLED"))
            .andExpect(jsonPath("$.canReschedule").value(false));
        mvc.perform(get(BASE).header(HttpHeaders.AUTHORIZATION, "Bearer patient").param("view", "past"))
            .andExpect(status().isOk()).andExpect(jsonPath("$.content[0].id").value(appointment.toString()));
        mvc.perform(get(BASE).header(HttpHeaders.AUTHORIZATION, "Bearer patient").param("view", "upcoming"))
            .andExpect(status().isOk()).andExpect(jsonPath("$.content").isEmpty());
    }

    @Test
    void otherPatientCannotReadCancelOrMoveAppointmentThroughEitherRoute() throws Exception {
        Graph graph = graph();
        person(OTHER);
        UUID appointment = id(mvc.perform(post(BASE).header(HttpHeaders.AUTHORIZATION, "Bearer patient")
            .contentType(MediaType.APPLICATION_JSON).content(booking(graph, graph.start)))
            .andExpect(status().isCreated()).andReturn().getResponse().getContentAsString());
        for (String prefix : List.of(BASE, "/api/v1/appointments")) {
            mvc.perform(get(prefix + "/{id}", appointment).header(HttpHeaders.AUTHORIZATION, "Bearer other"))
                .andExpect(status().isForbidden());
            mvc.perform(post(prefix + "/{id}/cancel", appointment).header(HttpHeaders.AUTHORIZATION, "Bearer other")
                .contentType(MediaType.APPLICATION_JSON).content("{}"))
                .andExpect(status().isForbidden());
            mvc.perform(post(prefix + "/{id}/reschedule", appointment).header(HttpHeaders.AUTHORIZATION, "Bearer other")
                .contentType(MediaType.APPLICATION_JSON).content(times(graph.start.plusSeconds(1800))))
                .andExpect(status().isForbidden());
        }
    }

    @Test
    @Transactional(propagation = Propagation.NOT_SUPPORTED)
    void simultaneousBookingsCanOnlyReserveTheSlotOnce() throws Exception {
        Graph graph = graph();
        UUID doctorPerson = jdbc.queryForObject("select person_id from professional.professional where id=?", UUID.class, graph.professional);
        UUID patientPerson = jdbc.queryForObject("select person_id from patient.patient where id=?", UUID.class, graph.patient);
        try (var executor = Executors.newVirtualThreadPerTaskExecutor()) {
            CountDownLatch ready = new CountDownLatch(2);
            CountDownLatch start = new CountDownLatch(1);
            java.util.concurrent.Callable<Integer> reserve = () -> {
                ready.countDown();
                if (!start.await(10, TimeUnit.SECONDS)) throw new IllegalStateException("Start timed out");
                return mvc.perform(post(BASE).header(HttpHeaders.AUTHORIZATION, "Bearer patient")
                    .contentType(MediaType.APPLICATION_JSON).content(booking(graph, graph.start)))
                    .andReturn().getResponse().getStatus();
            };
            var first = executor.submit(reserve);
            var second = executor.submit(reserve);
            if (!ready.await(10, TimeUnit.SECONDS)) throw new IllegalStateException("Workers timed out");
            start.countDown();
            var outcomes = new java.util.ArrayList<>(List.of(first.get(30, TimeUnit.SECONDS), second.get(30, TimeUnit.SECONDS)));
            outcomes.sort(Integer::compareTo);
            assertEquals(List.of(201, 409), outcomes);
            assertEquals(1, jdbc.queryForObject("select count(*) from appointment.appointment where professional_id=?", Integer.class, graph.professional));
        } finally {
            jdbc.update("delete from appointment.appointment_status_history where appointment_id in (select id from appointment.appointment where professional_id=?)", graph.professional);
            jdbc.update("delete from appointment.appointment where professional_id=?", graph.professional);
            jdbc.update("delete from professional.professional_availability where assignment_id in (select id from professional.professional_assignment where professional_id=?)", graph.professional);
            jdbc.update("delete from professional.professional_assignment where professional_id=?", graph.professional);
            jdbc.update("delete from patient.patient_registration where patient_id=?", graph.patient);
            jdbc.update("delete from patient.patient where id=?", graph.patient);
            jdbc.update("delete from professional.professional where id=?", graph.professional);
            jdbc.update("delete from organization.organization where id=?", graph.organization);
            jdbc.update("delete from identity.person where id in (?,?)", doctorPerson, patientPerson);
        }
    }

    private Graph graph() {
        UUID person = person(SUBJECT);
        UUID doctor = person(null);
        UUID patient = UUID.randomUUID();
        UUID professional = UUID.randomUUID();
        UUID organization = UUID.randomUUID();
        UUID assignment = UUID.randomUUID();
        Instant start = Instant.now().plus(2, ChronoUnit.DAYS).truncatedTo(ChronoUnit.HOURS);
        jdbc.update("insert into patient.patient(id,person_id,patient_number) values (?,?,?)", patient, person, "PAT-" + patient);
        jdbc.update("insert into organization.organization(id,organization_number,name) values (?,?,?)", organization, "ORG-" + organization, "Patient hospital");
        jdbc.update("insert into patient.patient_registration(id,patient_id,organization_id,registration_number,status) values (?,?,?,?,?)", UUID.randomUUID(), patient, organization, "REG-" + patient, "ACTIVE");
        jdbc.update("insert into professional.professional(id,person_id,professional_number,professional_type,status) values (?,?,?,?,?)", professional, doctor, "PRO-" + professional, "DOCTOR", "ACTIVE");
        jdbc.update("insert into professional.professional_assignment(id,professional_id,organization_id,start_date,status) values (?,?,?,?,?)", assignment, professional, organization, LocalDate.now(ZoneOffset.UTC).minusDays(1), "ACTIVE");
        jdbc.update("insert into professional.professional_availability(id,assignment_id,start_at,end_at,availability_type,status) values (?,?,?,?,?,?)", UUID.randomUUID(), assignment, java.sql.Timestamp.from(start), java.sql.Timestamp.from(start.plusSeconds(3600)), "CONSULTATION", "AVAILABLE");
        return new Graph(patient, professional, organization, start);
    }
    private UUID person(UUID subject) {
        UUID id = UUID.randomUUID();
        jdbc.update("insert into identity.person(id,person_number,keycloak_user_id,first_name,last_name,status) values (?,?,?,?,?,?)", id, "PER-" + id, subject, "Ada", "Patient", "ACTIVE");
        // The second patient exists, so IDOR tests reach ownership checks.
        if (OTHER.equals(subject)) {
            UUID patient = UUID.randomUUID();
            jdbc.update("insert into patient.patient(id,person_id,patient_number) values (?,?,?)", patient, id, "PAT-" + patient);
        }
        return id;
    }
    private String booking(Graph g, Instant start) {
        return "{\"organizationId\":\"%s\",\"professionalId\":\"%s\",\"scheduledStart\":\"%s\",\"scheduledEnd\":\"%s\",\"reason\":\"Check-up\"}".formatted(g.organization, g.professional, start, start.plusSeconds(1800));
    }
    private String times(Instant start) {
        return "{\"scheduledStart\":\"%s\",\"scheduledEnd\":\"%s\"}".formatted(start, start.plusSeconds(1800));
    }
    private UUID id(String body) {
        var matcher = java.util.regex.Pattern.compile("\"id\":\"([^\"]+)\"").matcher(body);
        if (!matcher.find()) throw new IllegalArgumentException(body);
        return UUID.fromString(matcher.group(1));
    }
    private record Graph(UUID patient, UUID professional, UUID organization, Instant start) {}
    @TestConfiguration(proxyBeanMethods = false)
    static class JwtFixtures {
        @Bean JwtDecoder jwtDecoder() {
            return token -> {
                Instant now = Instant.now();
                return Jwt.withTokenValue(token).header("alg", "RS256")
                    .subject((token.equals("other") ? OTHER : SUBJECT).toString())
                    .issuer("https://keycloak.example/realms/healthys").audience(List.of("healthys-backend-apps"))
                    .issuedAt(now).expiresAt(now.plusSeconds(300))
                    .claim("realm_access", Map.of("roles", List.of("PATIENT")))
                    .claim("resource_access", Map.of()).build();
            };
        }
    }
}
