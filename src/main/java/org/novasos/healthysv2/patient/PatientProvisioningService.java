package org.novasos.healthysv2.patient;

import java.util.UUID;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.novasos.healthysv2.identity.api.IdentityProvisioningService;
import org.novasos.healthysv2.patient.api.PatientProvisionResponse;
import org.novasos.healthysv2.shared.api.error.BusinessRuleException;

@Service
@Transactional
class PatientProvisioningService {
    private final PatientRepository patients;
    private final IdentityProvisioningService identities;

    PatientProvisioningService(
            PatientRepository patients,
            IdentityProvisioningService identities) {
        this.patients = patients;
        this.identities = identities;
    }

    PatientProvisionResponse provision(
            UUID subject,
            String firstName,
            String lastName,
            String email,
            boolean emailVerified) {
        return provision(subject, firstName, lastName, email, emailVerified,
                IdentityProvisioningService.RegistrationProfile.empty());
    }

    PatientProvisionResponse provision(UUID subject, String firstName, String lastName,
            String email, boolean emailVerified,
            IdentityProvisioningService.RegistrationProfile profile) {
        requireName(firstName, "FIRST_NAME_REQUIRED");
        requireName(lastName, "LAST_NAME_REQUIRED");
        var identity = identities.provisionPatientIdentity(
                subject, firstName, lastName, email, emailVerified, profile);
        return patients.findByPersonId(identity.id())
                .map(patient -> response(patient, false))
                .orElseGet(() -> response(
                        patients.saveAndFlush(Patient.create(identity.id())),
                        true));
    }

    private void requireName(String value, String code) {
        if (value == null || value.isBlank()) {
            throw new BusinessRuleException(code, "error.registration.name-required");
        }
    }

    private PatientProvisionResponse response(Patient patient, boolean created) {
        return new PatientProvisionResponse(
                patient.getId(), patient.getPersonId(),
                patient.getPatientNumber(), patient.getStatus(), created);
    }
}
