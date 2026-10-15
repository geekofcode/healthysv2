package org.novasos.healthysv2.care.api;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

/** Authorized access to the latest immutable validated report for document generation. */
public interface VisitReportReader {
  record ExamRequest(String name, String instructions) {}

  ValidatedVisitReport validatedReport(UUID visitId);

  record ValidatedVisitReport(
      UUID visitId,
      UUID episodeId,
      UUID patientId,
      UUID organizationId,
      UUID professionalId,
      String doctorName,
      long revision,
      UUID revisionId,
      Instant validatedAt,
      String reason,
      String observations,
      String diagnosis,
      String treatment,
      String followUp,
      UUID aiJobId,
      boolean patientVisible,
      List<ExamRequest> examRequests) {
    public ValidatedVisitReport {
      examRequests = examRequests == null ? List.of() : List.copyOf(examRequests);
    }

    public ValidatedVisitReport(
        UUID visitId,
        UUID episodeId,
        UUID patientId,
        UUID organizationId,
        UUID professionalId,
        String doctorName,
        long revision,
        UUID revisionId,
        Instant validatedAt,
        String reason,
        String observations,
        String diagnosis,
        String treatment,
        String followUp,
        UUID aiJobId,
        boolean patientVisible) {
      this(
          visitId,
          episodeId,
          patientId,
          organizationId,
          professionalId,
          doctorName,
          revision,
          revisionId,
          validatedAt,
          reason,
          observations,
          diagnosis,
          treatment,
          followUp,
          aiJobId,
          patientVisible,
          List.of());
    }

    public ValidatedVisitReport(
        UUID visitId,
        UUID episodeId,
        UUID patientId,
        UUID organizationId,
        UUID professionalId,
        String doctorName,
        long revision,
        UUID revisionId,
        Instant validatedAt,
        String reason,
        String observations,
        String diagnosis,
        String treatment,
        String followUp,
        UUID aiJobId) {
      this(
          visitId,
          episodeId,
          patientId,
          organizationId,
          professionalId,
          doctorName,
          revision,
          revisionId,
          validatedAt,
          reason,
          observations,
          diagnosis,
          treatment,
          followUp,
          aiJobId,
          true);
    }

    public ValidatedVisitReport(
        UUID visitId,
        UUID episodeId,
        UUID patientId,
        UUID organizationId,
        UUID professionalId,
        String doctorName,
        long revision,
        UUID revisionId,
        Instant validatedAt,
        String reason,
        String observations,
        String diagnosis,
        String treatment,
        String followUp) {
      this(
          visitId,
          episodeId,
          patientId,
          organizationId,
          professionalId,
          doctorName,
          revision,
          revisionId,
          validatedAt,
          reason,
          observations,
          diagnosis,
          treatment,
          followUp,
          null,
          true);
    }
  }
}
