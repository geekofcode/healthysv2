package org.novasos.healthysv2.organizationaccess;

import java.util.*;
import org.novasos.healthysv2.organizationaccess.api.*;
import org.novasos.healthysv2.organizationaccess.api.OrganizationAccessDtos.*;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationToken;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.context.request.RequestContextHolder;
import org.springframework.web.context.request.ServletRequestAttributes;

@Service
public class OrganizationPermissionService implements OrganizationPermissionAccess {
  private static final Set<String> ACTIONS = Set.of("CREATE", "READ", "UPDATE", "DELETE");
  private static final Map<String, String> CATALOG =
      Map.ofEntries(
          Map.entry("patients", "Identité des patients"),
          Map.entry("clinicalrecords", "Dossiers médicaux"),
          Map.entry("episodes", "Épisodes de soins"),
          Map.entry("intakes", "Accueil et constantes"),
          Map.entry("visits", "Visites"),
          Map.entry("scheduling", "Programmation"),
          Map.entry("queue", "File d’attente"),
          Map.entry("reports", "Rapports de consultation"),
          Map.entry("prescriptions", "Prescriptions"),
          Map.entry("documents", "Documents"),
          Map.entry("tariffs", "Tarifs"),
          Map.entry("invoices", "Factures"),
          Map.entry("payments", "Encaissements"),
          Map.entry("refunds", "Restitutions"),
          Map.entry("cashclosures", "Clôtures de caisse"),
          Map.entry("cashreports", "Rapports de caisse"),
          Map.entry("employees", "Employés"),
          Map.entry("roles", "Rôles et accès"),
          Map.entry("orgconfiguration", "Configuration de l’établissement"),
          Map.entry("professionalavailability", "Disponibilités professionnelles"),
          Map.entry("patientaccess", "Autorisations patient"),
          Map.entry("ai", "Assistant IA"),
          Map.entry("audit", "Journal de l’établissement"),
          Map.entry("examrequests", "Demandes d’examens"));
  private final org.novasos.healthysv2.accessprofile.api.AccessProfileResolver profiles;
  private final JdbcTemplate db;

  public OrganizationPermissionService(
      JdbcTemplate db, org.novasos.healthysv2.accessprofile.api.AccessProfileResolver profiles) {
    this.db = db;
    this.profiles = profiles;
  }

  public List<Resource> catalog() {
    return CATALOG.entrySet().stream()
        .sorted(Map.Entry.comparingByKey())
        .map(e -> new Resource(e.getKey(), e.getValue(), ACTIONS))
        .toList();
  }

  private Actor actor(UUID org) {
    var auth = SecurityContextHolder.getContext().getAuthentication();
    if (!(auth instanceof JwtAuthenticationToken jwt)) throw denied("IDENTITY_REQUIRED");
    UUID subject;
    try {
      subject = UUID.fromString(jwt.getToken().getSubject());
    } catch (IllegalArgumentException e) {
      throw denied("IDENTITY_REQUIRED");
    }
    Set<String> roles = new HashSet<>();
    jwt.getAuthorities()
        .forEach(
            a -> {
              if (a.getAuthority().startsWith("ROLE_")) roles.add(a.getAuthority().substring(5));
            });
    UUID selectedProfile = null;
    UUID selectedOrg = null;
    var attributes = RequestContextHolder.getRequestAttributes();
    if (attributes instanceof ServletRequestAttributes servlet) {
      String profileHeader = servlet.getRequest().getHeader("X-Profile-ID");
      if (profileHeader != null && !profileHeader.isBlank())
        selectedProfile = uuid(profileHeader, "INVALID_ACCESS_PROFILE");
      String orgHeader = servlet.getRequest().getHeader("X-Organization-ID");
      if (orgHeader != null && !orgHeader.isBlank())
        selectedOrg = uuid(orgHeader, "INVALID_ORGANIZATION_CONTEXT");
    }
    var profile = profiles.resolve(subject, Set.copyOf(roles), selectedProfile, selectedOrg);
    if (profile == null || !profile.active() || "PLATFORM_ADMIN".equals(profile.type()))
      throw denied("PLATFORM_OPERATIONAL_ACCESS_DENIED");
    if (!Objects.equals(org, profile.organizationId()))
      throw denied("ACCESS_PROFILE_ORGANIZATION_MISMATCH");
    requireActive(org);
    if ("ORGANIZATION_ADMINISTRATOR".equals(profile.sourceType())
        && "HOSPITAL_ADMIN".equals(profile.type()))
      return new Actor(profile.personId(), null, "HOSPITAL_ADMIN", true);
    if (!"ORGANIZATION_MEMBERSHIP".equals(profile.sourceType()))
      throw denied("ORGANIZATION_MEMBERSHIP_REQUIRED");
    return actorForProfile(org, profile.personId(), profile.sourceId());
  }

