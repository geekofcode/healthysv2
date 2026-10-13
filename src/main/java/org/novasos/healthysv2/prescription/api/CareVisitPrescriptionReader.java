package org.novasos.healthysv2.prescription.api;
import java.util.UUID;
public interface CareVisitPrescriptionReader {
    CareVisitPrescriptionDtos.ValidatedPrescription validatedPrescription(UUID visitId);
}
