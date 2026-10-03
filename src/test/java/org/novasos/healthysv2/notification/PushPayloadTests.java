package org.novasos.healthysv2.notification;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.time.Instant;
import java.time.LocalTime;
import java.util.Map;
import java.util.UUID;
import org.junit.jupiter.api.Test;

class PushPayloadTests {
    @Test
    void fcmPayloadContainsOnlyReferenceAndGenericLocalizedAlertWithApnsAndAndroidSettings() {
        UUID id = UUID.randomUUID();
        Map<?, ?> message = (Map<?, ?>) FcmPushGateway.payload("device-token", id, "fr").get("message");
        assertThat(message.keySet().stream().map(Object::toString).toList()).containsExactlyInAnyOrder("token", "notification", "data", "android", "apns");
        assertThat(message.get("data")).isEqualTo(Map.of("notificationId", id.toString()));
        assertThat(message.get("notification")).isEqualTo(Map.of("title", "HEALTH'YS", "body", "Vous avez une nouvelle notification."));
        Map<?, ?> android = (Map<?, ?>) message.get("android");
        assertThat(((Map<?, ?>) android.get("notification")).get("visibility")).isEqualTo("PRIVATE");
        Map<?, ?> apns = (Map<?, ?>) message.get("apns");
        assertThat(((Map<?, ?>) apns.get("headers")).get("apns-push-type")).isEqualTo("alert");
        Map<?, ?> english = (Map<?, ?>) FcmPushGateway.payload("device-token", id, "en").get("message");
        assertThat(english.get("notification")).isEqualTo(Map.of("title", "HEALTH'YS", "body", "You have a new notification."));
    }

    @Test
    void providerResponsesDistinguishRetriesInvalidTokensAndPermanentFailures() {
        ObjectMapper json = new ObjectMapper();
        assertThat(FcmPushGateway.classify(200, "{\"name\":\"projects/test/messages/123\"}", json))
                .isEqualTo(new PushGateway.Result(PushGateway.Outcome.ACCEPTED, "projects/test/messages/123"));
        assertThat(FcmPushGateway.classify(404, "{\"error\":{\"details\":[{\"errorCode\":\"UNREGISTERED\"}]}}", json).outcome())
                .isEqualTo(PushGateway.Outcome.UNREGISTERED);
        for (int status : new int[] {401, 429, 500, 503}) {
            assertThat(FcmPushGateway.classify(status, "{}", json).outcome())
                    .isEqualTo(PushGateway.Outcome.TRANSIENT_FAILURE);
        }
        assertThat(FcmPushGateway.classify(400, "{}", json).outcome()).isEqualTo(PushGateway.Outcome.PERMANENT_FAILURE);
    }

    @Test
    void enabledProviderRequiresConfigurationWhileDisabledProviderIsUsable() {
        ObjectMapper json = new ObjectMapper();
        assertThat(new FcmPushGateway(false, "", "", json).enabled()).isFalse();
        assertThatThrownBy(() -> new FcmPushGateway(true, "", "", json)).isInstanceOf(IllegalStateException.class);
    }

    @Test
    void utcQuietHoursIncludeStartExcludeEndAndCrossMidnight() {
        LocalTime start = LocalTime.of(22, 0);
        LocalTime end = LocalTime.of(7, 0);
        assertThat(PushDeliveryWorker.quietUntil(start, end, Instant.parse("2026-10-03T22:00:00Z")))
                .isEqualTo(Instant.parse("2026-10-04T07:00:00Z"));
        assertThat(PushDeliveryWorker.quietUntil(start, end, Instant.parse("2026-10-04T06:59:00Z")))
                .isEqualTo(Instant.parse("2026-10-04T07:00:00Z"));
        assertThat(PushDeliveryWorker.quietUntil(start, end, Instant.parse("2026-10-04T07:00:00Z"))).isNull();
        assertThat(PushDeliveryWorker.quietUntil(LocalTime.of(12, 0), LocalTime.of(13, 0), Instant.parse("2026-10-04T12:30:00Z")))
                .isEqualTo(Instant.parse("2026-10-04T13:00:00Z"));
        assertThat(PushDeliveryWorker.quietUntil(null, null, Instant.parse("2026-10-04T12:30:00Z"))).isNull();
    }
}
