package org.novasos.healthysv2.patient.api;

import java.util.UUID;

public record PatientProvisionResponse(
        UUID patientId,
        UUID personId,
        String patientNumber,
        String status,
        boolean created) {
}
