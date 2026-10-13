package org.novasos.healthysv2.care;

import jakarta.validation.constraints.*;
import java.time.Instant;
import java.util.*;
import org.novasos.healthysv2.care.api.VisitReportReader.ValidatedVisitReport;

public final class EncounterDtos {
    private EncounterDtos() {}
    public record Version(@PositiveOrZero long version) {}
    public record Draft(@PositiveOrZero long version, @NotBlank @Size(max=10000) String reason,
            @Size(max=50000) String observations, @Size(max=20000) String diagnosis,
            @Size(max=20000) String treatment, @Size(max=20000) String followUp, UUID aiJobId) {
        public Draft(long version, String reason, String observations, String diagnosis, String treatment, String followUp) {
            this(version,reason,observations,diagnosis,treatment,followUp,null);
        }
    }
    public record Correction(@PositiveOrZero long version, @NotBlank @Size(max=2000) String correctionReason) {}
    public record Started(UUID visitId, UUID episodeId, String status, long version,
            Instant startedAt, Instant episodeStartedAt, Instant episodeExpiresAt) {}
    public record Report(UUID visitId, UUID episodeId, UUID patientId, UUID organizationId,
            UUID professionalId, String visitStatus, long visitVersion, long version, int revision,
            String status, String reason, String observations, String diagnosis, String treatment,
            String followUp, String correctionReason, List<ValidatedVisitReport> revisions, UUID aiJobId) {}
}
