package org.novasos.healthysv2.licensing;

import java.util.*;
import org.novasos.healthysv2.licensing.api.*;
import org.novasos.healthysv2.licensing.api.LicensingDtos.*;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationToken;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.context.request.RequestContextHolder;
import org.springframework.web.context.request.ServletRequestAttributes;

@Service
public class LicensingService implements LicensingAccess {
  private final JdbcTemplate db;

  public LicensingService(JdbcTemplate db) {
    this.db = db;
  }

  boolean platform() {
    var a = SecurityContextHolder.getContext().getAuthentication();
    return a != null
        && a.getAuthorities().stream()
            .anyMatch(x -> x.getAuthority().equals("ROLE_PLATFORM_ADMIN"));
  }

  UUID person() {
    var a = SecurityContextHolder.getContext().getAuthentication();
    if (!(a instanceof JwtAuthenticationToken j)) throw denied("IDENTITY_REQUIRED");
    try {
      return db.query(
          "select id from identity.person where keycloak_user_id=? and status='ACTIVE'",
          r -> r.next() ? (UUID) r.getObject(1) : null,
          UUID.fromString(j.getToken().getSubject()));
    } catch (IllegalArgumentException e) {
      throw denied("IDENTITY_REQUIRED");
    }
  }

  private void audit(UUID org, String action, UUID target) {
    db.update(
        "insert into licensing.audit_entry(organization_id,actor_person_id,action,target_id)"
            + " values(?,?,?,?)",
        org,
        person(),
        action,
        target);
  }

  private AccessDeniedException denied(String code) {
    return new AccessDeniedException(code);
  }

  private org.novasos.healthysv2.accessprofile.api.AccessProfileResolver profiles;
  private String apiClientId;

  @org.springframework.beans.factory.annotation.Autowired
  void profiles(
      org.novasos.healthysv2.accessprofile.api.AccessProfileResolver profiles,
      @org.springframework.beans.factory.annotation.Value(
              "${healthys.security.api-client-id:healthys-backend}")
          String apiClientId) {
    this.profiles = profiles;
    this.apiClientId = apiClientId;
  }

  private org.novasos.healthysv2.accessprofile.api.AccessProfileResolver.AccessProfile
      activeProfile() {
    var auth = SecurityContextHolder.getContext().getAuthentication();
    if (!(auth instanceof JwtAuthenticationToken jwt)) throw denied("IDENTITY_REQUIRED");
    UUID profileId = null, org = null;
    if (RequestContextHolder.getRequestAttributes()
        instanceof ServletRequestAttributes attributes) {
      try {
        var request = attributes.getRequest();
        if (request.getHeader("X-Profile-ID") != null)
          profileId = UUID.fromString(request.getHeader("X-Profile-ID"));
        if (request.getHeader("X-Organization-ID") != null)
          org = UUID.fromString(request.getHeader("X-Organization-ID"));
      } catch (IllegalArgumentException e) {
        throw denied("INVALID_PROFILE_CONTEXT");
      }
    }
    return profiles.resolve(UUID.fromString(jwt.getToken().getSubject()), Set.of(), profileId, org);
  }

  private void profileOrganization(UUID org, boolean administrator) {
    if (profiles == null)
      return; // Direct constructor fixtures; Spring requires the resolver setter.
    var profile = activeProfile();
    if (profile == null
        || !org.equals(profile.organizationId())
        || (administrator && !profile.type().equals("HOSPITAL_ADMIN")))
      throw denied("SELECTED_ORGANIZATION_PROFILE_REQUIRED");
  }

  public void requireAdministrator(UUID org) {
    if (platform()) throw denied("PLATFORM_OPERATIONS_FORBIDDEN");
    profileOrganization(org, true);
    var authentication = SecurityContextHolder.getContext().getAuthentication();
    if (authentication == null
        || authentication.getAuthorities().stream()
            .noneMatch(a -> a.getAuthority().equals("ROLE_HOSPITAL_ADMIN")))
      throw denied("SELECTED_ADMINISTRATOR_PROFILE_REQUIRED");
    UUID p = person();
    if (isLocalAdministrator(org, p)) return;
    Integer n =
        db.queryForObject(
            "select count(*) from licensing.member where organization_id=? and person_id=? and"
                + " role='HOSPITAL_ADMIN' and status='ACTIVE'",
            Integer.class,
            org,
            p);
    if (n == null || n == 0) throw denied("ORGANIZATION_ADMIN_REQUIRED");
  }

