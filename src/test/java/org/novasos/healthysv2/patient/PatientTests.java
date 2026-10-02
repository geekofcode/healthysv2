package org.novasos.healthysv2.patient;

import static org.assertj.core.api.Assertions.assertThat;
import java.util.UUID;
import java.time.LocalDate;
import org.junit.jupiter.api.Test;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class PatientTests {
    @Test
    void createsAStablePatientIdentity() {
        UUID personId = UUID.fromString("12345678-1234-1234-1234-123456789012");
        Patient patient = Patient.create(personId);
        assertThat(patient.getPersonId()).isEqualTo(personId);
        assertThat(patient.getPatientNumber()).isEqualTo("PAT-12345678123412341234");
        assertThat(patient.getStatus()).isEqualTo("ACTIVE");
    }

    @Test
    void managesClinicalProfileAndKeepsOnlyOnePrimaryInsurance() {
        Patient patient = Patient.create(UUID.randomUUID(), "o+", "positive", "single", "Engineer", null);
        patient.addInsurance(UUID.randomUUID(), "P1", null, null, null, true);
        patient.addInsurance(UUID.randomUUID(), "P2", null, null, null, true);
        patient.addAllergy("Penicillin", "DRUG", "Rash", "HIGH", null, null, null);
        patient.addMedicalHistory("Asthma", LocalDate.of(2020, 1, 1), null, "Controlled");
        patient.addFlag("CLINICAL", "Fall risk", "HIGH", true, null);
        assertThat(patient.getBloodGroup()).isEqualTo("O+");
        assertThat(patient.getInsurances()).extracting(PatientInsurance::isPrimary).containsExactly(false, true);
        assertThat(patient.getAllergies()).hasSize(1);
        assertThat(patient.getMedicalHistories()).hasSize(1);
        assertThat(patient.getFlags()).hasSize(1);
    }

    @Test
    void rejectsInconsistentDates() {
        Patient patient = Patient.create(UUID.randomUUID());
        assertThatThrownBy(() -> patient.addMedicalHistory("Condition", LocalDate.of(2025, 1, 2), LocalDate.of(2025, 1, 1), null))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> patient.addInsurance(UUID.randomUUID(), null, null, LocalDate.of(2025, 1, 2), LocalDate.of(2025, 1, 1), false))
                .isInstanceOf(IllegalArgumentException.class);
    }
}
