package org.novasos.healthysv2.care;

import java.util.*;
import org.novasos.healthysv2.patient.*;
import org.novasos.healthysv2.shared.api.error.ResourceNotFoundException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.stereotype.Component;

@Component
class AiAuthorization {
  private final org.novasos.healthysv2.organizationaccess.api.OrganizationPermissionAccess
      permissions;
  private final org.novasos.healthysv2.profilecontract.api.ContractAccess contracts;
  private final JdbcTemplate db;
  private final PatientAccessService access;

  AiAuthorization(
      JdbcTemplate db,
      PatientAccessService access,
      org.novasos.healthysv2.organizationaccess.api.OrganizationPermissionAccess permissions,
      org.novasos.healthysv2.profilecontract.api.ContractAccess contracts) {
    this.contracts = contracts;
    this.permissions = permissions;
    this.db = db;
    this.access = access;
  }

  Context require(UUID visit, UUID actor, UUID org) {
    return require(visit, actor, org, null);
  }

  Context require(UUID visit, UUID actor, UUID org, UUID profileId) {
    if (actor == null || org == null || profileId == null)
      throw new AccessDeniedException("ASSIGNED_CLINICIAN_REQUIRED");
    contracts.requireSigned(actor, profileId);
    permissions.requireForProfile(org, actor, profileId, "clinicalrecords", "READ");
    var rows =
        db.queryForList(
            "select e.patient_id,v.professional_id from care.care_visit v join care.care_episode e"
                + " on e.id=v.episode_id where v.id=? and e.organization_id=? and"
                + " v.status<>'CANCELLED'",
            visit,
            org);
    if (rows.isEmpty()) throw new ResourceNotFoundException("CareVisit", visit);
    UUID professional = (UUID) rows.getFirst().get("professional_id"),
        patient = (UUID) rows.getFirst().get("patient_id");
    Integer n =
        db.queryForObject(
            """
            select count(*) from professional.professional p join identity.person person on person.id=p.person_id
            where p.id=? and p.person_id=? and p.status='ACTIVE' and person.status='ACTIVE' and p.professional_type in ('DOCTOR','MEDECIN','NURSE')
            and exists(select 1 from professional.professional_assignment a where a.professional_id=p.id and a.organization_id=? and a.status='ACTIVE' and a.start_date<=current_date and (a.end_date is null or a.end_date>=current_date))
            and exists(select 1 from licensing.member m where m.person_id=p.person_id and m.organization_id=? and m.status='ACTIVE' and m.id=? and ((m.role='DOCTOR' and p.professional_type in ('DOCTOR','MEDECIN')) or (m.role='NURSE' and p.professional_type='NURSE')))
            """,
            Integer.class,
            professional,
            actor,
            org,
            org,
            profileId);
    if (n == null || n == 0) throw new AccessDeniedException("ASSIGNED_CLINICIAN_REQUIRED");
    String role =
        db.queryForObject(
            "select role from licensing.member where id=? and person_id=? and organization_id=? and"
                + " status='ACTIVE'",
            String.class,
            profileId,
            actor,
            org);
    if (!Set.of("DOCTOR", "NURSE").contains(role == null ? "" : role))
      throw new AccessDeniedException("ASSIGNED_CLINICIAN_REQUIRED");
    Integer entitlement =
        db.queryForObject(
            "select count(*) from licensing.subscription s join organization.organization o on"
                + " o.id=s.organization_id where s.organization_id=? and s.status='active' and"
                + " s.period_start<=clock_timestamp() and s.period_end>clock_timestamp() and"
                + " s.ai_assistant_enabled and o.status='ACTIVE'",
            Integer.class,
            org);
    if (entitlement == null || entitlement == 0) throw new AccessDeniedException("AI_NOT_INCLUDED");
    access.requireAccessForActor(
        new CurrentUserContext.UserContext(actor, org, Set.of(role), profileId),
        patient,
        "MEDICAL_RECORD",
        "READ");
    return new Context(visit, actor, org, patient, profileId);
  }

  Context requireExecution(UUID visit, UUID actor, UUID org, UUID profileId) {
    if (profileId == null) throw new AccessDeniedException("AI_JOB_PROFILE_REQUIRED");
    permissions.requireForProfile(org, actor, profileId, "ai", "CREATE");
    return require(visit, actor, org, profileId);
  }

  record Context(UUID visitId, UUID actorId, UUID organizationId, UUID patientId, UUID profileId) {
    Context(UUID visitId, UUID actorId, UUID organizationId, UUID patientId) {
      this(visitId, actorId, organizationId, patientId, null);
    }
  }
}
