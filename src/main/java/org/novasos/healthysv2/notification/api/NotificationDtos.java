package org.novasos.healthysv2.notification.api;

import java.time.Instant;
import java.time.LocalTime;
import java.util.Set;
import java.util.UUID;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

public final class NotificationDtos {
    private NotificationDtos() {
    }

    public record CreateNotificationRequest(
            @NotBlank @Size(max = 80) String type,
            @Size(max = 255) String title,
            @NotBlank @Size(max = 10_000) String body,
            @NotEmpty Set<UUID> recipientPersonIds,
            @Size(max = 50) String resourceType,
            UUID resourceId,
            @Size(max = 1_000) String actionUrl,
            @Pattern(regexp = "LOW|NORMAL|HIGH|URGENT") String priority,
            Instant expiresAt) {
        public CreateNotificationRequest {
            recipientPersonIds = recipientPersonIds == null
                    ? Set.of()
                    : Set.copyOf(recipientPersonIds);
        }
    }

    public record NotificationResponse(
            UUID id,
            String type,
            String title,
            String body,
            String resourceType,
            UUID resourceId,
            String actionUrl,
            String priority,
            Instant createdAt,
            Instant expiresAt,
            String status,
            Instant readAt,
            boolean read) {
    }

    public record UnreadCountResponse(long unreadCount) {
    }

    public record MarkAllReadResponse(int updatedCount) {
    }

    public record NotificationPreferencesResponse(
            boolean inAppEnabled,
            boolean emailEnabled,
            boolean smsEnabled,
            boolean pushEnabled,
            LocalTime quietHoursStart,
            LocalTime quietHoursEnd,
            String locale,
            Instant updatedAt) {
    }

    public record UpdateNotificationPreferencesRequest(
            @NotNull Boolean inAppEnabled,
            @NotNull Boolean emailEnabled,
            @NotNull Boolean smsEnabled,
            @NotNull Boolean pushEnabled,
            LocalTime quietHoursStart,
            LocalTime quietHoursEnd,
            @NotBlank @Pattern(regexp = "en|fr") String locale) {
        public boolean hasConsistentQuietHours() {
            return (quietHoursStart == null) == (quietHoursEnd == null);
        }
    }
}
