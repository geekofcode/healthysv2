package org.novasos.healthysv2.care;

import jakarta.validation.constraints.*;
import java.time.Instant;
import java.util.List;
import java.util.UUID;

public final class VisitSchedulingDtos {
    private VisitSchedulingDtos() {}
    public record Program(@NotNull UUID professionalId, @NotNull Instant scheduledAt,
            @NotNull @Min(0) Long version, @Size(max=2000) String reason) {}
    public record History(UUID id, UUID previousProfessionalId, UUID professionalId,
            Instant previousScheduledAt, Instant scheduledAt, String previousStatus, String status,
            UUID actorPersonId, String reason, Instant changedAt) {}
    public record Visit(UUID id, UUID episodeId, UUID patientId, UUID organizationId,
            UUID professionalId, Instant scheduledAt, String status, boolean followUp, long version,
            int slotMinutes, Instant createdAt, List<History> history) {}
}