  public void requireSubscriptionAdministrator(UUID org) {
    if (!platform()) requireAdministrator(org);
  }

  public void requireMember(UUID org) {
    if (platform()) throw denied("PLATFORM_OPERATIONS_FORBIDDEN");
    profileOrganization(org, false);
    UUID p = person();
    if (isLocalAdministrator(org, p)) return;
    Integer n =
        db.queryForObject(
            "select count(*) from licensing.member where organization_id=? and person_id=? and"
                + " status='ACTIVE'",
            Integer.class,
            org,
            p);
    if (n == null || n == 0) throw denied("ORGANIZATION_MEMBERSHIP_REQUIRED");
  }

  public void requireActive(UUID org) {
    Integer n =
        db.queryForObject(
            "select count(*) from licensing.subscription s join organization.organization o on"
                + " o.id=s.organization_id where s.organization_id=? and s.status='active' and"
                + " s.period_start<=now() and s.period_end>now() and o.status='ACTIVE'",
            Integer.class,
            org);
    if (n == null || n == 0) throw denied("ACTIVE_SUBSCRIPTION_REQUIRED");
  }

  public void requireAi(UUID org) {
    requireMember(org);
    requireActive(org);
    Boolean enabled =
        db.queryForObject(
            "select ai_assistant_enabled from licensing.subscription where organization_id=?",
            Boolean.class,
            org);
    if (!Boolean.TRUE.equals(enabled)) throw denied("AI_NOT_INCLUDED");
  }

  private boolean isLocalAdministrator(UUID org, UUID p) {
    return p != null
        && db.queryForObject(
                "select count(*) from organization.organization where id=? and"
                    + " administrator_person_id=?",
                Integer.class,
                org,
                p)
            > 0;
  }

  private void lock(UUID org) {
    if (db.query(
            "select id from organization.organization where id=? for update",
            (r, i) -> r.getObject(1),
            org)
        .isEmpty()) throw new IllegalArgumentException("ORGANIZATION_NOT_FOUND");
  }

  List<Map<String, Object>> plans() {
    return db.queryForList("select * from licensing.plan order by amount_minor,name");
  }

  @Transactional
  Map<String, Object> savePlan(UUID id, PlanRequest request) {
    if (!platform()) throw denied("PLATFORM_ADMIN_REQUIRED");
    if (id == null) id = createPlan(request);
    else {
      validatePlanPrice(id, request);
      updatePlan(id, request);
    }
    audit(null, "PLAN_UPDATED", id);
    return db.queryForMap("select * from licensing.plan where id=?", id);
  }

  private void validatePlanPrice(UUID id, PlanRequest p) {
    if (id != null) {
      var old = db.queryForMap("select * from licensing.plan where id=?", id);
      Integer used =
          db.queryForObject(
              "select count(*) from licensing.subscription where plan_id=? or"
                  + " pending_checkout_plan_id=?",
              Integer.class,
              id,
              id);
      if (used != null
          && used > 0
          && (!p.stripePriceId().equals(old.get("stripe_price_id"))
              || p.amountMinor() != ((Number) old.get("amount_minor")).longValue()
              || !p.currency().equalsIgnoreCase(old.get("currency").toString())
              || !p.interval().equals(old.get("billing_interval"))))
        throw new IllegalArgumentException("SUBSCRIBED_PLAN_PRICE_IMMUTABLE");
    }
  }

  private UUID createPlan(PlanRequest p) {
    UUID id = UUID.randomUUID();
    db.update(
        "insert into"
            + " licensing.plan(id,name,amount_minor,currency,billing_interval,doctor_quota,professional_quota,cashier_quota,accountant_quota,ai_assistant_enabled,stripe_price_id,active)"
            + " values(?,?,?,?,?,?,?,?,?,?,?,?)",
        id,
        p.name(),
        p.amountMinor(),
        p.currency().toLowerCase(Locale.ROOT),
        p.interval(),
        p.doctorQuota(),
        p.professionalQuota(),
        p.cashierQuota(),
        p.accountantQuota(),
        p.aiAssistantEnabled(),
        p.stripePriceId(),
        p.active());
    return id;
  }

