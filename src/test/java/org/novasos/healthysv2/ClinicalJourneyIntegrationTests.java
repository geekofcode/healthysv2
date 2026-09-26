package org.novasos.healthysv2;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.junit.jupiter.api.Test;
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

@ActiveProfiles("test")
@SpringBootTest(properties = {
        "spring.security.oauth2.resourceserver.jwt.issuer-uri=https://keycloak.example/realms/healthys",
        "spring.security.oauth2.resourceserver.jwt.audiences=healthys-backend-apps",
        "healthys.security.api-client-id=healthys-backend-apps",
        "healthys.security.cors.allowed-origins=http://localhost:5173"
})
@AutoConfigureMockMvc
@Import({TestcontainersConfiguration.class, ClinicalJourneyIntegrationTests.Tokens.class})
@Transactional
class ClinicalJourneyIntegrationTests {
    private static final UUID DOCTOR_LOGIN = UUID.fromString("91000000-0000-0000-0000-000000000001");
    private static final UUID PATIENT_LOGIN = UUID.fromString("91000000-0000-0000-0000-000000000002");
    private static final UUID LAB_LOGIN = UUID.fromString("91000000-0000-0000-0000-000000000003");
    private static UUID organization;
    private static UUID otherOrganization;

    @Autowired MockMvc mvc;
    @Autowired ObjectMapper json;
    @Autowired JdbcTemplate jdbc;

