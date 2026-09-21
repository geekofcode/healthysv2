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

    PatientAccessService(JdbcTemplate jdbc, CurrentUserContext users, PatientAuditService audit) {
        this.jdbc = jdbc; this.users = users; this.audit = audit;
    }

    @Transactional(readOnly = true)
    public AccessDecision requireAccess(UUID patientId, String scope, String action) {
        var user = users.current();
        AccessDecision decision = decide(user, patientId, normalize(scope), normalize(action));
        audit.access(user.personId(), patientId, user.organizationId(), scope, patientId,
                decision.allowed() ? action : "DENIED", decision.reason(),
                Map.of("allowed", decision.allowed(), "decision", decision.reason(), "roles", user.roles()));
        if (!decision.allowed()) throw new AccessDeniedException(decision.reason());
        return decision;
    }

    private AccessDecision decide(CurrentUserContext.UserContext user, UUID patientId, String scope, String action) {
        if (!exists("select count(*) from patient.patient where id=?", patientId)) return denied("PATIENT_NOT_FOUND");
        if (user.has("PLATFORM_ADMIN")) return allowed("PLATFORM_ADMIN");
        if (user.has("PATIENT") && user.personId()!=null && exists("select count(*) from patient.patient where id=? and person_id=?",patientId,user.personId())) return allowed("PATIENT_SELF");
        if (user.has("HOSPITAL_ADMIN") && registered(patientId,user.organizationId())) return allowed("ORGANIZATION_ADMIN");
        if (!user.hasAny("DOCTOR","NURSE","PHARMACIST","LAB_TECHNICIAN")) return denied("ROLE_NOT_AUTHORIZED");
        if (user.personId()==null) return denied("PERSON_CONTEXT_MISSING");
        if (user.organizationId()==null) return denied("ORGANIZATION_CONTEXT_MISSING");
        UUID professional=jdbc.query("select id from professional.professional where person_id=? and status='ACTIVE' limit 1",rs->rs.next()?(UUID)rs.getObject(1):null,user.personId());
        if (professional==null) return denied("PROFESSIONAL_PROFILE_MISSING");
        if (!exists("select count(*) from professional.professional_assignment where professional_id=? and organization_id=? and status='ACTIVE' and start_date<=current_date and (end_date is null or end_date>=current_date)",professional,user.organizationId())) return denied("PROFESSIONAL_NOT_ASSIGNED");
        if (!registered(patientId,user.organizationId())) return denied("PATIENT_OTHER_ORGANIZATION");
        if (!exists("select count(*) from patient.care_relationship where patient_id=? and professional_id=? and organization_id=? and status='ACTIVE' and start_date<=now() and (end_date is null or end_date>now())",patientId,professional,user.organizationId())) return denied("CARE_RELATIONSHIP_MISSING");
        if (!exists("select count(*) from patient.consent where patient_id=? and status='ACTIVE' and revoked_at is null and (expires_at is null or expires_at>now()) and (grantee_person_id=? or grantee_organization_id=?) and (scope=? or scope='FULL_RECORD')",patientId,user.personId(),user.organizationId(),scope)) return denied("CONSENT_MISSING_OR_INACTIVE");
        return allowed("CARE_CONTEXT_AUTHORIZED");
    }

    private boolean registered(UUID patient,UUID organization){return organization!=null&&exists("select count(*) from patient.patient_registration where patient_id=? and organization_id=? and status='ACTIVE'",patient,organization);}
    private boolean exists(String sql,Object...args){return Objects.requireNonNull(jdbc.queryForObject(sql,Integer.class,args))>0;}
    private String normalize(String value){return value==null||value.isBlank()?"MEDICAL_RECORD":value.trim().toUpperCase();}
    private AccessDecision allowed(String reason){return new AccessDecision(true,reason,Instant.now());}private AccessDecision denied(String reason){return new AccessDecision(false,reason,Instant.now());}
    public record AccessDecision(boolean allowed,String reason,Instant decidedAt){}
}