  private void updatePlan(UUID id, PlanRequest p) {
    if (db.update(
            "update licensing.plan set"
                + " name=?,amount_minor=?,currency=?,billing_interval=?,doctor_quota=?,professional_quota=?,cashier_quota=?,accountant_quota=?,ai_assistant_enabled=?,stripe_price_id=?,active=?,updated_at=now()"
                + " where id=?",
            p.name(),
            p.amountMinor(),
            p.currency().toLowerCase(Locale.ROOT),
            p.interval(),
            p.doctorQuota(),
            p.professionalQuota(),
            p.cashierQuota(),
            p.accountantQuota(),
            p.aiAssistantEnabled(),
            p.stripePriceId(),
            p.active(),
            id)
        == 0) throw new IllegalArgumentException("PLAN_NOT_FOUND");
  }

  Map<String, Object> entitlements(UUID org) {
    if (!platform()) requireMember(org);
    var rows = db.queryForList("select * from licensing.subscription where organization_id=?", org);
    Map<String, Object> result = new LinkedHashMap<>();
    if (!rows.isEmpty()) result.putAll(rows.getFirst());
    else result.put("status", "INACTIVE");
    result.put("usage", usage(org));
    result.put(
        "active",
        db.queryForObject(
                "select count(*) from licensing.subscription s join organization.organization o on"
                    + " o.id=s.organization_id where s.organization_id=? and s.status='active' and"
                    + " s.period_start<=now() and s.period_end>now() and o.status='ACTIVE'",
                Integer.class,
                org)
            > 0);
    return result;
  }

  Map<String, Long> usage(UUID org) {
    Map<String, Long> m = new HashMap<>();
    for (var r :
        db.queryForList(
            "select role,count(*) as n from licensing.member where organization_id=? and"
                + " (status='ACTIVE' or (status='INVITED' and expires_at>now())) group by role",
            org))
      m.merge(bucket((String) r.get("role")), ((Number) r.get("n")).longValue(), Long::sum);
    return m;
  }

  static String bucket(String role) {
    return switch (role) {
      case "DOCTOR" -> "doctor";
      case "CASHIER" -> "cashier";
      case "ACCOUNTANT" -> "accountant";
      case "NURSE", "HOSPITAL_AGENT" -> "professional";
      case "HOSPITAL_ADMIN" -> "administrator";
      default -> throw new IllegalArgumentException("INVALID_ROLE");
    };
  }

  private void expireInvitations(UUID org) {
    db.update(
        "update licensing.member set status='INACTIVE',updated_at=now() where organization_id=? and"
            + " status='INVITED' and expires_at<=now()",
        org);
  }

  private void capacity(UUID org, String role) {
    String b = bucket(role);
    if (b.equals("administrator")) return;
    int quota =
        db.queryForObject(
            "select " + b + "_quota from licensing.subscription where organization_id=?",
            Integer.class,
            org);
    if (usage(org).getOrDefault(b, 0L) >= quota)
      throw new IllegalArgumentException("QUOTA_EXCEEDED_" + b.toUpperCase(Locale.ROOT));
  }

  private org.novasos.healthysv2.organizationaccess.api.OrganizationPermissionAccess permissions;

  @org.springframework.beans.factory.annotation.Autowired
  void permissions(
      org.novasos.healthysv2.organizationaccess.api.OrganizationPermissionAccess permissions) {
    this.permissions = permissions;
  }

  private void employeePermission(UUID org, String action) {
    if (platform()) throw denied("PLATFORM_OPERATIONS_FORBIDDEN");
    if (permissions == null) requireAdministrator(org);
    else permissions.require(org, "employees", action);
  }

  private org.novasos.healthysv2.identity.api.EmployeeAccountProvisioning accounts;

