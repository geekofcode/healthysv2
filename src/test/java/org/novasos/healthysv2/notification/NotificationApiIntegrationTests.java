package org.novasos.healthysv2.notification;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.time.Instant;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;
import org.novasos.healthysv2.TestcontainersConfiguration;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Import;
import org.springframework.context.annotation.TestConfiguration;
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
        "healthys.security.api-client-id=healthys-backend-apps",
        "healthys.security.cors.allowed-origins=http://localhost:5173"
})
@AutoConfigureMockMvc
@Import({TestcontainersConfiguration.class, NotificationApiIntegrationTests.JwtFixtures.class})
@Transactional
class NotificationApiIntegrationTests {
    @Autowired MockMvc mvc;
    @Autowired JdbcTemplate jdbc;
    private final ObjectMapper json = new ObjectMapper().findAndRegisterModules();

    @Test
    void createsListsReadsAndConfiguresNotifications() throws Exception {
        person("admin");
        UUID recipient = person("recipient");
        String body = mvc.perform(post("/api/v1/notifications")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer admin")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "type":"LAB_RESULT_READY",
                                  "title":"Results available",
                                  "body":"Your laboratory results are ready.",
                                  "recipientPersonIds":["%s"],
                                  "actionUrl":"/laboratory/orders/order-1",
                                  "priority":"HIGH"
                                }
                                """.formatted(recipient)))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$[0].priority").value("HIGH"))
                .andReturn().getResponse().getContentAsString();
        JsonNode created = json.readTree(body).get(0);
        UUID notification = UUID.fromString(created.path("id").asText());

        mvc.perform(get("/api/v1/notifications?unreadOnly=true")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer recipient"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.content[0].id").value(notification.toString()))
                .andExpect(jsonPath("$.content[0].read").value(false));
        mvc.perform(get("/api/v1/notifications/unread-count")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer recipient"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.unreadCount").value(1));
        mvc.perform(patch("/api/v1/notifications/{id}/read", notification)
                        .header(HttpHeaders.AUTHORIZATION, "Bearer recipient"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.read").value(true));
        mvc.perform(put("/api/v1/notifications/preferences")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer recipient")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"inAppEnabled":true,"emailEnabled":true,"smsEnabled":false,
                                 "pushEnabled":false,"quietHoursStart":"22:00:00",
                                 "quietHoursEnd":"07:00:00","locale":"fr"}
                                """))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.emailEnabled").value(true))
                .andExpect(jsonPath("$.locale").value("fr"));

        assertThat(jdbc.queryForObject(
                "select count(*) from audit.audit_log where module='NOTIFICATION'",
                Integer.class)).isGreaterThanOrEqualTo(3);
    }

    @Test
    void preventsAnotherPersonFromReadingTheNotification() throws Exception {
        person("admin");
        UUID recipient = person("recipient");
        person("outsider");
        String body = mvc.perform(post("/api/v1/notifications")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer admin")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"type":"APPOINTMENT_REMINDER","body":"Reminder",
                                 "recipientPersonIds":["%s"]}
                                """.formatted(recipient)))
                .andReturn().getResponse().getContentAsString();
        UUID id = UUID.fromString(json.readTree(body).get(0).path("id").asText());

        mvc.perform(patch("/api/v1/notifications/{id}/read", id)
                        .header(HttpHeaders.AUTHORIZATION, "Bearer outsider"))
                .andExpect(status().isNotFound());
    }

    private UUID person(String token) {
        UUID id = UUID.randomUUID();
        jdbc.update(
                "insert into identity.person(id,keycloak_user_id,person_number,first_name,last_name,status) values (?,?,?,?,?,'ACTIVE')",
                id, id, "PER-" + id, token, "User");
        JwtFixtures.SUBJECTS.put(token, id);
        return id;
    }

    @TestConfiguration(proxyBeanMethods = false)
    static class JwtFixtures {
        static final Map<String, UUID> SUBJECTS = new HashMap<>();

        @Bean
        JwtDecoder jwtDecoder() {
            return token -> {
                UUID subject = SUBJECTS.getOrDefault(token, UUID.randomUUID());
                Instant now = Instant.now();
                String role = "admin".equals(token) ? "PLATFORM_ADMIN" : "PATIENT";
                return Jwt.withTokenValue(token)
                        .header("alg", "RS256")
                        .subject(subject.toString())
                        .issuer("https://keycloak.example/realms/healthys")
                        .issuedAt(now)
                        .expiresAt(now.plusSeconds(300))
                        .claim("realm_access", Map.of("roles", List.of(role)))
                        .claim("resource_access", Map.of())
                        .build();
            };
        }
    }
}