  private UUID uuid(String value, String error) {
    try {
      return UUID.fromString(value);
    } catch (IllegalArgumentException e) {
      throw denied(error);
    }
  }

  private void requireActive(UUID org) {
    Integer active =
        db.queryForObject(
            "select count(*) from licensing.subscription s join organization.organization o on"
                + " o.id=s.organization_id where o.id=? and o.status='ACTIVE' and s.status='active'"
                + " and s.period_start<=clock_timestamp() and s.period_end>clock_timestamp()",
            Integer.class,
            org);
    if (!Integer.valueOf(1).equals(active)) throw denied("ACTIVE_SUBSCRIPTION_REQUIRED");
  }

  private Actor actorForProfile(UUID org, UUID person, UUID profileId) {
    if (person == null || profileId == null) throw denied("ACCESS_PROFILE_REQUIRED");
    requireActive(org);
    List<Actor> memberships =
        db.query(
            "select id,role from licensing.member where id=? and organization_id=? and person_id=?"
                + " and status='ACTIVE'",
            (rs, n) ->
                new Actor(
                    person,
                    (UUID) rs.getObject(1),
                    rs.getString(2),
                    "HOSPITAL_ADMIN".equals(rs.getString(2))),
            profileId,
            org,
            person);
    if (memberships.size() != 1) throw denied("ORGANIZATION_MEMBERSHIP_REQUIRED");
    return memberships.getFirst();
  }

  private Actor actorForPerson(UUID org, UUID person) {
    var memberIds =
        db.queryForList(
            "select id from licensing.member where organization_id=? and person_id=? and"
                + " status='ACTIVE'",
            UUID.class,
            org,
            person);
    if (memberIds.size() != 1) throw denied("ACCESS_PROFILE_REQUIRED");
    return actorForProfile(org, person, memberIds.getFirst());
  }

  private AccessDeniedException denied(String code) {
    return new AccessDeniedException(code);
  }

  private record Actor(UUID person, UUID member, String role, boolean admin) {}

  void requireAdministrator(UUID org) {
    if (!actor(org).admin()) throw denied("ORGANIZATION_ADMIN_REQUIRED");
  }

  @Override
  public void require(UUID org, String resource, String action) {
    validate(resource, Set.of(action));
    EffectivePermissions effective = effective(org);
    if (effective.permissions().stream()
        .noneMatch(p -> p.resource().equals(resource) && p.actions().contains(action)))
      throw denied("ORGANIZATION_PERMISSION_REQUIRED:" + resource + ":" + action);
  }

  @Override
  @Transactional(readOnly = true)
  public void requireForPerson(UUID org, UUID person, String resource, String action) {
    validate(resource, Set.of(action));
    if (person == null
        || !Integer.valueOf(1)
            .equals(
                db.queryForObject(
                    "select count(*) from identity.person where id=? and status='ACTIVE'",
                    Integer.class,
                    person))) throw denied("ACTIVE_IDENTITY_REQUIRED");
    var resolved = effective(org, actorForPerson(org, person));
    if (resolved.permissions().stream()
        .noneMatch(p -> p.resource().equals(resource) && p.actions().contains(action)))
      throw denied("ORGANIZATION_PERMISSION_REQUIRED:" + resource + ":" + action);
  }

  @Override
  @Transactional(readOnly = true)
  public void requireForProfile(
      UUID org, UUID person, UUID profileId, String resource, String action) {
    validate(resource, Set.of(action));
    if (person == null
        || !Integer.valueOf(1)
            .equals(
                db.queryForObject(
                    "select count(*) from identity.person where id=? and status='ACTIVE'",
                    Integer.class,
                    person))) throw denied("ACTIVE_IDENTITY_REQUIRED");
    var resolved = effective(org, actorForProfile(org, person, profileId));
    if (resolved.permissions().stream()
        .noneMatch(p -> p.resource().equals(resource) && p.actions().contains(action)))
      throw denied("ORGANIZATION_PERMISSION_REQUIRED:" + resource + ":" + action);
  }

  @Override
  @Transactional(readOnly = true)
  public EffectivePermissions effective(UUID org) {
    return effective(org, actor(org));
  }

