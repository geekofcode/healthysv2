package org.novasos.healthysv2.organizationaccess.api;

import jakarta.validation.Valid;
import jakarta.validation.constraints.*;
import java.util.*;

public final class OrganizationAccessDtos {
  private OrganizationAccessDtos() {}

  public record PermissionGrant(
      @NotBlank String resource,
      @NotNull Set<@Pattern(regexp = "CREATE|READ|UPDATE|DELETE") String> actions) {}

  public record Resource(String resource, String label, Set<String> actions) {}

  public record RoleRequest(
      @NotBlank @Size(max = 100) String name,
      @Size(max = 500) String description,
      @NotNull @Size(max = 24) List<@Valid PermissionGrant> permissions,
      Long version) {}

  public record RoleResponse(
      UUID id,
      UUID organizationId,
      String name,
      String description,
      long version,
      List<PermissionGrant> permissions) {}

  public record RoleAssignment(@NotNull @Size(max = 50) Set<@NotNull UUID> roleIds) {}

  public record EffectivePermissions(
      UUID organizationId, boolean administrator, List<PermissionGrant> permissions) {}
}
