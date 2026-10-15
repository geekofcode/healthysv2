package org.novasos.healthysv2.patienthealth.api;

import jakarta.validation.Valid;
import jakarta.validation.constraints.*;
import java.time.*;
import java.util.*;

public final class PatientHealthDtos {
  private PatientHealthDtos() {}

  public record Parameter(
      UUID id,
      String code,
      String label,
      String valueType,
      String unit,
      String loincCode,
      boolean custom,
      boolean active) {}

  public record ParameterInput(
      @NotBlank @Size(max = 120) String label,
      @NotBlank @Pattern(regexp = "NUMERIC|TEXT|BOOLEAN") String valueType,
      @Size(max = 30) String unit) {}

  public record ObservationInput(
      @NotNull UUID parameterId,
      @NotBlank @Size(max = 2000) String value,
      @NotNull Instant measuredAt,
      @Size(max = 2000) String note,
      UUID supersedesId) {}

  public record Observation(
      UUID id,
      UUID parameterId,
      String code,
      String label,
      String valueType,
      String value,
      String unit,
      String note,
      Instant measuredAt,
      Instant recordedAt,
      boolean selfReported,
      UUID organizationId,
      String organizationName,
      UUID visitId,
      String actorName,
      UUID supersedesId) {}

  public record ObservationPage(List<Observation> items, long total, int page, int size) {}

  public record DeclarationInput(
      @NotBlank
          @Pattern(
              regexp =
                  "BLOOD_GROUP|RHESUS|ALLERGY|CHRONIC_CONDITION|MEDICAL_HISTORY|SURGICAL_HISTORY|FAMILY_HISTORY|DISABILITY")
          String kind,
      @NotBlank @Size(max = 200) String label,
      @NotBlank @Size(max = 2000) String value,
      @Size(max = 4000) String detail,
      @PastOrPresent LocalDate occurredOn,
      UUID supersedesId) {}

  public record Declaration(
      UUID id,
      String kind,
      String label,
      String value,
      String detail,
      LocalDate occurredOn,
      Instant recordedAt,
      String actorName,
      UUID supersedesId) {}

  public record Measurements(
      @NotNull @Size(min = 1, max = 100) List<@Valid ObservationInput> measurements) {}
}
