package org.novasos.healthysv2.patient.api;

import java.time.LocalDate;
import java.util.List;
import java.util.UUID;
import org.novasos.healthysv2.identity.api.EmergencyContactResponse;
import org.novasos.healthysv2.patient.api.PatientDtos.AllergyResponse;
import org.novasos.healthysv2.patient.api.PatientDtos.EmergencyProfileResponse;
import org.novasos.healthysv2.patient.api.PatientDtos.FlagResponse;
import org.novasos.healthysv2.patient.api.PatientDtos.PatientSummary;

/** Patient-facing facts only: private notes are excluded from every section. */
public record PatientMedicalRecordResponse(
        PatientSummary patient,
        List<AllergyResponse> allergies,
        List<MedicalRecordChronicDisease> chronicDiseases,
        List<MedicalRecordHistory> medicalHistories,
        List<MedicalRecordSurgery> surgicalHistories,
        List<MedicalRecordFamilyHistory> familyHistories,
        List<MedicalRecordDisability> disabilities,
        List<FlagResponse> flags,
        EmergencyProfileResponse emergencyProfile,
        List<EmergencyContactResponse> emergencyContacts) {

    public record MedicalRecordChronicDisease(UUID id, UUID diagnosisCatalogId,
            String diagnosisCode, String diagnosisLabel, LocalDate diagnosedAt, String status) {}
    public record MedicalRecordHistory(UUID id, String condition, LocalDate diagnosedAt, LocalDate resolvedAt) {}
    public record MedicalRecordSurgery(UUID id, String procedureName, LocalDate procedureDate, UUID organizationId) {}
    public record MedicalRecordFamilyHistory(UUID id, String relationship, String condition) {}
    public record MedicalRecordDisability(UUID id, String type, String description, LocalDate startDate, String status) {}
}
