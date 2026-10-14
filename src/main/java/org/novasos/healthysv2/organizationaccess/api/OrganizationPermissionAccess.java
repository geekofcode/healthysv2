package org.novasos.healthysv2.organizationaccess.api;

import java.util.UUID;
import org.novasos.healthysv2.organizationaccess.api.OrganizationAccessDtos.EffectivePermissions;

public interface OrganizationPermissionAccess {
  void require(UUID organizationId, String resource, String action);

  void requireForPerson(UUID organizationId, UUID personId, String resource, String action);

  void requireForProfile(
      UUID organizationId, UUID personId, UUID profileId, String resource, String action);

  EffectivePermissions effective(UUID organizationId);
}
