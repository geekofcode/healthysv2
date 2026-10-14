package org.novasos.healthysv2.organizationaccess;

import jakarta.validation.Valid;
import java.util.*;
import org.novasos.healthysv2.organizationaccess.api.OrganizationAccessDtos.*;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/v1/organizations/{organizationId}/access")
public class OrganizationAccessController {
  private final OrganizationPermissionService service;

  public OrganizationAccessController(OrganizationPermissionService service) {
    this.service = service;
  }

  @GetMapping("/permissions")
  public List<Resource> catalog(@PathVariable UUID organizationId) {
    service.requireAdministrator(organizationId);
    return service.catalog();
  }

  @GetMapping("/me")
  public EffectivePermissions me(@PathVariable UUID organizationId) {
    return service.effective(organizationId);
  }

  @GetMapping("/roles")
  public List<RoleResponse> roles(@PathVariable UUID organizationId) {
    return service.roles(organizationId);
  }

  @PostMapping("/roles")
  public RoleResponse create(
      @PathVariable UUID organizationId, @Valid @RequestBody RoleRequest body) {
    return service.create(organizationId, body);
  }

  @PutMapping("/roles/{roleId}")
  public RoleResponse update(
      @PathVariable UUID organizationId,
      @PathVariable UUID roleId,
      @Valid @RequestBody RoleRequest body) {
    return service.update(organizationId, roleId, body);
  }

  @DeleteMapping("/roles/{roleId}")
  public void delete(@PathVariable UUID organizationId, @PathVariable UUID roleId) {
    service.delete(organizationId, roleId);
  }

  @GetMapping("/members/{memberId}/roles")
  public Set<UUID> assigned(@PathVariable UUID organizationId, @PathVariable UUID memberId) {
    return service.assigned(organizationId, memberId);
  }

  @PutMapping("/members/{memberId}/roles")
  public Set<UUID> assign(
      @PathVariable UUID organizationId,
      @PathVariable UUID memberId,
      @Valid @RequestBody RoleAssignment body) {
    return service.assign(organizationId, memberId, body.roleIds());
  }
}
