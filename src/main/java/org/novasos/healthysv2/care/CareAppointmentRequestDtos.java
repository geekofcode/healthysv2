package org.novasos.healthysv2.care;

import jakarta.validation.constraints.*;
import java.time.Instant;
import java.util.UUID;

public final class CareAppointmentRequestDtos {
  private CareAppointmentRequestDtos() {}

  public record SelfRequest(
      @NotNull UUID organizationId,
      @NotNull UUID specialityId,
      @NotBlank @Size(max = 4000) String reason,
      @NotNull @Future Instant preferredAt) {}

  public record StaffRequest(
      @NotNull UUID patientId,
      @NotNull UUID specialityId,
      @NotBlank @Size(max = 4000) String reason,
      @NotNull @Future Instant preferredAt,
      @NotBlank @Pattern(regexp = "PHONE|RECEPTION") String source) {}

  public record Approval(@NotNull UUID professionalId, @NotNull @Future Instant scheduledAt) {}

  public record Rejection(@NotBlank @Size(max = 2000) String reason) {}

  public record Request(
      UUID id,
      UUID organizationId,
      UUID patientId,
      UUID specialityId,
      String reason,
      Instant preferredAt,
      String source,
      String status,
      Instant createdAt,
      UUID decidedBy,
      Instant decidedAt,
      String rejectionReason,
      UUID episodeId,
      UUID visitId) {}

  public record Hospital(UUID id, String name, String city, UUID countryId, String countryName) {}
}
