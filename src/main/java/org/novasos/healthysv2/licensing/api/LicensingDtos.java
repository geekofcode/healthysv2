package org.novasos.healthysv2.licensing.api;

import jakarta.validation.constraints.*;
import java.util.*;

public final class LicensingDtos {
  private LicensingDtos() {}

  public record PlanRequest(
      @NotBlank String name,
      @PositiveOrZero long amountMinor,
      @NotBlank @Pattern(regexp = "[a-zA-Z]{3}") String currency,
      @NotBlank @Pattern(regexp = "MONTH|YEAR") String interval,
      @PositiveOrZero int doctorQuota,
      @PositiveOrZero int professionalQuota,
      @PositiveOrZero int cashierQuota,
      @PositiveOrZero int accountantQuota,
      boolean aiAssistantEnabled,
      @NotBlank String stripePriceId,
      boolean active) {}

  public record TeamRequest(
      UUID personId,
      @Email String email,
      @NotBlank @Pattern(regexp = "HOSPITAL_ADMIN|DOCTOR|NURSE|HOSPITAL_AGENT|CASHIER|ACCOUNTANT")
          String role) {}

  public record TeamUpdate(
      @NotBlank @Pattern(regexp = "HOSPITAL_ADMIN|DOCTOR|NURSE|HOSPITAL_AGENT|CASHIER|ACCOUNTANT")
          String role,
      @NotBlank @Pattern(regexp = "ACTIVE|INACTIVE") String status) {}

  public record CheckoutRequest(@NotNull UUID planId) {}
}