  @org.springframework.beans.factory.annotation.Autowired
  void accounts(org.novasos.healthysv2.identity.api.EmployeeAccountProvisioning accounts) {
    this.accounts = accounts;
  }

  @Transactional
  Map<String, Object> createEmployee(UUID org, EmployeeRequest request) {
    employeePermission(org, "CREATE");
    if (request.role().equals("HOSPITAL_ADMIN")) requireAdministrator(org);
    lock(org);
    employeePermission(org, "CREATE");
    if (request.role().equals("HOSPITAL_ADMIN")) requireAdministrator(org);
    requireActive(org);
    expireInvitations(org);
    UUID personId = request.personId();
    if (personId == null) {
      if (request.firstName() == null
          || request.firstName().isBlank()
          || request.lastName() == null
          || request.lastName().isBlank()
          || request.email() == null
          || request.email().isBlank())
        throw new IllegalArgumentException("EMPLOYEE_IDENTITY_REQUIRED");
      personId =
          accounts
              .ensureAccount(request.firstName(), request.lastName(), request.email())
              .personId();
    } else if (!Boolean.TRUE.equals(
        db.queryForObject(
            "select exists(select 1 from identity.person p where id=? and status='ACTIVE' and"
                + " (keycloak_user_id is not null or exists(select 1 from"
                + " identity.staff_account_outbox a where a.person_id=p.id)))",
            Boolean.class,
            personId))) {
      throw denied("ACTIVE_ACCOUNT_REQUIRED");
    }
    ensureDistinctProfile(org, personId, request.email(), request.role(), null);
    capacity(org, request.role());
    db.queryForObject(
        "select id from identity.person where id=? and status='ACTIVE' for update",
        UUID.class,
        personId);
    boolean pendingClinical =
        Set.of("DOCTOR", "NURSE").contains(request.role())
            && Boolean.TRUE.equals(
                db.queryForObject(
                    "select exists(select 1 from professional.registration_request where"
                        + " person_id=? and status<>'APPROVED')",
                    Boolean.class,
                    personId));
    if (!pendingClinical && Set.of("DOCTOR", "NURSE").contains(request.role())) {
      var profiles =
          db.queryForList(
              "select id,professional_type,status from professional.professional where person_id=?",
              personId);
      if (profiles.isEmpty()) {
        String number =
            request.professionalNumber() == null || request.professionalNumber().isBlank()
                ? "STAFF-" + UUID.randomUUID()
                : request.professionalNumber().trim();
        db.update(
            "insert into"
                + " professional.professional(id,person_id,professional_number,professional_type,status)"
                + " values(?,?,?,?,'ACTIVE')",
            UUID.randomUUID(),
            personId,
            number,
            request.role());
      }
      synchronizeClinical(org, personId, request.role(), true);
    }
    UUID memberId = UUID.randomUUID();
    db.update(
        "insert into licensing.member(id,organization_id,person_id,email,role,status,expires_at)"
            + " values(?,?,?,?,?,?,case when ?='INVITED' then now()+interval '7 days' else null"
            + " end)",
        memberId,
        org,
        personId,
        request.email(),
        request.role(),
        pendingClinical ? "INVITED" : "ACTIVE",
        pendingClinical ? "INVITED" : "ACTIVE");
    var roleIds = request.customRoleIds() == null ? Set.<UUID>of() : request.customRoleIds();
    if (!roleIds.isEmpty()) requireAdministrator(org);
    initializeMemberPolicy(org, memberId, request.customRoleIds() == null);
    for (UUID roleId : roleIds) {
      if (!Boolean.TRUE.equals(
          db.queryForObject(
              "select exists(select 1 from organizationaccess.role where id=? and"
                  + " organization_id=?)",
              Boolean.class,
              roleId,
              org))) throw new IllegalArgumentException("ROLE_TENANT_MISMATCH");
      db.update(
          "insert into organizationaccess.member_role(member_id,organization_id,role_id)"
              + " values(?,?,?)",
          memberId,
          org,
          roleId);
    }
    audit(org, "EMPLOYEE_CREATED", memberId);
    return db.queryForMap(
        "select m.*,p.id professional_id from licensing.member m left join"
            + " professional.professional p on p.person_id=m.person_id where m.id=?",
        memberId);
  }

