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

    record ProvisionedPerson(UUID id, boolean created) {}
}
