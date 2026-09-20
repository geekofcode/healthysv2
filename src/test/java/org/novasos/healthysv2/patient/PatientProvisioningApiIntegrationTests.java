package org.novasos.healthysv2.patient;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;
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
        "spring.security.oauth2.resourceserver.jwt.audiences=healthys-backend-apps",
        "healthys.security.api-client-id=healthys-backend-apps",
        "healthys.security.cors.allowed-origins=http://localhost:3000"
})
@AutoConfigureMockMvc
@Import({TestcontainersConfiguration.class, PatientProvisioningApiIntegrationTests.JwtFixtures.class})
@Transactional
class PatientProvisioningApiIntegrationTests {
    private static final UUID SUBJECT = UUID.fromString("00000000-0000-0000-0000-000000000301");
    @Autowired MockMvc mockMvc;

    @Test
    void provisionsPersonAndPatientIdempotently() throws Exception {
        mockMvc.perform(post("/api/v1/patients/me/provision")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer patient"))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.created").value(true))
                .andExpect(jsonPath("$.patientNumber").isNotEmpty());

        mockMvc.perform(post("/api/v1/patients/me/provision")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer patient"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.created").value(false));
    }

    @Test
    void nonPatientCannotProvision() throws Exception {
        mockMvc.perform(post("/api/v1/patients/me/provision")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer admin"))
                .andExpect(status().isForbidden());
    }

    @Test
    void requiresAuthentication() throws Exception {
        mockMvc.perform(post("/api/v1/patients/me/provision"))
                .andExpect(status().isUnauthorized());
    }

    @TestConfiguration(proxyBeanMethods = false)
    static class JwtFixtures {
        @Bean
        JwtDecoder jwtDecoder() {
            return token -> jwt(token, token.equals("patient")
                    ? List.of("PATIENT") : List.of("PLATFORM_ADMIN"));
        }

        private Jwt jwt(String token, List<String> roles) {
            Instant now = Instant.now();
            return Jwt.withTokenValue(token)
                    .header("alg", "RS256")
                    .subject(SUBJECT.toString())
                    .issuer("https://keycloak.example/realms/healthys")
                    .audience(List.of("healthys-backend-apps"))
                    .issuedAt(now)
                    .expiresAt(now.plusSeconds(300))
                    .claim("given_name", "Ada")
                    .claim("family_name", "Lovelace")
                    .claim("email", "ada@example.com")
                    .claim("email_verified", true)
                    .claim("realm_access", Map.of("roles", roles))
                    .claim("resource_access", Map.of())
                    .build();
        }
    }
}
