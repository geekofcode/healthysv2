package org.novasos.healthysv2.professional.api;

import java.util.UUID;

/**
 * Compatibility hook for local professional approval; no authorization roles are written to
 * Keycloak.
 */
public interface ProfessionalRoleProvisioning {
  void activate(UUID keycloakUserId, String realmRole);

  void deactivate(UUID keycloakUserId, String realmRole);
}
