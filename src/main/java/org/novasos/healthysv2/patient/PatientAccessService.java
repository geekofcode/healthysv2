package org.novasos.healthysv2.patient;

import java.time.Instant;
import java.util.*;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class PatientAccessService {
    private final JdbcTemplate jdbc;
    private final CurrentUserContext users;
    private final PatientAuditService audit;
    private final org.novasos.healthysv2.licensing.api.LicensingAccess licensing;
    private final PatientAccessPolicy policy;

    PatientAccessService(JdbcTemplate jdbc, CurrentUserContext users, PatientAuditService audit, org.novasos.healthysv2.licensing.api.LicensingAccess licensing, PatientAccessPolicy policy) {
        this.jdbc = jdbc; this.users = users; this.audit = audit; this.licensing=licensing; this.policy=policy;
    }

    @Transactional(readOnly = true)
    public AccessDecision requireAccess(UUID patientId, String scope, String action) {
        CurrentUserContext.UserContext user;
        try {
            user = users.current();
        } catch (AccessDeniedException exception) {
            auditContextDenied(patientId, scope, exception.getMessage());
            throw exception;
        }
        AccessDecision decision = decide(user, patientId, normalize(scope), normalize(action));
        audit.access(user.personId(), "PATIENT_NOT_FOUND".equals(decision.reason())?null:patientId, user.organizationId(), scope, patientId,
                decision.allowed() ? action : "DENIED", decision.reason(),
                Map.of("allowed", decision.allowed(), "decision", decision.reason(), "roles", user.roles()));
        if (!decision.allowed()) throw new AccessDeniedException(decision.reason());
        return decision;
    }

    private void auditContextDenied(UUID patientId, String scope, String reason) {
        UUID person = null;
        var authentication = org.springframework.security.core.context.SecurityContextHolder.getContext().getAuthentication();
        if (authentication instanceof org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationToken token) {
            try {
                UUID subject = UUID.fromString(token.getToken().getSubject());
                person = jdbc.query("select id from identity.person where keycloak_user_id=? limit 1",
                        rs -> rs.next() ? rs.getObject(1, UUID.class) : null, subject);
            } catch (IllegalArgumentException ignored) {
                // Invalid subjects must not be attributed to another local person.
            }
        }
        audit.access(person, exists("select count(*) from patient.patient where id=?",patientId)?patientId:null, null, scope, patientId, "DENIED", reason,
                Map.of("allowed", false, "decision", reason));
    }

    private AccessDecision decide(CurrentUserContext.UserContext user, UUID patientId, String scope, String action) {
        if (!exists("select count(*) from patient.patient where id=?", patientId)) return denied("PATIENT_NOT_FOUND");
        if (user.personId()!=null && exists("select count(*) from patient.patient where id=? and person_id=?",patientId,user.personId())) return allowed("PATIENT_SELF");
        if (!user.hasAny("DOCTOR","NURSE","HOSPITAL_ADMIN")) return denied("ROLE_NOT_AUTHORIZED");
        if (user.personId()==null || user.organizationId()==null) return denied("LICENSED_ORGANIZATION_REQUIRED");
        try { licensing.requireMember(user.organizationId()); licensing.requireActive(user.organizationId()); }
        catch(AccessDeniedException e) {return denied("ORGANIZATION_LICENSE_OR_MEMBERSHIP_REQUIRED");}
        if (exists("select count(*) from patient.access_grant where patient_id=? and person_id=? and organization_id=? and revoked_at is null and granted_at<=clock_timestamp() and expires_at>clock_timestamp() and (scope=? or scope='FULL_RECORD')",patientId,user.personId(),user.organizationId(),scope)) {
            if (!exists("select count(*) from patient.access_revocation where patient_id=? and person_id=? and organization_id=?",patientId,user.personId(),user.organizationId()) || exists("select count(*) from patient.access_grant where patient_id=? and person_id=? and organization_id=? and source='EMERGENCY' and revoked_at is null and expires_at>clock_timestamp()",patientId,user.personId(),user.organizationId())) return allowed("TEMPORARY_GRANT");
        }
        if(exists("select count(*) from patient.access_revocation where patient_id=? and person_id=? and organization_id=?",patientId,user.personId(),user.organizationId())) return denied("PATIENT_REVOKED_ACCESS");
        if (!user.hasAny("DOCTOR","NURSE")) return denied("CLINICAL_ROLE_REQUIRED");
        UUID professional=jdbc.query("select id from professional.professional where person_id=? and status='ACTIVE' limit 1",rs->rs.next()?rs.getObject(1,UUID.class):null,user.personId());
        if(professional==null) return denied("PROFESSIONAL_PROFILE_MISSING");
        if(!exists("select count(*) from professional.professional_assignment where professional_id=? and organization_id=? and status='ACTIVE' and start_date<=current_date and (end_date is null or end_date>=current_date)",professional,user.organizationId())) return denied("PROFESSIONAL_NOT_ASSIGNED");
        String participant = user.has("NURSE") ? "" : " and v.professional_id=?";
        Object[] scheduled=user.has("NURSE")? new Object[]{patientId,user.organizationId(),policy.scheduledGrace.getSeconds()}:new Object[]{patientId,user.organizationId(),policy.scheduledGrace.getSeconds(),professional};
        if(exists("select count(*) from care.care_visit v join care.care_episode e on e.id=v.episode_id where e.patient_id=? and e.organization_id=? and e.status in ('READY','ACTIVE') and v.status='SCHEDULED' and v.created_at<=clock_timestamp() and v.scheduled_at + ? * interval '1 second'>clock_timestamp()"+participant,scheduled)) return allowed("SCHEDULED_VISIT");
        Object[] progress=user.has("NURSE")?new Object[]{patientId,user.organizationId(),policy.inProgressDuration.getSeconds()}:new Object[]{patientId,user.organizationId(),policy.inProgressDuration.getSeconds(),professional};
        if(exists("select count(*) from care.care_episode e join care.care_visit v on v.episode_id=e.id where e.patient_id=? and e.organization_id=? and e.status='ACTIVE' and v.status='IN_PROGRESS' and v.started_at<=clock_timestamp() and v.started_at + ? * interval '1 second'>clock_timestamp()"+participant,progress)) return allowed("IN_PROGRESS_VISIT");
        Object[] active=user.has("NURSE")?new Object[]{patientId,user.organizationId()}:new Object[]{patientId,user.organizationId(),professional};
        if(exists("select count(*) from care.care_episode e join care.care_visit v on v.episode_id=e.id where e.patient_id=? and e.organization_id=? and e.status='ACTIVE' and e.started_at<=clock_timestamp() and e.expires_at>clock_timestamp() and v.status<>'CANCELLED'"+participant,active)) return allowed("ACTIVE_EPISODE");
        // Legacy grants are still honored, but never indefinitely or without an effective expiry.
        if(exists("select count(*) from patient.consent where patient_id=? and grantee_person_id=? and status='ACTIVE' and revoked_at is null and granted_at<=clock_timestamp() and expires_at>clock_timestamp() and (scope=? or scope='FULL_RECORD')",patientId,user.personId(),scope)) return allowed("PATIENT_CONSENT");
        return denied("TEMPORARY_ACCESS_REQUIRED");
    }

    private boolean exists(String sql,Object...args){return Objects.requireNonNull(jdbc.queryForObject(sql,Integer.class,args))>0;}
    private String normalize(String value){if(value==null||value.isBlank())return "MEDICAL_RECORD";String normalized=value.trim().toUpperCase(java.util.Locale.ROOT);return switch(normalized){case "DOCUMENTS" -> "DOCUMENT";case "LAB_RESULTS","MATERNAL_HEALTH","PEDIATRIC_RECORD","PRESCRIPTIONS" -> "MEDICAL_RECORD";default -> normalized;};}
    private AccessDecision allowed(String reason){return new AccessDecision(true,reason,Instant.now());}private AccessDecision denied(String reason){return new AccessDecision(false,reason,Instant.now());}
    public record AccessDecision(boolean allowed,String reason,Instant decidedAt){}
}
