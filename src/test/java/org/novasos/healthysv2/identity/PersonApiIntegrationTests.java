package org.novasos.healthysv2.identity;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.time.Instant;
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
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.jwt.JwtDecoder;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.transaction.annotation.Transactional;

@ActiveProfiles("test")
@SpringBootTest(properties = {
        "spring.security.oauth2.resourceserver.jwt.issuer-uri=https://keycloak.example/realms/healthys",
        "spring.security.oauth2.resourceserver.jwt.audiences=healthys-api",
        "healthys.security.api-client-id=healthys-api",
        "healthys.security.cors.allowed-origins=http://localhost:5173",
        "healthys.security.cors.max-age-seconds=3600"
})
@AutoConfigureMockMvc
@Import({
        TestcontainersConfiguration.class,
        PersonApiIntegrationTests.JwtFixtures.class
})
@Transactional
class PersonApiIntegrationTests {

    private static final UUID PATIENT_SUBJECT =
            UUID.fromString("00000000-0000-0000-0000-000000000101");
    private static final UUID ADMIN_SUBJECT =
            UUID.fromString("00000000-0000-0000-0000-000000000102");

    @Autowired
    MockMvc mockMvc;

    @Autowired
    PersonRepository repository;

    @Test
    void meReturnsThePersonLinkedToTheJwtSubject() throws Exception {
        repository.saveAndFlush(Person.create(
                "PER-ME",
                PATIENT_SUBJECT,
                "Ada",
                null,
                "Lovelace",
                null,
                null,
                null));

        mockMvc.perform(get("/api/v1/persons/me")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer patient"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.personNumber").value("PER-ME"))
                .andExpect(jsonPath("$.keycloakUserId")
                        .value(PATIENT_SUBJECT.toString()));
    }

    @Test
    void meRequiresAuthentication() throws Exception {
        mockMvc.perform(get("/api/v1/persons/me"))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void meReturnsNotFoundForAnUnprovisionedUser() throws Exception {
        mockMvc.perform(get("/api/v1/persons/me")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer unknown"))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code").value("RESOURCE_NOT_FOUND"));
    }

    @Test
    void platformAdminCanCreateAPerson() throws Exception {
        UUID keycloakUser = UUID.randomUUID();

        mockMvc.perform(post("/api/v1/persons")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer admin")
                        .contentType("application/json")
                        .content("""
                                {
                                  "personNumber": "PER-API-1",
                                  "keycloakUserId": "%s",
                                  "firstName": "Grace",
                                  "lastName": "Hopper",
                                  "contacts": [{
                                    "type": "EMAIL",
                                    "value": "grace@example.com",
                                    "primary": true,
                                    "verified": true
                                  }]
                                }
                                """.formatted(keycloakUser)))
                .andExpect(status().isCreated())
                .andExpect(header().string(
                        HttpHeaders.LOCATION,
                        org.hamcrest.Matchers.matchesPattern(
                                "/api/v1/persons/[0-9a-f-]{36}")))
                .andExpect(jsonPath("$.personNumber").value("PER-API-1"))
                .andExpect(jsonPath("$.contacts[0].value")
                        .value("grace@example.com"));
    }

    @Test
    void patientCannotCreateAPerson() throws Exception {
        mockMvc.perform(post("/api/v1/persons")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer patient")
                        .contentType("application/json")
                        .content("""
                                {
                                  "personNumber": "PER-FORBIDDEN",
                                  "firstName": "Blocked",
                                  "lastName": "User"
                                }
                                """))
                .andExpect(status().isForbidden());
    }

    @TestConfiguration(proxyBeanMethods = false)
    static class JwtFixtures {

        @Bean
        JwtDecoder jwtDecoder() {
            return token -> switch (token) {
                case "patient" -> jwt(
                        token,
                        PATIENT_SUBJECT,
                        List.of("PATIENT"));
                case "admin" -> jwt(
                        token,
                        ADMIN_SUBJECT,
                        List.of("PLATFORM_ADMIN"));
                case "unknown" -> jwt(
                        token,
                        UUID.fromString(
                                "00000000-0000-0000-0000-000000000199"),
                        List.of("PATIENT"));
                default -> throw new IllegalArgumentException("Unknown token");
            };
        }

        private Jwt jwt(
                String token,
                UUID subject,
                List<String> roles) {
            Instant now = Instant.now();
            return Jwt.withTokenValue(token)
                    .header("alg", "RS256")
                    .subject(subject.toString())
                    .issuer("https://keycloak.example/realms/healthys")
                    .audience(List.of("healthys-api"))
                    .issuedAt(now)
                    .expiresAt(now.plusSeconds(300))
                    .claim("realm_access", Map.of("roles", roles))
                    .claim("resource_access", Map.of())
                    .build();
        }
    }
}
