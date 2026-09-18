package org.novasos.healthysv2;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.options;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.time.Instant;
import java.util.List;
import java.util.Map;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Import;
import org.springframework.http.HttpHeaders;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.jwt.JwtDecoder;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;

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
        SecurityIntegrationTests.SecurityFixtures.class,
        SecurityIntegrationTests.SecurityEndpoints.class
})
class SecurityIntegrationTests {

    @Autowired
    MockMvc mockMvc;

    @Test
    void publicEndpointDoesNotRequireAuthentication() throws Exception {
        mockMvc.perform(get("/api/v1/public/security-test"))
                .andExpect(status().isOk());
    }

    @Test
    void privateEndpointRejectsAnonymousRequests() throws Exception {
        mockMvc.perform(get("/api/v1/private/security-test"))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void privateEndpointAcceptsAValidJwt() throws Exception {
        mockMvc.perform(get("/api/v1/private/security-test")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer patient"))
                .andExpect(status().isOk());
    }

    @Test
    void mapsRealmRolesForMethodAuthorization() throws Exception {
        mockMvc.perform(get("/api/v1/private/patient-test")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer patient"))
                .andExpect(status().isOk());
    }

    @Test
    void mapsClientRolesForMethodAuthorization() throws Exception {
        mockMvc.perform(get("/api/v1/private/laboratory-test")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer laboratory"))
                .andExpect(status().isOk());
    }

    @Test
    void adminEndpointRejectsNonAdminRole() throws Exception {
        mockMvc.perform(get("/api/v1/admin/security-test")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer patient"))
                .andExpect(status().isForbidden());
    }

    @Test
    void adminEndpointAcceptsPlatformAdminRole() throws Exception {
        mockMvc.perform(get("/api/v1/admin/security-test")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer admin"))
                .andExpect(status().isOk());
    }

    @Test
    void allowsConfiguredCorsOrigin() throws Exception {
        mockMvc.perform(options("/api/v1/private/security-test")
                        .header(HttpHeaders.ORIGIN, "http://localhost:5173")
                        .header(
                                HttpHeaders.ACCESS_CONTROL_REQUEST_METHOD,
                                HttpMethodNames.GET))
                .andExpect(status().isOk())
                .andExpect(header().string(
                        HttpHeaders.ACCESS_CONTROL_ALLOW_ORIGIN,
                        "http://localhost:5173"))
                .andExpect(header().string(
                        HttpHeaders.ACCESS_CONTROL_ALLOW_CREDENTIALS,
                        "true"));
    }

    @Test
    void rejectsUnknownCorsOrigin() throws Exception {
        mockMvc.perform(options("/api/v1/private/security-test")
                        .header(HttpHeaders.ORIGIN, "https://untrusted.example")
                        .header(
                                HttpHeaders.ACCESS_CONTROL_REQUEST_METHOD,
                                HttpMethodNames.GET))
                .andExpect(status().isForbidden());
    }

    @RestController
    static class SecurityEndpoints {

        @GetMapping("/api/v1/public/security-test")
        String publicEndpoint() {
            return "public";
        }

        @GetMapping("/api/v1/private/security-test")
        String privateEndpoint() {
            return "private";
        }

        @PreAuthorize("hasRole('PATIENT')")
        @GetMapping("/api/v1/private/patient-test")
        String patientEndpoint() {
            return "patient";
        }

        @PreAuthorize("hasRole('LAB_TECHNICIAN')")
        @GetMapping("/api/v1/private/laboratory-test")
        String laboratoryEndpoint() {
            return "laboratory";
        }

        @GetMapping("/api/v1/admin/security-test")
        String adminEndpoint() {
            return "admin";
        }
    }

    @TestConfiguration(proxyBeanMethods = false)
    static class SecurityFixtures {

        @Bean
        JwtDecoder jwtDecoder() {
            return token -> switch (token) {
                case "patient" -> jwt(
                        token,
                        Map.of("roles", List.of("PATIENT")),
                        Map.of());
                case "laboratory" -> jwt(
                        token,
                        Map.of(),
                        Map.of(
                                "healthys-api",
                                Map.of("roles", List.of("LAB_TECHNICIAN"))));
                case "admin" -> jwt(
                        token,
                        Map.of("roles", List.of("PLATFORM_ADMIN")),
                        Map.of());
                default -> throw new IllegalArgumentException("Unknown token");
            };
        }

        private Jwt jwt(
                String token,
                Map<String, Object> realmAccess,
                Map<String, Object> resourceAccess) {
            Instant now = Instant.now();
            return Jwt.withTokenValue(token)
                    .header("alg", "RS256")
                    .subject("00000000-0000-0000-0000-000000000001")
                    .issuer("https://keycloak.example/realms/healthys")
                    .audience(List.of("healthys-api"))
                    .issuedAt(now)
                    .expiresAt(now.plusSeconds(300))
                    .claim("realm_access", realmAccess)
                    .claim("resource_access", resourceAccess)
                    .build();
        }
    }

    private static final class HttpMethodNames {
        private static final String GET = "GET";

        private HttpMethodNames() {
        }
    }
}
