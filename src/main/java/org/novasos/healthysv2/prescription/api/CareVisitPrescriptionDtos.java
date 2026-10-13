package org.novasos.healthysv2.prescription.api;

import jakarta.validation.Valid;
import jakarta.validation.constraints.*;
import java.time.Instant;
import java.util.List;
import java.util.UUID;

public final class CareVisitPrescriptionDtos {
    private CareVisitPrescriptionDtos() {}
    public record Item(@NotBlank @Size(max=500) String medication,
            @NotBlank @Size(max=500) String dosage,
            @NotBlank @Size(max=150) String route,
            @NotBlank @Size(max=300) String frequency,
            @NotBlank @Size(max=300) String duration,
            @Size(max=2000) String instructions) {}
    public record SaveRequest(@Min(0) long expectedVersion,
            @NotNull @Size(max=100) List<@Valid Item> items, @Size(max=2000) String correctionReason) {}
    public record VersionRequest(@Min(0) long expectedVersion) {}
    public record CorrectionRequest(@Min(0) long expectedVersion, @NotBlank @Size(max=2000) String reason) {}
    public record VisitPrescriptionResponse(UUID visitId, long version, int currentRevision, String status,
            List<Item> items, String correctionReason, Instant validatedAt) {}
    public record Revision(UUID id, int revision, List<Item> items, String correctionReason, Instant validatedAt, UUID validatedBy) {}
    public record ValidatedPrescription(UUID visitId, UUID revisionId, int revision, Instant validatedAt,
            List<Item> items) {}
}
