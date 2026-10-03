package org.novasos.healthysv2.identity.api;

import java.util.UUID;

/** Read-only identity lookup through the Keycloak linkage; never falls back to person ID. */
public interface PersonLookup {
    PersonResponse findMe(UUID keycloakUserId);
}
