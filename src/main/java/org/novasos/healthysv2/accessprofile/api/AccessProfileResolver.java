package org.novasos.healthysv2.accessprofile.api;

import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.Set;
import java.util.UUID;

/** A selected profile represents exactly one persona; it never unions membership roles. */
public interface AccessProfileResolver {
  List<AccessProfile> profiles(UUID subject, Set<String> originalRoles);

  AccessProfile resolve(
      UUID subject, Set<String> originalRoles, UUID selectedProfileId, UUID selectedOrganizationId);

  /**
   * Background validation excludes global platform claims and requires an active persisted persona.
   */
  AccessProfile forPerson(UUID personId, UUID profileId);

  static UUID administratorId(UUID organizationId, UUID personId) {
    return UUID.nameUUIDFromBytes(
        ("healthys:organization-administrator:" + organizationId + ":" + personId)
            .getBytes(StandardCharsets.UTF_8));
  }

  static UUID platformId(UUID personId) {
    return UUID.nameUUIDFromBytes(
        ("healthys:platform:" + personId).getBytes(StandardCharsets.UTF_8));
  }

  record AccessProfile(
      UUID id,
      UUID personId,
      String type,
      String sourceType,
      UUID sourceId,
      UUID organizationId,
      String organizationName,
      String label,
      boolean active) {}
}
