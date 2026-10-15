package org.novasos.healthysv2.care;

import jakarta.validation.Valid;
import jakarta.validation.constraints.*;
import java.time.Instant;
import java.util.*;
import org.novasos.healthysv2.care.api.VisitReportReader.ValidatedVisitReport;

public final class EncounterDtos {
  private EncounterDtos() {}

  public record ExamRequest(
      @NotBlank @Size(max = 500) String name, @Size(max = 4000) String instructions) {}

  public record Finalize(
      @PositiveOrZero long version, @Future Instant followUpAt, UUID followUpProfessionalId) {}

  public record Version(@PositiveOrZero long version) {}

  public record Draft(
      @PositiveOrZero long version,
      @NotBlank @Size(max = 10000) String reason,
      @Size(max = 50000) String observations,
      @Size(max = 20000) String diagnosis,
      @Size(max = 20000) String treatment,
      @Size(max = 20000) String followUp,
      UUID aiJobId,
      @Size(max = 100) List<@Valid ExamRequest> examRequests) {
    public Draft {
      examRequests = examRequests == null ? List.of() : List.copyOf(examRequests);
    }

    public Draft(
        long version,
        String reason,
        String observations,
        String diagnosis,
        String treatment,
        String followUp,
        UUID aiJobId) {
      this(version, reason, observations, diagnosis, treatment, followUp, aiJobId, List.of());
    }

    public Draft(
        long version,
        String reason,
        String observations,
        String diagnosis,
        String treatment,
        String followUp) {
      this(version, reason, observations, diagnosis, treatment, followUp, null);
    }
  }

  public record Correction(
      @PositiveOrZero long version, @NotBlank @Size(max = 2000) String correctionReason) {}

  public record Started(
      UUID visitId,
      UUID episodeId,
      String status,
      long version,
      Instant startedAt,
      Instant episodeStartedAt,
      Instant episodeExpiresAt) {}

  public record Report(
      UUID visitId,
      UUID episodeId,
      UUID patientId,
      UUID organizationId,
      UUID professionalId,
      String visitStatus,
      long visitVersion,
      long version,
      int revision,
      String status,
      String reason,
      String observations,
      String diagnosis,
      String treatment,
      String followUp,
      String correctionReason,
      List<ValidatedVisitReport> revisions,
      UUID aiJobId,
      List<ExamRequest> examRequests,
      UUID followUpVisitId) {}
}
