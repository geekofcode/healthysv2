package org.novasos.healthysv2.consultation.api;

import java.time.Instant;
import java.util.List;
import java.util.UUID;
import org.novasos.healthysv2.consultation.api.ConsultationDtos.DiagnosisResponse;

public final class PatientConsultationDtos {
    private PatientConsultationDtos() {}
    public record PatientConsultationSummary(UUID id, String consultationNumber, UUID patientId,
            UUID professionalId, String professionalName, UUID organizationId, String organizationName,
            String type, Instant startedAt, Instant completedAt, String status) {}
    public record SharedConsultationNote(UUID id, String noteType, String content, Instant createdAt, Instant updatedAt) {}
    public record PatientConsultationDetail(PatientConsultationSummary consultation,
            List<DiagnosisResponse> diagnoses, List<SharedConsultationNote> notes) {}
}
