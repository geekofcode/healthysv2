package org.novasos.healthysv2.notification;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.time.Instant;
import java.time.LocalTime;
import java.time.ZoneOffset;
import java.sql.Time;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.BeforeEach;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.novasos.healthysv2.TestcontainersConfiguration;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Import;
import org.springframework.context.annotation.Primary;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.jwt.JwtDecoder;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationToken;
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
@Import({TestcontainersConfiguration.class, PushApiIntegrationTests.JwtFixtures.class})
@Transactional
class PushApiIntegrationTests {
    @Autowired MockMvc mvc;
    @Autowired JdbcTemplate jdbc;
    @Autowired NotificationService notifications;
    @Autowired PushDeliveryWorker worker;
    @Autowired FakePushGateway gateway;
    private final ObjectMapper json = new ObjectMapper();

    @BeforeEach
    void resetGateway() {
        gateway.active = true;
        gateway.outcome = PushGateway.Outcome.ACCEPTED;
        gateway.sentNotification = null;
    }

    @Test
    void provisioningNeverUsesSubjectAsAnotherPersonsIdentifier() throws Exception {
        UUID personId = person("linked");
        UUID notificationId = createNotification(personId);
        JwtFixtures.SUBJECTS.put("unlinked", personId);
        mvc.perform(get("/api/v1/notifications")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer unlinked"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.content").isEmpty());
        UUID provisioned = jdbc.queryForObject(
                "select id from identity.person where keycloak_user_id=?", UUID.class, personId);
        assertThat(provisioned).isNotEqualTo(personId);
        mvc.perform(get("/api/v1/notifications/{id}", notificationId)
                        .header(HttpHeaders.AUTHORIZATION, "Bearer unlinked"))
                .andExpect(status().isNotFound());
        mvc.perform(get("/api/v1/notifications/preferences")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer unlinked"))
                .andExpect(status().isOk());
        UUID installationId = UUID.randomUUID();
        mvc.perform(put("/api/v1/notifications/devices/{id}", installationId)
                        .header(HttpHeaders.AUTHORIZATION, "Bearer unlinked")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"token\":\"some-device-token\",\"revocationToken\":\"AAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAA\",\"platform\":\"ANDROID\"}"))
                .andExpect(status().isOk());
        assertThat(jdbc.queryForObject(
                "select person_id from notification.push_device where installation_id=?",
                UUID.class, installationId)).isEqualTo(provisioned);
        assertThat(notifications.maySubscribe(new JwtAuthenticationToken(JwtFixtures.jwt("linked")), personId))
                .isTrue();
        assertThat(notifications.maySubscribe(new JwtAuthenticationToken(JwtFixtures.jwt("unlinked")), personId))
                .isFalse();
        assertThat(notifications.maySubscribe(new JwtAuthenticationToken(JwtFixtures.jwt("linked")), UUID.randomUUID()))
                .isFalse();
    }

    @Test
    void registeringAndDeletingDeviceRequiresOwnershipAndNeverEchoesToken() throws Exception {
        UUID owner = person("owner");
        person("outsider");
        UUID installation = UUID.randomUUID();
        mvc.perform(put("/api/v1/notifications/devices/{id}", installation)
                        .header(HttpHeaders.AUTHORIZATION, "Bearer owner")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"token\":\"fcm-device-secret-one\",\"revocationToken\":\"AAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAA\",\"platform\":\"ANDROID\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.installationId").value(installation.toString()))
                .andExpect(jsonPath("$.active").value(true))
                .andExpect(jsonPath("$.token").doesNotExist());
        assertThat(jdbc.queryForObject("select person_id from notification.push_device where installation_id=?",
                UUID.class, installation)).isEqualTo(owner);
        mvc.perform(put("/api/v1/notifications/devices/{id}", installation)
                        .header(HttpHeaders.AUTHORIZATION, "Bearer outsider")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"token\":\"fcm-stolen-installation\",\"revocationToken\":\"AAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAA\",\"platform\":\"IOS\"}"))
                .andExpect(status().isForbidden());
        mvc.perform(delete("/api/v1/notifications/devices/{id}", installation)
                        .header(HttpHeaders.AUTHORIZATION, "Bearer outsider"))
                .andExpect(status().isForbidden());
        mvc.perform(delete("/api/v1/notifications/devices/{id}", installation)
                        .header(HttpHeaders.AUTHORIZATION, "Bearer owner"))
                .andExpect(status().isNoContent());
        assertThat(jdbc.queryForObject("select active from notification.push_device where installation_id=?",
                Boolean.class, installation)).isFalse();
    }

    @Test
    void tokenRotationPreservesDeviceOwnerAndRejectsInvalidPlatform() throws Exception {
        person("owner");
        UUID installation = UUID.randomUUID();
        for (String token : List.of("old-fcm-token", "new-fcm-token")) {
            mvc.perform(put("/api/v1/notifications/devices/{id}", installation)
                            .header(HttpHeaders.AUTHORIZATION, "Bearer owner")
                            .contentType(MediaType.APPLICATION_JSON)
                            .content("{\"token\":\"%s\",\"revocationToken\":\"AAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAA\",\"platform\":\"IOS\"}".formatted(token)))
                    .andExpect(status().isOk());
        }
        assertThat(jdbc.queryForObject("select token from notification.push_device where installation_id=?",
                String.class, installation)).isEqualTo("new-fcm-token");
        mvc.perform(put("/api/v1/notifications/devices/{id}", UUID.randomUUID())
                        .header(HttpHeaders.AUTHORIZATION, "Bearer owner")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"token\":\"some-token\",\"revocationToken\":\"AAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAA\",\"platform\":\"WEB\"}"))
                .andExpect(status().isBadRequest());
    }

    @Test
    void preferencesExposeUtcQuietHoursAndValidateCompleteInterval() throws Exception {
        person("owner");
        mvc.perform(put("/api/v1/notifications/preferences")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer owner")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"inAppEnabled":true,"emailEnabled":false,"smsEnabled":false,
                                 "pushEnabled":true,"quietHoursStart":"22:00:00",
                                 "quietHoursEnd":"07:00:00","locale":"fr"}
                                """))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.quietHoursTimezone").value("UTC"))
                .andExpect(jsonPath("$.pushEnabled").value(true));
        mvc.perform(put("/api/v1/notifications/preferences")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer owner")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"inAppEnabled":true,"emailEnabled":false,"smsEnabled":false,
                                 "pushEnabled":true,"quietHoursStart":"22:00:00","locale":"fr"}
                                """))
                .andExpect(status().isUnprocessableContent());
    }

    @Test
    void equalQuietHoursAreRejected() throws Exception {
        person("owner");
        mvc.perform(put("/api/v1/notifications/preferences")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer owner")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"inAppEnabled":true,"emailEnabled":false,"smsEnabled":false,
                                 "pushEnabled":true,"quietHoursStart":"22:00:00",
                                 "quietHoursEnd":"22:00:00","locale":"en"}
                                """))
                .andExpect(status().isUnprocessableContent());
    }

    @Test
    void outboxSendsOnlyNotificationReferenceAndHonorsPushDisabledPreference() throws Exception {
        UUID owner = person("owner");
        person("admin");
        registerDevice("owner", UUID.randomUUID(), "fcm-outbox-device");
        jdbc.update("insert into notification.notification_preference(person_id,push_enabled) values (?,true)", owner);
        gateway.outcome = PushGateway.Outcome.ACCEPTED;
        gateway.sentNotification = null;
        createNotification(owner);
        assertThat(jdbc.queryForObject("select count(*) from notification.push_outbox where person_id=? and status='PENDING'",
                Integer.class, owner)).isEqualTo(1);
        worker.dispatch();
        assertThat(gateway.sentNotification).isNotNull();
        assertThat(gateway.sentToken).isEqualTo("fcm-outbox-device");
        assertThat(jdbc.queryForObject("select status from notification.push_outbox where person_id=?",
                String.class, owner)).isEqualTo("SENT");
        jdbc.update("update notification.notification_preference set push_enabled=false where person_id=?", owner);
        createNotification(owner);
        assertThat(jdbc.queryForObject("select count(*) from notification.push_outbox where person_id=? and status='PENDING'",
                Integer.class, owner)).isZero();
    }

    @Test
    void transientDeliveryRetriesAndUnregisteredTokenIsDeactivated() throws Exception {
        UUID owner = person("owner");
        person("admin");
        UUID installation = UUID.randomUUID();
        registerDevice("owner", installation, "fcm-retry-device");
        jdbc.update("insert into notification.notification_preference(person_id,push_enabled) values (?,true)", owner);
        gateway.outcome = PushGateway.Outcome.TRANSIENT_FAILURE;
        createNotification(owner);
        worker.dispatch();
        assertThat(jdbc.queryForObject("select status from notification.push_outbox where person_id=?",
                String.class, owner)).isEqualTo("PENDING");
        assertThat(jdbc.queryForObject("select attempts from notification.push_outbox where person_id=?",
                Integer.class, owner)).isEqualTo(1);
        jdbc.update("update notification.push_outbox set next_attempt_at=now()-interval '1 second' where person_id=?", owner);
        gateway.outcome = PushGateway.Outcome.UNREGISTERED;
        worker.dispatch();
        assertThat(jdbc.queryForObject("select active from notification.push_device where installation_id=?",
                Boolean.class, installation)).isFalse();
        assertThat(jdbc.queryForObject("select status from notification.push_outbox where person_id=?",
                String.class, owner)).isEqualTo("FAILED");
    }

    @Test
    void quietHoursDelayDeliveryAndReadNotificationsAreCancelled() throws Exception {
        UUID owner = person("owner");
        person("admin");
        registerDevice("owner", UUID.randomUUID(), "fcm-quiet-device");
        LocalTime utcNow = LocalTime.now(ZoneOffset.UTC);
        jdbc.update("insert into notification.notification_preference(person_id,push_enabled,quiet_hours_start,quiet_hours_end) values (?,true,?,?)",
                owner, Time.valueOf(utcNow.minusMinutes(30)), Time.valueOf(utcNow.plusMinutes(30)));
        gateway.sentNotification = null;
        createNotification(owner);
        worker.dispatch();
        assertThat(gateway.sentNotification).isNull();
        assertThat(jdbc.queryForObject("select status from notification.push_outbox where person_id=?",
                String.class, owner)).isEqualTo("PENDING");
        assertThat(jdbc.queryForObject("select attempts from notification.push_outbox where person_id=?",
                Integer.class, owner)).isZero();
        jdbc.update("update notification.notification_preference set quiet_hours_start=null,quiet_hours_end=null where person_id=?", owner);
        jdbc.update("update notification.notification_recipient set read_at=now() where person_id=?", owner);
        jdbc.update("update notification.push_outbox set next_attempt_at=now()-interval '1 second' where person_id=?", owner);
        worker.dispatch();
        assertThat(gateway.sentNotification).isNull();
        assertThat(jdbc.queryForObject("select status from notification.push_outbox where person_id=?",
                String.class, owner)).isEqualTo("CANCELLED");
    }

    @Test
    void notificationDetailIsRecipientOnlyAndExpiredNotificationsAreUnavailable() throws Exception {
        UUID recipient = person("owner");
        person("admin");
        person("outsider");
        UUID id = createNotification(recipient);
        mvc.perform(get("/api/v1/notifications/{id}", id)
                        .header(HttpHeaders.AUTHORIZATION, "Bearer owner"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id").value(id.toString()));
        mvc.perform(get("/api/v1/notifications/{id}", id)
                        .header(HttpHeaders.AUTHORIZATION, "Bearer outsider"))
                .andExpect(status().isNotFound());
        jdbc.update("update notification.notification set created_at=now()-interval '2 hours',expires_at=now()-interval '1 hour' where id=?", id);
        mvc.perform(get("/api/v1/notifications/{id}", id)
                        .header(HttpHeaders.AUTHORIZATION, "Bearer owner"))
                .andExpect(status().isNotFound());
    }

    @Test
    void rotatedLogoutCapabilityCannotRevokeNewTokenAndCurrentCapabilityWorksWithoutSession() throws Exception {
        person("owner");
        UUID installation = UUID.randomUUID();
        String first = capability(installation, "first-device-token");
        String rotated = capability(installation, "rotated-device-token");
        assertThat(first).hasSize(43).isNotEqualTo(rotated);
        assertThat(jdbc.queryForObject("select revocation_hash from notification.push_device where installation_id=?",
                String.class, installation)).isNotEqualTo(rotated);
        revokeCapability(installation, first);
        assertThat(jdbc.queryForObject("select active from notification.push_device where installation_id=?",
                Boolean.class, installation)).isTrue();
        revokeCapability(installation, rotated);
        assertThat(jdbc.queryForObject("select active from notification.push_device where installation_id=?",
                Boolean.class, installation)).isFalse();
        assertThat(jdbc.queryForObject("select token from notification.push_device where installation_id=?",
                String.class, installation)).isNull();
        revokeCapability(installation, rotated);
    }

    @Test
    void disabledProviderDoesNotEnqueuePush() throws Exception {
        UUID recipient = person("owner");
        person("admin");
        registerDevice("owner", UUID.randomUUID(), "fcm-disabled-provider");
        jdbc.update("insert into notification.notification_preference(person_id,push_enabled) values (?,true)", recipient);
        gateway.active = false;
        createNotification(recipient);
        assertThat(jdbc.queryForObject("select count(*) from notification.push_outbox where person_id=?",
                Integer.class, recipient)).isZero();
    }

    private String capability(UUID installation, String token) throws Exception {
        String secret = (token.startsWith("first") ? "A" : "B").repeat(43);
        String response = mvc.perform(put("/api/v1/notifications/devices/{id}", installation)
                        .header(HttpHeaders.AUTHORIZATION, "Bearer owner")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"token\":\"%s\",\"revocationToken\":\"%s\",\"platform\":\"IOS\"}".formatted(token, secret)))
                .andExpect(status().isOk()).andReturn().getResponse().getContentAsString();
        return json.readTree(response).path("revocationToken").asText();
    }

    private void revokeCapability(UUID installation, String capability) throws Exception {
        mvc.perform(post("/api/v1/notifications/devices/revoke")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"installationId\":\"%s\",\"revocationToken\":\"%s\"}".formatted(installation, capability)))
                .andExpect(status().isNoContent());
    }

    private void registerDevice(String actor, UUID installation, String token) throws Exception {
        mvc.perform(put("/api/v1/notifications/devices/{id}", installation)
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + actor)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"token\":\"%s\",\"revocationToken\":\"AAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAA\",\"platform\":\"ANDROID\"}".formatted(token)))
                .andExpect(status().isOk());
    }

    private UUID createNotification(UUID recipient) throws Exception {
        String response = mvc.perform(post("/api/v1/notifications")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer admin")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"type":"LAB_RESULT_READY","title":"Private clinical title",
                                 "body":"Private clinical result detail","recipientPersonIds":["%s"]}
                                """.formatted(recipient)))
                .andExpect(status().isCreated()).andReturn().getResponse().getContentAsString();
        return UUID.fromString(json.readTree(response).get(0).path("id").asText());
    }

    static class FakePushGateway implements PushGateway {
        boolean active = true;
        Outcome outcome = Outcome.ACCEPTED;
        UUID sentNotification;
        String sentToken;
        @Override public boolean enabled() { return active; }
        @Override public Result send(String token, UUID notificationId, String locale) {
            sentToken = token;
            sentNotification = notificationId;
            return new Result(outcome, outcome == Outcome.ACCEPTED ? "fake-provider-id" : null);
        }
    }

    private UUID person(String token) {
        UUID id = UUID.randomUUID();
        UUID keycloak = UUID.randomUUID();
        jdbc.update(
                "insert into identity.person(id,keycloak_user_id,person_number,first_name,last_name,status) values (?,?,?,?,?,'ACTIVE')",
                id, keycloak, "PER-" + id, token, "User");
        JwtFixtures.SUBJECTS.put(token, keycloak);
        return id;
    }

    @TestConfiguration(proxyBeanMethods = false)
    static class JwtFixtures {
        static final Map<String, UUID> SUBJECTS = new HashMap<>();

        static Jwt jwt(String token) {
            Instant now = Instant.now();
            return Jwt.withTokenValue(token)
                    .header("alg", "RS256")
                    .subject(SUBJECTS.getOrDefault(token, UUID.randomUUID()).toString())
                    .issuer("https://keycloak.example/realms/healthys")
                    .issuedAt(now)
                    .expiresAt(now.plusSeconds(300))
                    .claim("realm_access", Map.of("roles", List.of("admin".equals(token) ? "PLATFORM_ADMIN" : "PATIENT")))
                    .claim("resource_access", Map.of())
                    .build();
        }

        @Bean
        @Primary
        FakePushGateway pushGateway() { return new FakePushGateway(); }

        @Bean
        JwtDecoder jwtDecoder() {
            return JwtFixtures::jwt;
        }
    }
}
