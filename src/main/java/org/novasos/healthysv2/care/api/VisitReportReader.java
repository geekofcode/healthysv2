package org.novasos.healthysv2.care.api;

import java.time.Instant;
import java.util.UUID;

/** Authorized access to the latest immutable validated report for document generation. */
public interface VisitReportReader {
    ValidatedVisitReport validatedReport(UUID visitId);
    record ValidatedVisitReport(UUID visitId, UUID episodeId, UUID patientId, UUID organizationId,
            UUID professionalId, String doctorName, long revision, UUID revisionId, Instant validatedAt,
            String reason, String observations, String diagnosis, String treatment, String followUp) {}
}