    @Test
    void adminToDoctorToLaboratoryToPatient() throws Exception {
        String suffix = UUID.randomUUID().toString().substring(0, 8);
        organization = id(create("admin", "/api/v1/organizations",
                "{\"number\":\"ORG-%s\",\"name\":\"Journey Clinic\"}".formatted(suffix)));
        otherOrganization = id(create("admin", "/api/v1/organizations",
                "{\"number\":\"OTHER-%s\",\"name\":\"Other Clinic\"}".formatted(suffix)));
        UUID doctorPerson = person("Doctor", DOCTOR_LOGIN, suffix);
        UUID patientPerson = person("Patient", PATIENT_LOGIN, suffix);
        UUID labPerson = person("Laboratory", LAB_LOGIN, suffix);
        UUID doctor = professional(doctorPerson, "DOCTOR", suffix);
        UUID technician = professional(labPerson, "LAB_TECHNICIAN", suffix);
        assign(doctor);
        assign(technician);

        UUID patient = id(create("admin", "/api/v1/patients",
                "{\"personId\":\"%s\"}".formatted(patientPerson)));
        create("admin", "/api/v1/patients/%s/registrations".formatted(patient),
                "{\"organizationId\":\"%s\",\"registrationNumber\":\"REG-%s\"}".formatted(organization, suffix));
        allow(patient, doctor, doctorPerson);
        allow(patient, technician, labPerson);

        Instant start = Instant.now().plusSeconds(172800);
        Instant end = start.plusSeconds(1800);
        create("admin", "/api/v1/professionals/%s/assignments/%s/availabilities".formatted(doctor, assignment(doctor)),
                "{\"startAt\":\"%s\",\"endAt\":\"%s\",\"availabilityType\":\"CONSULTATION\"}".formatted(start.minusSeconds(3600), end.plusSeconds(3600)));
        UUID appointment = id(create("admin", "/api/v1/appointments",
                "{\"patientId\":\"%s\",\"professionalId\":\"%s\",\"organizationId\":\"%s\",\"type\":\"CONSULTATION\",\"scheduledStart\":\"%s\",\"scheduledEnd\":\"%s\"}".formatted(patient, doctor, organization, start, end)));

        read("doctor", "/api/v1/patients/" + patient);
        mvc.perform(get("/api/v1/patients/{id}", patient)
                .header(HttpHeaders.AUTHORIZATION, "Bearer doctor-other"))
                .andExpect(status().isForbidden());
        UUID consultation = id(create("doctor", "/api/v1/consultations",
                "{\"patientId\":\"%s\",\"professionalId\":\"%s\",\"organizationId\":\"%s\",\"appointmentId\":\"%s\",\"type\":\"GENERAL\"}".formatted(patient, doctor, organization, appointment)));
        create("doctor", "/api/v1/consultations/%s/diagnoses".formatted(consultation),
                "{\"diagnosisType\":\"PRIMARY\",\"description\":\"Hypertension\"}");

        UUID medication = UUID.randomUUID(), exam = UUID.randomUUID();
        jdbc.update("insert into catalog.medication_catalog(id,code,name,active) values (?,?,?,true)", medication, "MED-" + suffix, "Treatment");
        jdbc.update("insert into catalog.laboratory_exam_catalog(id,code,name,specimen_type,active) values (?,?,?,'BLOOD',true)", exam, "CBC-" + suffix, "Blood count");
        UUID prescription = id(create("doctor", "/api/v1/prescriptions",
                "{\"patientId\":\"%s\",\"consultationId\":\"%s\",\"prescriberId\":\"%s\",\"organizationId\":\"%s\",\"items\":[{\"medicationCatalogId\":\"%s\",\"dosage\":\"500 mg\",\"frequency\":\"BID\",\"route\":\"ORAL\",\"duration\":\"5 days\",\"quantity\":10}]}".formatted(patient, consultation, doctor, organization, medication)));
        JsonNode order = create("doctor", "/api/v1/lab-orders",
                "{\"patientId\":\"%s\",\"consultationId\":\"%s\",\"orderingProfessionalId\":\"%s\",\"laboratoryOrganizationId\":\"%s\",\"priority\":\"ROUTINE\",\"items\":[{\"labExamCatalogId\":\"%s\"}]}".formatted(patient, consultation, doctor, organization, exam));
        UUID orderId = id(order), item = id(order.path("items").get(0));

        mvc.perform(get("/api/v1/lab-orders/{id}", orderId).header(HttpHeaders.AUTHORIZATION, "Bearer patient"))
                .andExpect(status().isForbidden());
        UUID specimen = id(create("lab", "/api/v1/lab-orders/%s/items/%s/specimens".formatted(orderId, item),
                "{\"specimenNumber\":\"SP-%s\",\"specimenType\":\"BLOOD\",\"collectedBy\":\"%s\"}".formatted(suffix, technician)));
        mvc.perform(post("/api/v1/lab-orders/{order}/specimens/{specimen}/receive", orderId, specimen)
                .header(HttpHeaders.AUTHORIZATION, "Bearer lab")).andExpect(status().isOk());
        UUID result = id(create("lab", "/api/v1/lab-orders/%s/results".formatted(orderId),
                "{\"performedBy\":\"%s\"}".formatted(technician)));
        create("lab", "/api/v1/lab-orders/%s/results/%s/items".formatted(orderId, result),
                "{\"labOrderItemId\":\"%s\",\"parameter\":\"Hemoglobin\",\"value\":\"12.5\",\"unit\":\"g/dL\"}".formatted(item));
        mvc.perform(post("/api/v1/lab-orders/{order}/results/{result}/validate", orderId, result)
                .header(HttpHeaders.AUTHORIZATION, "Bearer lab").contentType(MediaType.APPLICATION_JSON)
                .content("{\"validatedBy\":\"%s\"}".formatted(technician)))
                .andExpect(status().isOk());

        assertThat(read("patient", "/api/v1/lab-orders/" + orderId).path("results").get(0)
                .path("items").get(0).path("value").asText()).isEqualTo("12.5");
        assertThat(read("patient", "/api/v1/lab-orders?patientId=" + patient).path("content").get(0)
                .path("id").asText()).isEqualTo(orderId.toString());
        mvc.perform(get("/api/v1/lab-orders/{id}", orderId).header(HttpHeaders.AUTHORIZATION, "Bearer stranger"))
                .andExpect(status().isForbidden());
        mvc.perform(get("/api/v1/lab-orders/{id}", orderId).header(HttpHeaders.AUTHORIZATION, "Bearer doctor-other"))
                .andExpect(status().isForbidden());
        assertThat(read("patient", "/api/v1/prescriptions/" + prescription).path("id").asText())
                .isEqualTo(prescription.toString());
        assertThat(jdbc.queryForObject("select count(*) from audit.audit_log where entity_id in (?,?)", Integer.class, consultation, orderId)).isGreaterThan(2);
        assertThat(jdbc.queryForObject("select count(*) from audit.data_access_log where patient_id=? and action='DENIED'", Integer.class, patient)).isPositive();
    }

