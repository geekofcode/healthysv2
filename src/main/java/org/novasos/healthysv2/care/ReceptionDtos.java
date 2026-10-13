package org.novasos.healthysv2.care;

import jakarta.validation.Valid;
import jakarta.validation.constraints.*;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import java.util.UUID;

public final class ReceptionDtos {
    private ReceptionDtos() {}
    public record OpenEpisode(@NotNull UUID patientId, @NotBlank @Size(max=4000) String reason,
            @NotNull UUID specialityId, @NotNull UUID professionalId, @NotNull Instant scheduledAt) {}
    public record Intake(@Size(max=10000) String observations, @NotNull @Valid Vitals vitals) {}
    public record Vitals(
            @DecimalMin("25") @DecimalMax("45") BigDecimal temperatureC,
            @Min(30) @Max(300) Integer systolicBp, @Min(10) @Max(200) Integer diastolicBp,
            @Min(10) @Max(300) Integer heartRate, @Min(1) @Max(100) Integer respiratoryRate,
            @DecimalMin("0") @DecimalMax("100") BigDecimal oxygenSaturation,
            @DecimalMin("0.1") @DecimalMax("700") BigDecimal weightKg,
            @DecimalMin("10") @DecimalMax("300") BigDecimal heightCm) {}
    public record ReceptionDoctor(UUID professionalId, String firstName, String lastName, List<UUID> specialityIds) {}
    public record ReceptionSpeciality(UUID id, String name) {}
    public record ReceptionOptions(List<ReceptionDoctor> doctors, List<ReceptionSpeciality> specialities) {}
    public record Visit(UUID id, UUID professionalId, Instant scheduledAt, CareVisit.Status status,
            boolean followUp, Instant startedAt, long version, int slotMinutes) {}
    public record Episode(UUID id, UUID patientId, UUID organizationId, String reason, UUID specialityId,
            CareEpisode.Status status, Instant createdAt, Instant startedAt, Instant expiresAt,
            long followUpSeconds, List<Visit> visits, Intake intake) {}
}