  private void initializeMemberPolicy(UUID org, UUID memberId) {
    initializeMemberPolicy(org, memberId, true);
  }

  private void initializeMemberPolicy(UUID org, UUID memberId, boolean defaultClinical) {
    int inserted =
        db.update(
            "insert into organizationaccess.member_policy(member_id,organization_id) values(?,?) on"
                + " conflict(member_id) do nothing",
            memberId,
            org);
    if (inserted == 1 && defaultClinical)
      db.update(
          "insert into organizationaccess.member_role(member_id,organization_id,role_id) select"
              + " m.id,m.organization_id,r.id from licensing.member m join organizationaccess.role"
              + " r on r.organization_id=m.organization_id and r.system_key=m.role and r.reserved"
              + " where m.id=? and m.organization_id=? on conflict do nothing",
          memberId,
          org);
  }

  private void ensureDistinctProfile(
      UUID org, UUID personId, String email, String role, UUID excludedId) {
    String normalized =
        email == null || email.isBlank() ? null : email.trim().toLowerCase(Locale.ROOT);
    List<Object> arguments = new ArrayList<>(Arrays.asList(org, role, personId, normalized));
    String sql =
        "select id from licensing.member where organization_id=? and role=? and status<>'INACTIVE'"
            + " and (person_id=? or lower(trim(email))=?)";
    if (excludedId != null) {
      sql += " and id<>?";
      arguments.add(excludedId);
    }
    var existing = db.queryForList(sql + " limit 1", arguments.toArray());
    if (!existing.isEmpty())
      throw new org.novasos.healthysv2.shared.api.error.ConflictException(
          "EMPLOYEE_ALREADY_ATTACHED",
          "This account already has this profile in this organization");
  }

  Object lookupEmployeeAccount(UUID org, String email) {
    employeePermission(org, "READ");
    return accounts.lookupExactEmail(email).orElse(null);
  }

  List<Map<String, Object>> team(UUID org) {
    employeePermission(org, "READ");
    return db.queryForList(
        "select m.*,p.first_name,p.last_name,pr.id professional_id from licensing.member m left"
            + " join identity.person p on p.id=m.person_id left join professional.professional pr"
            + " on pr.person_id=m.person_id where m.organization_id=? order by m.invited_at",
        org);
  }

  @Transactional
  Map<String, Object> invite(UUID org, TeamRequest r) {
    employeePermission(org, "CREATE");
    if (r.role().equals("HOSPITAL_ADMIN")) requireAdministrator(org);
    lock(org);
    employeePermission(org, "CREATE");
    if (r.role().equals("HOSPITAL_ADMIN")) requireAdministrator(org);
    requireActive(org);
    expireInvitations(org);
    ensureDistinctProfile(org, r.personId(), r.email(), r.role(), null);
    capacity(org, r.role());
    if (r.personId() == null && (r.email() == null || r.email().isBlank()))
      throw new IllegalArgumentException("PERSON_OR_EMAIL_REQUIRED");
    UUID id = UUID.randomUUID();
    db.update(
        "insert into licensing.member(id,organization_id,person_id,email,role,status,expires_at)"
            + " values(?,?,?,?,?,'INVITED',now()+interval '7 days')",
        id,
        org,
        r.personId(),
        r.email(),
        r.role());
    initializeMemberPolicy(org, id);
    audit(org, "MEMBER_CHANGED", id);
    return db.queryForMap("select * from licensing.member where id=?", id);
  }

