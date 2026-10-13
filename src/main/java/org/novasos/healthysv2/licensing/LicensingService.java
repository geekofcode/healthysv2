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
          "select id from identity.person where keycloak_user_id=?",
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

  public void requireAdministrator(UUID org) {
    if (platform()) return;
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

  public void requireMember(UUID org) {
    if (platform()) return;
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
    requireMember(org);
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

  List<Map<String, Object>> team(UUID org) {
    requireAdministrator(org);
    return db.queryForList(
        "select m.*,p.first_name,p.last_name from licensing.member m left join identity.person p on"
            + " p.id=m.person_id where m.organization_id=? order by m.invited_at",
        org);
  }

  @Transactional
  Map<String, Object> invite(UUID org, TeamRequest r) {
    requireAdministrator(org);
    lock(org);
    requireActive(org);
    expireInvitations(org);
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
    audit(org, "MEMBER_CHANGED", id);
    return db.queryForMap("select * from licensing.member where id=?", id);
  }

  @Transactional
  Map<String, Object> update(UUID org, UUID id, TeamUpdate r) {
    requireAdministrator(org);
    lock(org);
    var old =
        db.queryForMap("select * from licensing.member where id=? and organization_id=?", id, org);
    if (!Set.of("ACTIVE", "INACTIVE").contains(r.status()))
      throw new IllegalArgumentException("INVALID_MEMBER_STATUS");
    if (r.status().equals("ACTIVE")) {
      if (old.get("person_id") == null)
        throw new IllegalArgumentException("ACCEPT_INVITATION_FIRST");
      requireActive(org);
      db.update("update licensing.member set status='INACTIVE' where id=?", id);
      capacity(org, r.role());
    }
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
    db.update("update licensing.member set status='INACTIVE' where id=?", id);
    capacity(org, (String) r.get("role"));
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
    requireAdministrator(org);
    lock(org);
    expireInvitations(org);
    requireActive(org);
    verifiedProfessional(person, role);
    var existing =
        db.queryForList(
            "select id,role from licensing.member where organization_id=? and person_id=? and"
                + " status='ACTIVE'",
            org,
            person);
    if (!existing.isEmpty() && existing.getFirst().get("role").equals(role)) return;
    db.update(
        "update licensing.member set status='INACTIVE' where organization_id=? and person_id=? and"
            + " status<>'INACTIVE'",
        org,
        person);
    capacity(org, role);
    db.update(
        "insert into licensing.member(organization_id,person_id,role,status)"
            + " values(?,?,?,'ACTIVE')",
        org,
        person,
        role);
    audit(org, "ASSIGNMENT_RESERVED", person);
  }

  @Transactional
  public void releaseProfessionalAssignment(UUID org, UUID person) {
    requireAdministrator(org);
    lock(org);
    db.update(
        "update licensing.member set status='INACTIVE',updated_at=now() where organization_id=? and"
            + " person_id=? and role in ('DOCTOR','NURSE','HOSPITAL_AGENT')",
        org,
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
    if (!active || !Set.of("DOCTOR", "NURSE").contains(role)) {
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
    requireAdministrator(org);
    return db.queryForList(
        "select * from licensing.subscription_invoice where organization_id=? order by created_at"
            + " desc",
        org);
  }
}
