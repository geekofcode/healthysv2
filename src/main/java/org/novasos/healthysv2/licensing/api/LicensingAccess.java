package org.novasos.healthysv2.licensing.api;

import java.util.UUID;

public interface LicensingAccess {
  void requireActive(UUID organizationId);

  void requireAi(UUID organizationId);

  void requireAdministrator(UUID organizationId);

  void requireMember(UUID organizationId);

  void reserveProfessionalAssignment(UUID organizationId, UUID personId, String role);

  void releaseProfessionalAssignment(UUID organizationId, UUID personId);
}
