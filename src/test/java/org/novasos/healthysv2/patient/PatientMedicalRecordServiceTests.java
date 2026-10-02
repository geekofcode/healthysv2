package org.novasos.healthysv2.patient;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.Mockito.*;
import static org.mockito.ArgumentMatchers.*;

import java.sql.ResultSet;
import java.time.LocalDate;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.novasos.healthysv2.identity.api.PersonLookup;
import org.novasos.healthysv2.identity.api.PersonResponse;
import org.novasos.healthysv2.identity.api.EmergencyContactResponse;
import org.novasos.healthysv2.patient.api.PatientMedicalRecordResponse;
import org.novasos.healthysv2.shared.api.error.ResourceNotFoundException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.ResultSetExtractor;

class PatientMedicalRecordServiceTests {
    private final PersonLookup identities = mock(PersonLookup.class);
    private final PatientRepository patients = mock(PatientRepository.class);
    private final JdbcTemplate jdbc = mock(JdbcTemplate.class);
    private final PatientAuditService audit = mock(PatientAuditService.class);
    private final PatientMedicalRecordService service = new PatientMedicalRecordService(identities, patients, jdbc, audit);
    private final UUID subject = UUID.randomUUID();
    private final UUID personId = UUID.randomUUID();

    @Test
    void mapsOwnMedicalFactsAndEmergencyInformationWithoutPrivateNotes() throws Exception {
        Patient patient = linkPatient();
        UUID diagnosis = UUID.randomUUID();
        patient.addAllergy("Penicillin", "DRUG", "Rash", "HIGH", "ACTIVE", null, null);
        patient.addAllergy("Historical", "FOOD", "Mild rash", "LOW", "INACTIVE", null, null);
        patient.addChronicDisease(diagnosis, LocalDate.of(2020, 1, 1), "ACTIVE", "PRIVATE_CHRONIC_NOTE");
        patient.addMedicalHistory("Asthma", LocalDate.of(2010, 1, 1), null, "PRIVATE_HISTORY_NOTE");
        patient.addSurgicalHistory("Appendectomy", LocalDate.of(2011, 1, 1), null, "PRIVATE_SURGERY_NOTE");
        patient.addFamilyHistory("Father", "Diabetes", "PRIVATE_FAMILY_NOTE");
        patient.addDisability("MOBILITY", "Walking aid", LocalDate.of(2021, 1, 1), "ACTIVE");
        patient.addFlag("MEDICAL", "High allergy risk", "HIGH", true, null);
        patient.addFlag("MEDICAL", "Inactive flag", "LOW", false, null);
        patient.addNote(null, "PRIVATE", "PRIVATE_PATIENT_NOTE", null);
        patient.setEmergencyProfile("EMERGENCY-1", true, true, true, false, true, true);
        when(jdbc.query(eq("select code, label from catalog.diagnosis_catalog where id=?"),
                org.mockito.ArgumentMatchers.<ResultSetExtractor<Object>>any(), eq(diagnosis)))
                .thenAnswer(invocation -> {
                    ResultSet resultSet = mock(ResultSet.class);
                    when(resultSet.next()).thenReturn(true);
                    when(resultSet.getString("code")).thenReturn("E11");
                    when(resultSet.getString("label")).thenReturn("Type 2 diabetes");
                    ResultSetExtractor<?> mapper = invocation.getArgument(1);
                    return mapper.extractData(resultSet);
                });

        var response = service.findMe(subject);

        assertThat(response.patient().personId()).isEqualTo(personId);
        assertThat(response.allergies()).extracting(allergy -> allergy.status()).containsExactly("ACTIVE", "INACTIVE");
        assertThat(response.chronicDiseases().getFirst().diagnosisCode()).isEqualTo("E11");
        assertThat(response.chronicDiseases().getFirst().diagnosisLabel()).isEqualTo("Type 2 diabetes");
        assertThat(response.medicalHistories().getFirst().condition()).isEqualTo("Asthma");
        assertThat(response.surgicalHistories().getFirst().procedureName()).isEqualTo("Appendectomy");
        assertThat(response.familyHistories().getFirst().relationship()).isEqualTo("Father");
        assertThat(response.disabilities().getFirst().description()).isEqualTo("Walking aid");
        assertThat(response.flags()).extracting(flag -> flag.label()).containsExactly("High allergy risk");
        assertThat(response.emergencyProfile().emergencyCode()).isEqualTo("EMERGENCY-1");
        assertThat(response.emergencyProfile().medicationsVisible()).isFalse();
        assertThat(response.emergencyContacts()).extracting(contact -> contact.phone()).containsExactly("555-0100");
        assertThat(response.toString()).doesNotContain("PRIVATE_");
        for (Class<?> dto : List.of(PatientMedicalRecordResponse.class,
                PatientMedicalRecordResponse.MedicalRecordChronicDisease.class,
                PatientMedicalRecordResponse.MedicalRecordHistory.class,
                PatientMedicalRecordResponse.MedicalRecordSurgery.class,
                PatientMedicalRecordResponse.MedicalRecordFamilyHistory.class)) {
            assertThat(dto.getRecordComponents()).extracting(component -> component.getName()).doesNotContain("notes");
        }
        verify(patients).findByPersonId(personId);
        verify(patients, never()).findById(any());
        verify(patients, never()).save(any());
        verify(audit).access(eq(personId), eq(patient.getId()), isNull(), eq("PATIENT_MEDICAL_RECORD"),
                eq(patient.getId()), eq("READ"), eq("PATIENT_SELF"), any());
    }

    @Test
    void absentCatalogAndEmergencyProfileRemainNullInsteadOfInventingFacts() {
        Patient patient = linkPatient();
        patient.addChronicDisease(null, null, "ACTIVE", "Private note");
        var response = service.findMe(subject);
        assertThat(response.chronicDiseases().getFirst().diagnosisLabel()).isNull();
        assertThat(response.emergencyProfile()).isNull();
        verifyNoInteractions(jdbc);
    }

    @Test
    void absentIdentityCannotProvisionOrQueryAnotherPatient() {
        when(identities.findMe(subject)).thenThrow(new ResourceNotFoundException("Person", subject));
        assertThatThrownBy(() -> service.findMe(subject)).isInstanceOf(ResourceNotFoundException.class);
        verifyNoInteractions(patients, jdbc, audit);
    }

    @Test
    void absentPatientReturnsNotFoundWithoutProvisioning() {
        PersonResponse person = mock(PersonResponse.class);
        when(person.id()).thenReturn(personId);
        when(identities.findMe(subject)).thenReturn(person);
        when(patients.findByPersonId(personId)).thenReturn(Optional.empty());
        assertThatThrownBy(() -> service.findMe(subject)).isInstanceOf(ResourceNotFoundException.class);
        verify(patients, never()).save(any());
        verifyNoInteractions(jdbc, audit);
    }

    private Patient linkPatient() {
        PersonResponse person = mock(PersonResponse.class);
        when(person.id()).thenReturn(personId);
        when(person.emergencyContacts()).thenReturn(List.of(new EmergencyContactResponse(
                UUID.randomUUID(), "Jane", "Doe", "SPOUSE", "555-0100", null)));
        when(identities.findMe(subject)).thenReturn(person);
        Patient patient = Patient.create(personId);
        when(patients.findByPersonId(personId)).thenReturn(Optional.of(patient));
        return patient;
    }
}
