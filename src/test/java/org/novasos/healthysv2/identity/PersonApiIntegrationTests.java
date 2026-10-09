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
import java.util.concurrent.Executors;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import org.novasos.healthysv2.identity.api.IdentityProvisioningService;
import static org.assertj.core.api.Assertions.assertThat;

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
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.TransactionDefinition;
import org.springframework.transaction.support.TransactionTemplate;
import org.junit.jupiter.api.AfterEach;

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

    @Autowired
    PlatformTransactionManager transactions;

    private TransactionTemplate independentTransaction() {
        TransactionTemplate template = new TransactionTemplate(transactions);
        template.setPropagationBehavior(TransactionDefinition.PROPAGATION_REQUIRES_NEW);
        return template;
    }

    @AfterEach
    void removeCommittedMeFixtures() {
        independentTransaction().executeWithoutResult(status -> {
            repository.findByKeycloakUserId(PATIENT_SUBJECT).ifPresent(repository::delete);
            repository.findByKeycloakUserId(UUID.fromString(
                    "00000000-0000-0000-0000-000000000199")).ifPresent(repository::delete);
            repository.flush();
        });
    }

    @Test
    void meReturnsThePersonLinkedToTheJwtSubject() throws Exception {
        independentTransaction().executeWithoutResult(status -> repository.saveAndFlush(Person.create(
                "PER-ME",
                PATIENT_SUBJECT,
                "Ada",
                null,
                "Lovelace",
                null,
                null,
                null)));

        mockMvc.perform(get("/api/v1/persons/me")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer patient"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.personNumber").value("PER-ME"))
                .andExpect(jsonPath("$.keycloakUserId")
                        .value(PATIENT_SUBJECT.toString()));
    }

    @Autowired
    IdentityProvisioningService provisioning;

    @Test
    void concurrentProvisioningCreatesOnlyOneIdentity() throws Exception {
        UUID subject = UUID.randomUUID();
        CountDownLatch start = new CountDownLatch(1);
        try (var executor = Executors.newFixedThreadPool(2)) {
            java.util.concurrent.Callable<IdentityProvisioningService.ProvisionedPerson> request = () -> {
                if (!start.await(10, TimeUnit.SECONDS)) {
                    throw new IllegalStateException("Provisioning start timed out");
                }
                return provisioning.provisionIdentity(subject, "Ada", "Lovelace", null, false);
            };
            var first = executor.submit(request);
            var second = executor.submit(request);
            start.countDown();
            var firstResult = first.get(20, TimeUnit.SECONDS);
            var secondResult = second.get(20, TimeUnit.SECONDS);
            assertThat(firstResult.id()).isEqualTo(secondResult.id());
            assertThat(List.of(firstResult.created(), secondResult.created()))
                    .containsExactlyInAnyOrder(true, false);
        } finally {
            independentTransaction().executeWithoutResult(status -> {
                repository.findByKeycloakUserId(subject).ifPresent(repository::delete);
                repository.flush();
            });
        }
    }

    @Test
    void meRequiresAuthentication() throws Exception {
        mockMvc.perform(get("/api/v1/persons/me"))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void meProvisionsAnUnprovisionedUserIdempotently() throws Exception {

        mockMvc.perform(get("/api/v1/persons/me")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer unknown"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.firstName").value("Ada"))
                .andExpect(jsonPath("$.lastName").value("Lovelace"));
        UUID subject = UUID.fromString("00000000-0000-0000-0000-000000000199");
        UUID personId = repository.findByKeycloakUserId(subject).orElseThrow().getId();
        mockMvc.perform(get("/api/v1/persons/me")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer unknown"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id").value(personId.toString()));
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
                    .claim("given_name", "Ada")
                    .claim("family_name", "Lovelace")
                    .claim("email", "ada@example.test")
                    .claim("email_verified", true)
                    .claim("realm_access", Map.of("roles", roles))
                    .claim("resource_access", Map.of())
                    .build();
        }
    }
}
