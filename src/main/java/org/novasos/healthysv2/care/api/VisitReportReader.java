package org.novasos.healthysv2.care.api;

import java.time.Instant;
import java.util.UUID;

/** Authorized access to the latest immutable validated report for document generation. */
public interface VisitReportReader {
    ValidatedVisitReport validatedReport(UUID visitId);
    record ValidatedVisitReport(UUID visitId, UUID episodeId, UUID patientId, UUID organizationId,
            UUID professionalId, String doctorName, long revision, UUID revisionId, Instant validatedAt,
            String reason, String observations, String diagnosis, String treatment, String followUp, UUID aiJobId) {
        public ValidatedVisitReport(UUID visitId, UUID episodeId, UUID patientId, UUID organizationId,
                UUID professionalId, String doctorName, long revision, UUID revisionId, Instant validatedAt,
                String reason, String observations, String diagnosis, String treatment, String followUp) {
            this(visitId,episodeId,patientId,organizationId,professionalId,doctorName,revision,revisionId,validatedAt,
                reason,observations,diagnosis,treatment,followUp,null);
        }
    }
}
