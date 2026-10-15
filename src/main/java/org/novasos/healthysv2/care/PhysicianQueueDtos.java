package org.novasos.healthysv2.care;

import jakarta.validation.constraints.*;
import java.time.*;
import java.util.*;

public final class PhysicianQueueDtos {
  private PhysicianQueueDtos() {}

  public record Visit(
      UUID visitId,
      UUID episodeId,
      UUID patientId,
      String patientFirstName,
      String patientLastName,
      Instant scheduledAt,
      String status,
      String presence,
      long position,
      long visitVersion) {}

  public record Calendar(
      UUID professionalId,
      LocalDate date,
      String timezone,
      long version,
      List<Visit> visits,
      boolean closed,
      List<ClosedWindow> closedWindows) {
    public Calendar(UUID p, LocalDate d, String t, long v, List<Visit> vs) {
      this(p, d, t, v, vs, false, List.of());
    }
  }

  public record ClosedWindow(Instant windowStart, Instant windowEnd, String reason) {}

  public record Close(
      @NotNull UUID professionalId,
      @NotNull LocalDate date,
      @Min(0) long expectedVersion,
      @NotBlank String reason,
      Instant windowStart,
      Instant windowEnd) {
    public Close(UUID p, LocalDate d, long v, String r) {
      this(p, d, v, r, null, null);
    }
  }

  public record Defer(
      @NotNull UUID visitId, @Min(0) long expectedVersion, @NotBlank String reason) {}

  public record Reassignment(
      UUID id, UUID visitId, UUID sourceProfessionalId, LocalDate sourceDate, String reason) {}

  public record Presence(
      @NotNull UUID visitId, boolean present, @NotNull @Min(0) Long expectedVersion) {}

  public record Next(
      @NotNull UUID professionalId,
      @NotNull LocalDate date,
      @NotNull @Min(0) Long expectedVersion) {}

  public record Order(
      @NotNull UUID professionalId,
      @NotNull LocalDate date,
      @NotNull List<@NotNull UUID> visitIds,
      @NotNull @Min(0) Long expectedVersion) {}
}
