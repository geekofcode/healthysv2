package org.novasos.healthysv2.teleconsultation;

import static org.assertj.core.api.Assertions.*;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.time.Instant;
import java.util.Base64;
import java.util.UUID;
import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import org.junit.jupiter.api.Test;

class LiveKitTokenServiceTests {
    private static final String SECRET = "test-secret-for-signing-only-at-least-32-characters";
    private final ObjectMapper json = new ObjectMapper();

    @Test
    void signsRoomScopedShortLivedCredentialWithServerSecret() throws Exception {
        var properties = properties();
        UUID person = UUID.randomUUID();
        Instant before = Instant.now();
        var response = new LiveKitTokenService(properties).create(person, "healthys-test-room", "PATIENT");
        String[] segments = response.token().split("\\.");
        assertThat(segments).hasSize(3);
        JsonNode header = decode(segments[0]);
        JsonNode claims = decode(segments[1]);
        assertThat(header.path("alg").asText()).isEqualTo("HS256");
        assertThat(claims.path("iss").asText()).isEqualTo("test-api-key");
        assertThat(claims.path("sub").asText()).isEqualTo(person.toString());
        assertThat(claims.path("video").path("room").asText()).isEqualTo("healthys-test-room");
        assertThat(claims.path("video").path("roomJoin").asBoolean()).isTrue();
        assertThat(claims.path("video").path("roomAdmin").asBoolean()).isFalse();
        assertThat(claims.path("video").path("roomCreate").asBoolean()).isFalse();
        Instant expiry = Instant.ofEpochSecond(claims.path("exp").asLong());
        assertThat(expiry).isBetween(before.plusSeconds(299), Instant.now().plusSeconds(301));
        assertThat(Math.abs(Duration.between(expiry, response.expiresAt()).toMillis())).isLessThan(2000);
        Mac signing = Mac.getInstance("HmacSHA256");
        signing.init(new SecretKeySpec(SECRET.getBytes(StandardCharsets.UTF_8), "HmacSHA256"));
        byte[] expected = signing.doFinal((segments[0] + "." + segments[1]).getBytes(StandardCharsets.UTF_8));
        assertThat(Base64.getUrlDecoder().decode(segments[2])).isEqualTo(expected);
        assertThat(claims.toString()).doesNotContain(SECRET);
    }

    @Test
    void tokenResponseStringCannotExposeBearerCredential() {
        var response = new LiveKitTokenService(properties()).create(UUID.randomUUID(), "healthys-test-room", "PATIENT");
        assertThat(response.toString()).doesNotContain(response.token()).contains("[REDACTED]");
    }

    @Test
    void invalidLifetimeFailsBeforeIssuingCredential() {
        var properties = properties();
        properties.setTokenTtlMinutes(16);
        assertThatThrownBy(() -> new LiveKitTokenService(properties).create(UUID.randomUUID(), "room", "PATIENT"))
            .isInstanceOf(IllegalStateException.class);
    }

    private JsonNode decode(String segment) throws Exception {
        return json.readTree(Base64.getUrlDecoder().decode(segment));
    }

    private LiveKitProperties properties() {
        var properties = new LiveKitProperties();
        properties.setUrl("wss://livekit.example.org");
        properties.setApiKey("test-api-key");
        properties.setApiSecret(SECRET);
        properties.setTokenTtlMinutes(5);
        return properties;
    }
}
