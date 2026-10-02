package org.novasos.healthysv2.identity.api;

import java.util.UUID;

public interface IdentityProvisioningService {
    ProvisionedPerson provisionPatientIdentity(
            UUID keycloakUserId,
            String firstName,
            String lastName,
            String email,
            boolean emailVerified);

    record ProvisionedPerson(UUID id, boolean created) {}
}
