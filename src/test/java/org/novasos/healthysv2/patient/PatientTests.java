package org.novasos.healthysv2.patient;

import static org.assertj.core.api.Assertions.assertThat;
import java.util.UUID;
import org.junit.jupiter.api.Test;

class PatientTests {
    @Test
    void createsAStablePatientIdentity() {
        UUID personId = UUID.fromString("12345678-1234-1234-1234-123456789012");
        Patient patient = Patient.create(personId);
        assertThat(patient.getPersonId()).isEqualTo(personId);
        assertThat(patient.getPatientNumber()).isEqualTo("PAT-12345678123412341234");
        assertThat(patient.getStatus()).isEqualTo("ACTIVE");
    }
}
