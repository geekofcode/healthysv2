package org.novasos.healthysv2.notification;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.LocalTime;

import org.junit.jupiter.api.Test;
import org.novasos.healthysv2.notification.api.NotificationDtos.UpdateNotificationPreferencesRequest;

class NotificationDtosTests {
    @Test
    void quietHoursRequireBothBounds() {
        var invalid = new UpdateNotificationPreferencesRequest(
                true, false, false, false, LocalTime.of(22, 0), null, "en");
        var valid = new UpdateNotificationPreferencesRequest(
                true, false, false, false, LocalTime.of(22, 0), LocalTime.of(7, 0), "en");

        assertThat(invalid.hasConsistentQuietHours()).isFalse();
        assertThat(valid.hasConsistentQuietHours()).isTrue();
    }
}
