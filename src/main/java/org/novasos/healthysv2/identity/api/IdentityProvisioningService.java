package org.novasos.healthysv2.identity.api;

import java.util.UUID;

public interface IdentityProvisioningService {
    ProvisionedPerson provisionIdentity(
            UUID keycloakUserId,
            String firstName,
            String lastName,
            String email,
            boolean emailVerified);

    default ProvisionedPerson provisionPatientIdentity(
            UUID keycloakUserId,
            String firstName,
            String lastName,
            String email,
            boolean emailVerified) {
        return provisionIdentity(keycloakUserId, firstName, lastName, email, emailVerified);
    }

    default ProvisionedPerson provisionIdentity(UUID subject, String firstName,
            String lastName, String email, boolean emailVerified, RegistrationProfile profile) {
        return provisionIdentity(subject, firstName, lastName, email, emailVerified);
    }

    default ProvisionedPerson provisionPatientIdentity(UUID subject, String firstName,
            String lastName, String email, boolean emailVerified, RegistrationProfile profile) {
        return provisionIdentity(subject, firstName, lastName, email, emailVerified, profile);
    }

    record RegistrationProfile(String middleName, String birthdate, String gender,
            String phoneNumber, String locale, RegistrationAddress address) {
        public RegistrationProfile(String middleName, String birthdate, String gender, String phoneNumber) {
            this(middleName, birthdate, gender, phoneNumber, null, null);
        }
        public static RegistrationProfile empty() {
            return new RegistrationProfile(null, null, null, null);
        }
    }

    record RegistrationAddress(String line1, String line2, String city, String province,
            String postalCode, String country) {}

    record ProvisionedPerson(UUID id, boolean created) {}
}
