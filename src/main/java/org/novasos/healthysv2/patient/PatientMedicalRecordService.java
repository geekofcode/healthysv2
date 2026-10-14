package org.novasos.healthysv2.patient;

import java.util.Map;
import java.util.UUID;
import org.novasos.healthysv2.identity.api.PersonLookup;
import org.novasos.healthysv2.patient.api.PatientDtos.*;
import org.novasos.healthysv2.patient.api.PatientMedicalRecordResponse;
import org.novasos.healthysv2.patient.api.PatientMedicalRecordResponse.*;
import org.novasos.healthysv2.shared.api.error.ResourceNotFoundException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
class PatientMedicalRecordService {
  private org.novasos.healthysv2.patient.PatientSelfAccess selves;

  @org.springframework.beans.factory.annotation.Autowired
  public void configureSelfAccess(org.novasos.healthysv2.patient.PatientSelfAccess selves) {
    this.selves = java.util.Objects.requireNonNull(selves);
  }

  private final PersonLookup identities;
  private final PatientRepository patients;
  private final JdbcTemplate jdbc;
  private final PatientAuditService audit;

  PatientMedicalRecordService(
      PersonLookup identities,
      PatientRepository patients,
      JdbcTemplate jdbc,
      PatientAuditService audit) {
    this.identities = identities;
    this.patients = patients;
    this.jdbc = jdbc;
    this.audit = audit;
  }

  @Transactional(readOnly = true)
  PatientMedicalRecordResponse findMe(UUID subject) {
    if (selves == null)
      throw new org.springframework.security.access.AccessDeniedException(
          "SELECTED_PATIENT_PROFILE_REQUIRED");
    var self = selves.resolve(subject);
    var patient =
        patients
            .findById(self.patient())
            .orElseThrow(() -> new ResourceNotFoundException("Selected patient", self.patient()));
    var person = identities.findPerson(patient.getPersonId());
    var response =
        new PatientMedicalRecordResponse(
            new PatientSummary(
                patient.getId(),
                patient.getPersonId(),
                patient.getPatientNumber(),
                patient.getBloodGroup(),
                patient.getRhesus(),
                patient.getStatus()),
            patient.getAllergies().stream().map(this::allergy).toList(),
            patient.getChronicDiseases().stream().map(this::chronicDisease).toList(),
            patient.getMedicalHistories().stream()
                .map(
                    history ->
                        new MedicalRecordHistory(
                            history.getId(),
                            history.getCondition(),
                            history.getDiagnosedAt(),
                            history.getResolvedAt()))
                .toList(),
            patient.getSurgicalHistories().stream()
                .map(
                    surgery ->
                        new MedicalRecordSurgery(
                            surgery.getId(),
                            surgery.getProcedureName(),
                            surgery.getProcedureDate(),
                            surgery.getOrganizationId()))
                .toList(),
            patient.getFamilyHistories().stream()
                .map(
                    history ->
                        new MedicalRecordFamilyHistory(
                            history.getId(), history.getRelationship(), history.getCondition()))
                .toList(),
            patient.getDisabilities().stream()
                .map(
                    disability ->
                        new MedicalRecordDisability(
                            disability.getId(),
                            disability.getType(),
                            disability.getDescription(),
                            disability.getStartDate(),
                            disability.getStatus()))
                .toList(),
            patient.getFlags().stream()
                .filter(PatientFlag::isActive)
                .map(
                    flag ->
                        new FlagResponse(
                            flag.getId(),
                            flag.getType(),
                            flag.getLabel(),
                            flag.getSeverity(),
                            true,
                            flag.getCreatedAt()))
                .toList(),
            emergencyProfile(patient.getEmergencyProfile()),
            person.emergencyContacts());
    audit.access(
        self.person(),
        patient.getId(),
        null,
        "PATIENT_MEDICAL_RECORD",
        patient.getId(),
        "READ",
        "PATIENT_SELF",
        Map.of("allowed", true));
    return response;
  }

  private AllergyResponse allergy(Allergy allergy) {
    return new AllergyResponse(
        allergy.getId(),
        allergy.getAllergen(),
        allergy.getType(),
        allergy.getReaction(),
        allergy.getSeverity(),
        allergy.getStatus(),
        allergy.getRecordedAt(),
        allergy.getRecordedBy());
  }

  private MedicalRecordChronicDisease chronicDisease(ChronicDisease disease) {
    DiagnosisLabel label =
        disease.getDiagnosisCatalogId() == null
            ? null
            : jdbc.query(
                "select code, label from catalog.diagnosis_catalog where id=?",
                rs ->
                    rs.next()
                        ? new DiagnosisLabel(rs.getString("code"), rs.getString("label"))
                        : null,
                disease.getDiagnosisCatalogId());
    return new MedicalRecordChronicDisease(
        disease.getId(),
        disease.getDiagnosisCatalogId(),
        label == null ? null : label.code(),
        label == null ? null : label.label(),
        disease.getDiagnosedAt(),
        disease.getStatus());
  }

  private EmergencyProfileResponse emergencyProfile(EmergencyProfile profile) {
    if (profile == null) return null;
    return new EmergencyProfileResponse(
        profile.getId(),
        profile.getEmergencyCode(),
        profile.isBloodGroupVisible(),
        profile.isAllergiesVisible(),
        profile.isConditionsVisible(),
        profile.isMedicationsVisible(),
        profile.isEmergencyContactVisible(),
        profile.isActive());
  }

  private record DiagnosisLabel(String code, String label) {}
}
