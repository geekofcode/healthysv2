package org.novasos.healthysv2.care;

import jakarta.validation.Valid;
import jakarta.validation.constraints.*;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import java.util.UUID;
import org.novasos.healthysv2.patienthealth.api.PatientHealthDtos.ObservationInput;

public final class ReceptionDtos {
  private ReceptionDtos() {}

  public record OpenEpisode(
      @NotNull UUID patientId,
      @NotBlank @Size(max = 4000) String reason,
      @NotNull UUID specialityId,
      @NotNull UUID professionalId,
      @NotNull Instant scheduledAt) {}

  public record Intake(
      @Size(max = 10000) String observations,
      @NotNull @Valid Vitals vitals,
      @Size(max = 100) List<@Valid ObservationInput> measurements) {
    public Intake(String observations, Vitals vitals) {
      this(observations, vitals, List.of());
    }

    public Intake {
      measurements = measurements == null ? List.of() : List.copyOf(measurements);
    }
  }

  public record Vitals(
      @DecimalMin("25") @DecimalMax("45") BigDecimal temperatureC,
      @Min(30) @Max(300) Integer systolicBp,
      @Min(10) @Max(200) Integer diastolicBp,
      @Min(10) @Max(300) Integer heartRate,
      @Min(1) @Max(100) Integer respiratoryRate,
      @DecimalMin("0") @DecimalMax("100") BigDecimal oxygenSaturation,
      @DecimalMin("0.1") @DecimalMax("700") BigDecimal weightKg,
      @DecimalMin("10") @DecimalMax("300") BigDecimal heightCm) {}

  public record ReceptionDoctor(
      UUID professionalId, String firstName, String lastName, List<UUID> specialityIds) {}

  public record ReceptionSpeciality(UUID id, String name) {}

  public record ReceptionOptions(
      List<ReceptionDoctor> doctors, List<ReceptionSpeciality> specialities) {}

  public record Visit(
      UUID id,
      UUID professionalId,
      Instant scheduledAt,
      CareVisit.Status status,
      boolean followUp,
      Instant startedAt,
      long version,
      int slotMinutes,
      String reason) {}

  public record Episode(
      UUID id,
      UUID patientId,
      UUID organizationId,
      String reason,
      UUID specialityId,
      CareEpisode.Status status,
      Instant createdAt,
      Instant startedAt,
      Instant expiresAt,
      long followUpSeconds,
      List<Visit> visits,
      Intake intake,
      UUID tariffId,
      BigDecimal tariffPrice,
      String tariffCurrency,
      BigDecimal tariffTaxRate) {
    public Episode(
        UUID id,
        UUID p,
        UUID o,
        String r,
        UUID s,
        CareEpisode.Status st,
        Instant c,
        Instant start,
        Instant exp,
        long secs,
        List<Visit> vs,
        Intake i) {
      this(id, p, o, r, s, st, c, start, exp, secs, vs, i, null, null, null, null);
    }
  }
}
