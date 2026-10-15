package org.novasos.healthysv2.care;

import jakarta.validation.Valid;
import jakarta.validation.constraints.*;
import java.util.*;
import org.novasos.healthysv2.licensing.api.LicensingAccess;
import org.novasos.healthysv2.organizationaccess.api.OrganizationPermissionAccess;
import org.novasos.healthysv2.patient.CurrentUserContext;
import org.novasos.healthysv2.shared.api.error.*;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/v1/care/speciality-policies")
class SpecialityPolicyController {
  private final JdbcTemplate jdbc;
  private final CurrentUserContext users;
  private final LicensingAccess licensing;
  private final OrganizationPermissionAccess permissions;

  SpecialityPolicyController(
      JdbcTemplate jdbc,
      CurrentUserContext users,
      LicensingAccess licensing,
      OrganizationPermissionAccess permissions) {
    this.jdbc = jdbc;
    this.users = users;
    this.licensing = licensing;
    this.permissions = permissions;
  }

  record Policy(UUID specialityId, UUID tariffId, long followUpSeconds, long version) {}

  record Write(
      @NotNull UUID tariffId, @Positive long followUpSeconds, @PositiveOrZero long version) {}

  private UUID org(String action) {
    var u = users.current();
    if (u.organizationId() == null || u.has("PLATFORM_ADMIN"))
      throw new org.springframework.security.access.AccessDeniedException(
          "HOSPITAL_PROFILE_REQUIRED");
    licensing.requireMember(u.organizationId());
    permissions.require(u.organizationId(), "tariffs", action);
    if (!action.equals("READ")) {
      licensing.requireActive(u.organizationId());
      if (!u.hasAny("HOSPITAL_ADMIN", "ORG_ADMIN"))
        throw new org.springframework.security.access.AccessDeniedException(
            "HOSPITAL_ADMIN_REQUIRED");
    }
    return u.organizationId();
  }

  @GetMapping
  @Transactional(readOnly = true)
  List<Policy> list() {
    var org = org("READ");
    return jdbc.query(
        "select * from care.speciality_policy where organization_id=? order by speciality_id",
        (r, n) ->
            new Policy(
                r.getObject("speciality_id", UUID.class),
                r.getObject("tariff_id", UUID.class),
                r.getLong("follow_up_seconds"),
                r.getLong("version")),
        org);
  }

  @PutMapping("/{speciality}")
  @Transactional
  Policy save(@PathVariable UUID speciality, @Valid @RequestBody Write request) {
    var org = org("UPDATE");
    jdbc.queryForObject(
        "select id from organization.organization where id=? for update", UUID.class, org);
    Integer valid =
        jdbc.queryForObject(
            "select count(*) from billing.care_tariff t where t.id=? and t.organization_id=? and"
                + " t.active and exists(select 1 from catalog.speciality_catalog s where s.id=? and"
                + " s.active)",
            Integer.class,
            request.tariffId(),
            org,
            speciality);
    if (valid == null || valid == 0)
      throw new BusinessRuleException(
          "INVALID_SPECIALITY_POLICY", "Active establishment tariff and specialty required");
    var versions =
        jdbc.queryForList(
            "select version from care.speciality_policy where organization_id=? and"
                + " speciality_id=?",
            Long.class,
            org,
            speciality);
    if (!versions.isEmpty() && versions.getFirst() != request.version())
      throw new ConflictException("POLICY_VERSION_CONFLICT", "Reload policy");
    jdbc.update(
        "insert into"
            + " care.speciality_policy(organization_id,speciality_id,tariff_id,follow_up_seconds)"
            + " values(?,?,?,?) on conflict(organization_id,speciality_id) do update set"
            + " tariff_id=excluded.tariff_id,follow_up_seconds=excluded.follow_up_seconds,version=care.speciality_policy.version+1",
        org,
        speciality,
        request.tariffId(),
        request.followUpSeconds());
    return list().stream()
        .filter(p -> p.specialityId().equals(speciality))
        .findFirst()
        .orElseThrow();
  }
}
