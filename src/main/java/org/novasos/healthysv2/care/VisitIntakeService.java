package org.novasos.healthysv2.care;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.time.*;
import java.util.UUID;
import org.novasos.healthysv2.audit.AuditTrail;
import org.novasos.healthysv2.licensing.api.LicensingAccess;
import org.novasos.healthysv2.patient.CurrentUserContext;
import org.novasos.healthysv2.patient.PatientAccessService;
import org.novasos.healthysv2.shared.api.error.*;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@Transactional
class VisitIntakeService {
    private final CareVisitRepository visits;
    private final CurrentUserContext users;
    private final LicensingAccess licensing;
    private final PatientAccessService access;
    private final JdbcTemplate jdbc;
    private final ObjectMapper json;
    private final CarePolicy policy;
    private final AuditTrail audit;
    VisitIntakeService(CareVisitRepository visits,CurrentUserContext users,LicensingAccess licensing,
            PatientAccessService access,JdbcTemplate jdbc,ObjectMapper json,CarePolicy policy,AuditTrail audit) {
        this.visits=visits;this.users=users;this.licensing=licensing;this.access=access;
        this.jdbc=jdbc;this.json=json;this.policy=policy;this.audit=audit;
    }
    ReceptionDtos.Intake read(UUID id) {
        var user=staff();
        var visit=scoped(id,user.organizationId(),false);
        boolean reception=user.has("NURSE") && visit.getStatus()!=CareVisit.Status.COMPLETED
                && visit.getStatus()!=CareVisit.Status.CANCELLED && visit.episode().getStatus()!=CareEpisode.Status.CLOSED
                && (visit.episode().getStatus()!=CareEpisode.Status.ACTIVE || visit.episode().isActiveAt(Instant.now()));
        if(!reception) access.requireAccess(visit.episode().getPatientId(),"MEDICAL_RECORD","READ");
        var result=jdbc.query("select observations,vitals::text from care.visit_intake where visit_id=?",rs->
                rs.next()?new ReceptionDtos.Intake(rs.getString(1),decode(rs.getString(2))):empty(),id);
        audit.access(user.personId(),visit.episode().getPatientId(),user.organizationId(),"VISIT_INTAKE",id,
                "READ",reception?"CURRENT_RECEPTION":"CLINICAL_ACCESS",java.util.Map.of("visitId",id));
        return result;
    }
    ReceptionDtos.Intake write(UUID id,ReceptionDtos.Intake request) {
        var user=staff();
        if(!user.has("NURSE")) throw new AccessDeniedException("NURSE_REQUIRED");
        jdbc.queryForObject("select id from organization.organization where id=? for update",UUID.class,user.organizationId());
        licensing.requireMember(user.organizationId()); licensing.requireActive(user.organizationId());
        Integer nurse=jdbc.queryForObject("select count(*) from licensing.member where organization_id=? and person_id=? and role='NURSE' and status='ACTIVE'",Integer.class,user.organizationId(),user.personId());
        if(nurse==null || nurse==0) throw new AccessDeniedException("NURSE_ROLE_REVOKED");
        var visit=scoped(id,user.organizationId(),true);
        if(visit.getStatus()==CareVisit.Status.IN_PROGRESS || visit.getStatus()==CareVisit.Status.COMPLETED || visit.getStatus()==CareVisit.Status.CANCELLED
                || visit.episode().getStatus()==CareEpisode.Status.CLOSED
                || (visit.episode().getStatus()==CareEpisode.Status.ACTIVE && !visit.episode().isActiveAt(Instant.now()))) {
            throw new BusinessRuleException("INTAKE_CLOSED","Reception is no longer open for this visit");
        }
        var vitals=request.vitals();
        if(vitals.systolicBp()!=null && vitals.diastolicBp()!=null && vitals.systolicBp()<=vitals.diastolicBp()) {
            throw new BusinessRuleException("INVALID_BLOOD_PRESSURE","Systolic pressure must exceed diastolic pressure");
        }
        Instant now=Instant.now();
        jdbc.update("""
                insert into care.visit_intake(visit_id,observations,vitals,recorded_by,recorded_at,retention_expires_at)
                values(?,?,?::jsonb,?,?,?) on conflict(visit_id) do update set observations=excluded.observations,
                    vitals=excluded.vitals,recorded_by=excluded.recorded_by,recorded_at=excluded.recorded_at
                """,id,request.observations()==null?"":request.observations().trim(),encode(vitals),user.personId(),
                java.sql.Timestamp.from(now),java.sql.Timestamp.from(now.atZone(ZoneOffset.UTC).plusYears(policy.retentionYears()).toInstant()));
        audit.change(user.personId(),user.organizationId(),"CARE","VisitIntake",id,"UPDATE",null,java.util.Map.of("recordedAt",now));
        return request;
    }
    private CurrentUserContext.UserContext staff() {
        var user=users.current();
        if(user.personId()==null || user.organizationId()==null || !user.hasAny("NURSE","DOCTOR")) throw new AccessDeniedException("CLINICAL_ROLE_REQUIRED");
        licensing.requireMember(user.organizationId()); licensing.requireActive(user.organizationId()); return user;
    }
    private CareVisit scoped(UUID id,UUID org,boolean locked) {
        return (locked?visits.findLockedById(id):visits.findById(id)).filter(v->v.episode().getOrganizationId().equals(org))
                .orElseThrow(()->new ResourceNotFoundException("CareVisit",id));
    }
    private ReceptionDtos.Intake empty() { return new ReceptionDtos.Intake("",new ReceptionDtos.Vitals(null,null,null,null,null,null,null,null)); }
    private String encode(Object value) {try{return json.writeValueAsString(value);}catch(JsonProcessingException e){throw new IllegalStateException(e);}}
    private ReceptionDtos.Vitals decode(String value) {try{return json.readValue(value,ReceptionDtos.Vitals.class);}catch(JsonProcessingException e){throw new IllegalStateException(e);}}
}
