package org.novasos.healthysv2.care;

import java.util.*;
import org.novasos.healthysv2.patient.*;
import org.novasos.healthysv2.shared.api.error.ResourceNotFoundException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.stereotype.Component;

@Component
class AiAuthorization {
  private final JdbcTemplate db;
  private final PatientAccessService access;

  AiAuthorization(JdbcTemplate db, PatientAccessService access) {
    this.db = db;
    this.access = access;
  }

  Context require(UUID visit, UUID actor, UUID org) {
    if (actor == null || org == null) throw new AccessDeniedException("ASSIGNED_DOCTOR_REQUIRED");
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
            where p.id=? and p.person_id=? and p.status='ACTIVE' and person.status='ACTIVE' and p.professional_type in ('DOCTOR','MEDECIN')
            and exists(select 1 from professional.professional_assignment a where a.professional_id=p.id and a.organization_id=? and a.status='ACTIVE' and a.start_date<=current_date and (a.end_date is null or a.end_date>=current_date))
            and exists(select 1 from licensing.member m where m.person_id=p.person_id and m.organization_id=? and m.role='DOCTOR' and m.status='ACTIVE')
            """,
            Integer.class,
            professional,
            actor,
            org,
            org);
    if (n == null || n == 0) throw new AccessDeniedException("ASSIGNED_DOCTOR_REQUIRED");
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
        new CurrentUserContext.UserContext(actor, org, Set.of("DOCTOR")),
        patient,
        "MEDICAL_RECORD",
        "READ");
    return new Context(visit, actor, org, patient);
  }

  record Context(UUID visitId, UUID actorId, UUID organizationId, UUID patientId) {}
}
