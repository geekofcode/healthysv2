package org.novasos.healthysv2.document.api;

import java.time.Instant;
import java.util.UUID;
import jakarta.validation.constraints.NotNull;

public final class PatientDocumentDtos {
    private PatientDocumentDtos() {}
    public record PatientDocumentMetadata(UUID id,String documentNumber,UUID patientId,UUID categoryId,
            String categoryCode,String categoryName,String fileName,String mimeType,long sizeBytes,Instant uploadedAt,String status) {}
    public record PatientVisibilityRequest(@NotNull Boolean patientVisible) {}
    public record PatientVisibilityResponse(UUID id,boolean patientVisible) {}
}
