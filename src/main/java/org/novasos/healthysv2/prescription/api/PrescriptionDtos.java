package org.novasos.healthysv2.prescription.api;

import jakarta.validation.Valid;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Future;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import java.util.UUID;

public final class PrescriptionDtos {
    private PrescriptionDtos() {
    }

    public record CreatePrescriptionRequest(@NotNull UUID patientId, UUID consultationId,
                                            @NotNull UUID prescriberId, UUID organizationId,
                                            @Future Instant expiresAt,
                                            @NotEmpty List<@Valid PrescriptionItemRequest> items) {
    }

    public record PrescriptionItemRequest(@NotNull UUID medicationCatalogId,
                                          @NotBlank @Size(max = 100) String dosage,
                                          @NotBlank @Size(max = 100) String frequency,
                                          @NotBlank @Size(max = 100) String route,
                                          @NotBlank @Size(max = 100) String duration,
                                          @NotNull @DecimalMin("0.001") BigDecimal quantity,
                                          String instructions) {
    }

    public record PrescriptionSummary(UUID id, String prescriptionNumber, UUID patientId,
                                      UUID organizationId, Instant prescribedAt, Instant expiresAt, String status) {
    }

    public record PrescriptionResponse(UUID id, String prescriptionNumber, UUID patientId,
                                       UUID consultationId, UUID prescriberId, UUID organizationId,
                                       Instant prescribedAt, Instant expiresAt, String status,
                                       List<PrescriptionItemResponse> items) {
    }

    public record PrescriptionItemResponse(UUID id, UUID medicationCatalogId, String medicationCode,
                                           String medicationName, String dosage, String frequency, String route,
                                           String duration, BigDecimal quantity, BigDecimal quantityDispensed,
                                           BigDecimal quantityRemaining, String instructions) {
    }
}