  private EffectivePermissions effective(UUID org, Actor actor) {
    if (actor.admin())
      return new EffectivePermissions(
          org,
          true,
          catalog().stream().map(r -> new PermissionGrant(r.resource(), ACTIONS)).toList());
    boolean explicit =
        Integer.valueOf(1)
            .equals(
                db.queryForObject(
                    "select count(*) from organizationaccess.member_policy where member_id=? and"
                        + " organization_id=?",
                    Integer.class,
                    actor.member(),
                    org));
    if (!explicit) return new EffectivePermissions(org, false, baseline(actor.role()));
    Map<String, Set<String>> grants = new TreeMap<>();
    db.query(
        "select distinct p.resource,p.action from organizationaccess.member_role mr join"
            + " organizationaccess.role r on r.id=mr.role_id and"
            + " r.organization_id=mr.organization_id join organizationaccess.permission p on"
            + " p.role_id=r.id where mr.member_id=? and mr.organization_id=?",
        rs -> {
          grants.computeIfAbsent(rs.getString(1), k -> new TreeSet<>()).add(rs.getString(2));
        },
        actor.member(),
        org);
    return new EffectivePermissions(
        org,
        false,
        grants.entrySet().stream()
            .map(e -> new PermissionGrant(e.getKey(), Set.copyOf(e.getValue())))
            .toList());
  }

  private List<PermissionGrant> baseline(String role) {
    Set<String> resources =
        switch (role) {
          case "DOCTOR" ->
              Set.of(
                  "patients",
                  "clinicalrecords",
                  "episodes",
                  "intakes",
                  "visits",
                  "scheduling",
                  "queue",
                  "reports",
                  "prescriptions",
                  "documents",
                  "patientaccess",
                  "professionalavailability",
                  "ai");
          case "NURSE" ->
              Set.of(
                  "patients",
                  "clinicalrecords",
                  "episodes",
                  "intakes",
                  "visits",
                  "scheduling",
                  "queue",
                  "documents",
                  "invoices",
                  "patientaccess",
                  "professionalavailability",
                  "reports",
                  "prescriptions",
                  "ai");
          case "CASHIER" ->
              Set.of("invoices", "payments", "refunds", "cashclosures", "cashreports");
          case "ACCOUNTANT" ->
              Set.of("invoices", "payments", "refunds", "cashclosures", "cashreports", "tariffs");
          case "HOSPITAL_AGENT" ->
              Set.of("patients", "episodes", "visits", "scheduling", "queue", "invoices");
          default -> Set.of();
        };
    return resources.stream()
        .sorted()
        .map(r -> new PermissionGrant(r, role.equals("ACCOUNTANT") ? Set.of("READ") : ACTIONS))
        .toList();
  }

  private void validate(String resource, Set<String> actions) {
    if (!CATALOG.containsKey(resource) || !ACTIONS.containsAll(actions))
      throw new IllegalArgumentException("INVALID_ORGANIZATION_PERMISSION");
  }

  private void validate(RoleRequest request) {
    request.permissions().forEach(p -> validate(p.resource(), p.actions()));
  }

  @Transactional(readOnly = true)
  public List<RoleResponse> roles(UUID org) {
    requireAdministrator(org);
    return db.query(
        "select id,name,description,version from organizationaccess.role where organization_id=?"
            + " order by lower(name)",
        (rs, n) ->
            role(org, (UUID) rs.getObject(1), rs.getString(2), rs.getString(3), rs.getLong(4)),
        org);
  }

  private RoleResponse role(UUID org, UUID id, String name, String description, long version) {
    Map<String, Set<String>> grants = new TreeMap<>();
    db.query(
        "select resource,action from organizationaccess.permission where role_id=?",
        rs -> {
          grants.computeIfAbsent(rs.getString(1), k -> new TreeSet<>()).add(rs.getString(2));
        },
        id);
    return new RoleResponse(
        id,
        org,
        name,
        description,
        version,
        grants.entrySet().stream()
            .map(e -> new PermissionGrant(e.getKey(), Set.copyOf(e.getValue())))
            .toList(),
        Boolean.TRUE.equals(
            db.queryForObject(
                "select reserved from organizationaccess.role where id=? and organization_id=?",
                Boolean.class,
                id,
                org)));
  }

  @Transactional
  public RoleResponse create(UUID org, RoleRequest request) {
    lockAdministrator(org);
    validate(request);
    if (Set.of("DOCTOR", "NURSE").contains(request.name().trim().toUpperCase(Locale.ROOT)))
      throw new IllegalArgumentException("RESERVED_ROLE_NAME");
    UUID id = UUID.randomUUID();
    db.update(
        "insert into organizationaccess.role(id,organization_id,name,description) values(?,?,?,?)",
        id,
        org,
        request.name().trim(),
        request.description());
    permissions(id, request.permissions());
    audit(org, "ROLE_CREATED", id);
    return role(org, id, request.name().trim(), request.description(), 0);
  }

