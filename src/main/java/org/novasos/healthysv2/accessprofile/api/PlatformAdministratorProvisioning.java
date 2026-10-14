package org.novasos.healthysv2.accessprofile.api;

import java.util.UUID;

/** Controlled local bootstrap only; never derives platform access from Keycloak role names. */
public interface PlatformAdministratorProvisioning {
  void bootstrap(UUID subject);
}
