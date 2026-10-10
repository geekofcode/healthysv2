package org.novasos.healthysv2.professional.api;

import java.util.UUID;

/** Persisted in the caller transaction; Keycloak delivery is asynchronous. */
public interface ProfessionalRoleProvisioning {
    void activate(UUID keycloakUserId, String realmRole);
    void deactivate(UUID keycloakUserId, String realmRole);
}