  @Transactional
  public RoleResponse update(UUID org, UUID id, RoleRequest request) {
    lockAdministrator(org);
    validate(request);
    var protectedNames =
        db.queryForList(
            "select name from organizationaccess.role where id=? and organization_id=? and"
                + " reserved",
            String.class,
            id,
            org);
    if (!protectedNames.isEmpty() && !protectedNames.getFirst().equals(request.name().trim()))
      throw new IllegalArgumentException("RESERVED_ROLE_IMMUTABLE");
    if (request.version() == null) throw new IllegalArgumentException("ROLE_VERSION_REQUIRED");
    int count =
        db.update(
            "update organizationaccess.role set"
                + " name=?,description=?,version=version+1,updated_at=now() where id=? and"
                + " organization_id=? and version=?",
            request.name().trim(),
            request.description(),
            id,
            org,
            request.version());
    if (count != 1)
      throw new org.springframework.dao.OptimisticLockingFailureException(
          "ROLE_CHANGED_OR_NOT_FOUND");
    db.update("delete from organizationaccess.permission where role_id=?", id);
    permissions(id, request.permissions());
    audit(org, "ROLE_UPDATED", id);
    return role(org, id, request.name().trim(), request.description(), request.version() + 1);
  }

  private void permissions(UUID id, List<PermissionGrant> permissions) {
    permissions.stream()
        .distinct()
        .forEach(
            p ->
                p.actions()
                    .forEach(
                        a ->
                            db.update(
                                "insert into organizationaccess.permission(role_id,resource,action)"
                                    + " values(?,?,?) on conflict do nothing",
                                id,
                                p.resource(),
                                a)));
  }

  @Transactional
  public void delete(UUID org, UUID id) {
    lockAdministrator(org);
    if (Boolean.TRUE.equals(
        db.queryForObject(
            "select exists(select 1 from organizationaccess.role where id=? and organization_id=?"
                + " and reserved)",
            Boolean.class,
            id,
            org))) throw new IllegalArgumentException("RESERVED_ROLE_IMMUTABLE");
    if (db.update("delete from organizationaccess.role where id=? and organization_id=?", id, org)
        != 1) throw new NoSuchElementException("ROLE_NOT_FOUND");
    audit(org, "ROLE_DELETED", id);
  }

  private void member(UUID org, UUID member) {
    if (!Integer.valueOf(1)
        .equals(
            db.queryForObject(
                "select count(*) from licensing.member where id=? and organization_id=?",
                Integer.class,
                member,
                org))) throw new NoSuchElementException("MEMBER_NOT_FOUND");
  }

  @Transactional(readOnly = true)
  public Set<UUID> assigned(UUID org, UUID member) {
    requireAdministrator(org);
    member(org, member);
    return Set.copyOf(
        db.queryForList(
            "select role_id from organizationaccess.member_role where member_id=? and"
                + " organization_id=?",
            UUID.class,
            member,
            org));
  }

  @Transactional
  public Set<UUID> assign(UUID org, UUID member, Set<UUID> roleIds) {
    lockAdministrator(org);
    db.query(
        "select id from licensing.member where id=? and organization_id=? for update",
        rs -> {},
        member,
        org);
    member(org, member);
    for (UUID role : roleIds) {
      if (!Integer.valueOf(1)
          .equals(
              db.queryForObject(
                  "select count(*) from organizationaccess.role where id=? and organization_id=?",
                  Integer.class,
                  role,
                  org))) throw new IllegalArgumentException("ROLE_TENANT_MISMATCH");
    }
    db.update(
        "insert into organizationaccess.member_policy(member_id,organization_id) values(?,?) on"
            + " conflict(member_id) do update set updated_at=now()",
        member,
        org);
    db.update("delete from organizationaccess.member_role where member_id=?", member);
    roleIds.forEach(
        r ->
            db.update(
                "insert into organizationaccess.member_role(member_id,organization_id,role_id)"
                    + " values(?,?,?)",
                member,
                org,
                r));
    audit(org, "MEMBER_ROLES_UPDATED", member);
    return Set.copyOf(roleIds);
  }

  private void lockAdministrator(UUID org) {
    Boolean found =
        db.query(
            "select id from organization.organization where id=? for update",
            (org.springframework.jdbc.core.ResultSetExtractor<Boolean>) rs -> rs.next(),
            org);
    if (!Boolean.TRUE.equals(found)) throw new NoSuchElementException("ORGANIZATION_NOT_FOUND");
    // Read membership and license after acquiring the same tenant lock as operational writes.
    requireAdministrator(org);
  }

  private void audit(UUID org, String action, UUID target) {
    db.update(
        "insert into licensing.audit_entry(organization_id,actor_person_id,action,target_id)"
            + " values(?,?,?,?)",
        org,
        actor(org).person(),
        action,
        target);
  }
}