  @Transactional
  Map<String, Object> update(UUID org, UUID id, TeamUpdate r) {
    employeePermission(org, r.status().equals("INACTIVE") ? "DELETE" : "UPDATE");
    if (r.role().equals("HOSPITAL_ADMIN")) requireAdministrator(org);
    lock(org);
    employeePermission(org, r.status().equals("INACTIVE") ? "DELETE" : "UPDATE");
    if (r.role().equals("HOSPITAL_ADMIN")) requireAdministrator(org);
    var old =
        db.queryForMap("select * from licensing.member where id=? and organization_id=?", id, org);
    if ("HOSPITAL_ADMIN".equals(old.get("role"))) requireAdministrator(org);
    if (r.status().equals("INACTIVE")
        && "HOSPITAL_ADMIN".equals(old.get("role"))
        && isLocalAdministrator(org, (UUID) old.get("person_id")))
      throw new IllegalArgumentException("TRANSFER_ORGANIZATION_ADMINISTRATOR_FIRST");
    if (!Set.of("ACTIVE", "INACTIVE").contains(r.status()))
      throw new IllegalArgumentException("INVALID_MEMBER_STATUS");
    if (r.status().equals("ACTIVE")) {
      if (old.get("person_id") == null)
        throw new IllegalArgumentException("ACCEPT_INVITATION_FIRST");
      requireActive(org);
      expireInvitations(org);
      ensureDistinctProfile(
          org, (UUID) old.get("person_id"), (String) old.get("email"), r.role(), id);
      db.update("update licensing.member set status='INACTIVE' where id=?", id);
      capacity(org, r.role());
    }
    if (!r.role().equals(old.get("role")))
      synchronizeClinical(org, (UUID) old.get("person_id"), (String) old.get("role"), false);
    synchronizeClinical(org, (UUID) old.get("person_id"), r.role(), r.status().equals("ACTIVE"));
    db.update(
        "update licensing.member set role=?,status=?,updated_at=now() where id=?",
        r.role(),
        r.status(),
        id);
    audit(org, "MEMBER_CHANGED", id);
    return db.queryForMap("select * from licensing.member where id=?", id);
  }

  @Transactional
  Map<String, Object> accept(UUID org, UUID id) {
    lock(org);
    requireActive(org);
    UUID p = person();
    if (p == null) throw denied("IDENTITY_REQUIRED");
    var r =
        db.queryForMap(
            "select * from licensing.member where id=? and organization_id=? and status='INVITED'"
                + " and expires_at>now()",
            id,
            org);
    boolean matches = p.equals(r.get("person_id"));
    if (!matches && r.get("person_id") == null) {
      var a = (JwtAuthenticationToken) SecurityContextHolder.getContext().getAuthentication();
      String email = a.getToken().getClaimAsString("email");
      matches =
          Boolean.TRUE.equals(a.getToken().getClaimAsBoolean("email_verified"))
              && email != null
              && email.equalsIgnoreCase((String) r.get("email"));
    }
    if (!matches) throw denied("INVITATION_IDENTITY_MISMATCH");
    expireInvitations(org);
    ensureDistinctProfile(org, p, (String) r.get("email"), (String) r.get("role"), id);
    db.update("update licensing.member set status='INACTIVE' where id=?", id);
    capacity(org, (String) r.get("role"));
    initializeMemberPolicy(org, id);
    synchronizeClinical(org, p, (String) r.get("role"), true);
    db.update(
        "update licensing.member set person_id=?,status='ACTIVE',updated_at=now() where id=?",
        p,
        id);
    audit(org, "MEMBER_CHANGED", id);
    return db.queryForMap("select * from licensing.member where id=?", id);
  }

  @Transactional
  public void reserveProfessionalAssignment(UUID org, UUID person, String role) {
    employeePermission(org, "CREATE");
    lock(org);
    employeePermission(org, "CREATE");
    expireInvitations(org);
    requireActive(org);
    verifiedProfessional(person, role);
    var existing =
        db.queryForList(
            "select id,role from licensing.member where organization_id=? and person_id=? and"
                + " role=? and status='ACTIVE'",
            org,
            person,
            role);
    if (!existing.isEmpty() && existing.getFirst().get("role").equals(role)) return;
    db.update(
        "update licensing.member set status='INACTIVE' where organization_id=? and person_id=? and"
            + " role=? and status<>'INACTIVE'",
        org,
        person,
        role);
    capacity(org, role);
    UUID memberId = UUID.randomUUID();
    db.update(
        "insert into licensing.member(id,organization_id,person_id,role,status)"
            + " values(?,?,?,?,'ACTIVE')",
        memberId,
        org,
        person,
        role);
    initializeMemberPolicy(org, memberId);
    audit(org, "ASSIGNMENT_RESERVED", person);
  }