    private UUID person(String name, UUID login, String suffix) throws Exception {
        return id(create("admin", "/api/v1/persons",
                "{\"personNumber\":\"PER-%s-%s\",\"keycloakUserId\":\"%s\",\"firstName\":\"%s\",\"lastName\":\"Journey\"}".formatted(name, suffix, login, name)));
    }
    private UUID professional(UUID person, String type, String suffix) throws Exception {
        return id(create("admin", "/api/v1/professionals",
                "{\"personId\":\"%s\",\"professionalNumber\":\"PRO-%s-%s\",\"professionalType\":\"%s\"}".formatted(person, type, suffix, type)));
    }
    private void assign(UUID professional) throws Exception {
        create("admin", "/api/v1/professionals/%s/assignments".formatted(professional),
                "{\"organizationId\":\"%s\",\"startDate\":\"%s\"}".formatted(organization, LocalDate.now().minusDays(1)));
    }
    private UUID assignment(UUID professional) throws Exception {
        return id(read("admin", "/api/v1/professionals/" + professional).path("assignments").get(0));
    }
    private void allow(UUID patient, UUID professional, UUID person) throws Exception {
        create("admin", "/api/v1/patients/%s/care-relationships".formatted(patient),
                "{\"professionalId\":\"%s\",\"organizationId\":\"%s\",\"relationshipType\":\"ATTENDING\"}".formatted(professional, organization));
        create("admin", "/api/v1/patients/%s/consents".formatted(patient),
                "{\"granteePersonId\":\"%s\",\"scope\":\"FULL_RECORD\"}".formatted(person));
    }
    private JsonNode create(String token, String path, String body) throws Exception {
        return json.readTree(mvc.perform(post(path).header(HttpHeaders.AUTHORIZATION, "Bearer " + token)
                .contentType(MediaType.APPLICATION_JSON).content(body)).andExpect(status().isCreated())
                .andReturn().getResponse().getContentAsString());
    }
    private JsonNode read(String token, String path) throws Exception {
        return json.readTree(mvc.perform(get(path).header(HttpHeaders.AUTHORIZATION, "Bearer " + token))
                .andExpect(status().isOk()).andReturn().getResponse().getContentAsString());
    }
    private UUID id(JsonNode node) { return UUID.fromString(node.path("id").asText()); }

    @TestConfiguration(proxyBeanMethods = false)
    static class Tokens {
        @Bean JwtDecoder jwtDecoder() {
            return token -> {
                Instant now = Instant.now();
                UUID subject = switch (token) {
                    case "doctor", "doctor-other" -> DOCTOR_LOGIN;
                    case "patient" -> PATIENT_LOGIN;
                    case "lab" -> LAB_LOGIN;
                    default -> UUID.randomUUID();
                };
                String role = switch (token) {
                    case "doctor", "doctor-other" -> "DOCTOR";
                    case "patient", "stranger" -> "PATIENT";
                    case "lab" -> "LAB_TECHNICIAN";
                    default -> "PLATFORM_ADMIN";
                };
                var jwt = Jwt.withTokenValue(token).header("alg", "RS256").subject(subject.toString())
                        .issuer("https://keycloak.example/realms/healthys")
                        .audience(List.of("healthys-backend-apps")).issuedAt(now).expiresAt(now.plusSeconds(300))
                        .claim("realm_access", Map.of("roles", List.of(role))).claim("resource_access", Map.of());
                if (token.equals("doctor") || token.equals("lab")) jwt.claim("organization_id", organization.toString());
                if (token.equals("doctor-other")) jwt.claim("organization_id", otherOrganization.toString());
                return jwt.build();
            };
        }
    }
}