  @Transactional
  public void releaseProfessionalAssignment(UUID org, UUID person) {
    employeePermission(org, "DELETE");
    lock(org);
    employeePermission(org, "DELETE");
    db.update(
        "update licensing.member set status='INACTIVE',updated_at=now() where organization_id=? and"
            + " person_id=? and role in ('DOCTOR','NURSE') and role=(select case when"
            + " professional_type='MEDECIN' then 'DOCTOR' else professional_type end from"
            + " professional.professional where person_id=?)",
        org,
        person,
        person);
    audit(org, "ASSIGNMENT_RELEASED", person);
  }

  List<Map<String, Object>> myMemberships() {
    UUID p = person();
    if (p == null) return List.of();
    return db.queryForList(
        "select o.id as \"organizationId\",o.name as \"organizationName\",m.role from"
            + " licensing.member m join organization.organization o on o.id=m.organization_id where"
            + " m.person_id=? and m.status='ACTIVE' union select o.id,o.name,'HOSPITAL_ADMIN' from"
            + " organization.organization o where o.administrator_person_id=?",
        p,
        p);
  }

  List<Map<String, Object>> myInvitations() {
    UUID p = person();
    if (p == null) return List.of();
    var jwt = (JwtAuthenticationToken) SecurityContextHolder.getContext().getAuthentication();
    String email =
        Boolean.TRUE.equals(jwt.getToken().getClaimAsBoolean("email_verified"))
            ? jwt.getToken().getClaimAsString("email")
            : null;
    return db.queryForList(
        "select m.*,o.name as organization_name from licensing.member m join"
            + " organization.organization o on o.id=m.organization_id where m.status='INVITED' and"
            + " m.expires_at>now() and (m.person_id=? or (m.person_id is null and"
            + " lower(m.email)=lower(?))) order by m.invited_at",
        p,
        email);
  }

  private void synchronizeClinical(UUID org, UUID person, String role, boolean active) {
    if (person == null) return;
    if (!Set.of("DOCTOR", "NURSE").contains(role)) return;
    if (!active) {
      db.update(
          "update professional.professional_assignment set"
              + " status='INACTIVE',end_date=greatest(start_date,current_date) where"
              + " organization_id=? and professional_id in (select id from"
              + " professional.professional where person_id=?) and status='ACTIVE'",
          org,
          person);
      return;
    }
    UUID professional = verifiedProfessional(person, role);
    var assignments =
        db.queryForList(
            "select id from professional.professional_assignment where organization_id=? and"
                + " professional_id=? and status='ACTIVE' and start_date<=current_date and"
                + " (end_date is null or end_date>=current_date)",
            org,
            professional);
    if (assignments.isEmpty())
      db.update(
          "insert into"
              + " professional.professional_assignment(professional_id,organization_id,position,start_date,status)"
              + " values(?,?,?,current_date,'ACTIVE')",
          professional,
          org,
          role);
  }

  private UUID verifiedProfessional(UUID person, String role) {
    if (!Set.of("DOCTOR", "NURSE").contains(role))
      throw new IllegalArgumentException("INVALID_CLINICAL_ROLE");
    String alias = role.equals("DOCTOR") ? "MEDECIN" : role;
    var profiles =
        db.queryForList(
            "select p.id from professional.professional p where p.person_id=? and"
                + " p.professional_type in (?,?) and p.status='ACTIVE' and not exists(select 1 from"
                + " professional.registration_request r where r.person_id=p.person_id and"
                + " r.status<>'APPROVED')",
            person,
            role,
            alias);
    if (profiles.isEmpty())
      throw new IllegalArgumentException("VERIFIED_PROFESSIONAL_PROFILE_REQUIRED");
    return (UUID) profiles.getFirst().get("id");
  }

  List<Map<String, Object>> invoices(UUID org) {
    requireSubscriptionAdministrator(org);
    return db.queryForList(
        "select * from licensing.subscription_invoice where organization_id=? order by created_at"
            + " desc",
        org);
  }
}
