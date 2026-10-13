package org.novasos.healthysv2.patient;

import java.sql.Timestamp;
import java.time.Instant;
import java.util.*;
import org.novasos.healthysv2.licensing.api.LicensingAccess;
import org.novasos.healthysv2.shared.api.error.ResourceNotFoundException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@Transactional
class TemporaryPatientAccessService {
    private final JdbcTemplate jdbc;
    private final CurrentUserContext users;
    private final LicensingAccess licensing;
    private final PatientAccessPolicy policy;
    private final PatientAuditService audit;
    TemporaryPatientAccessService(JdbcTemplate jdbc, CurrentUserContext users, LicensingAccess licensing,
                                 PatientAccessPolicy policy, PatientAuditService audit) {
        this.jdbc=jdbc; this.users=users; this.licensing=licensing; this.policy=policy; this.audit=audit;
    }
    Map<String,Object> request(UUID patient, String scope, String reason) {
        var u=staff(false);
        requirePatient(patient);
        String normalized=scope==null?"FULL_RECORD":scope.trim().toUpperCase(Locale.ROOT);
        if(!Set.of("FULL_RECORD","MEDICAL_RECORD","DOCUMENT").contains(normalized)) throw new org.novasos.healthysv2.shared.api.error.BusinessRuleException("INVALID_ACCESS_SCOPE", "Unsupported access scope");
        UUID id=UUID.randomUUID(); Instant now=Instant.now();
        jdbc.update("insert into patient.access_request values (?,?,?,?,?,?,'PENDING',?,?)",id,patient,u.personId(),u.organizationId(),normalized,reason,Timestamp.from(now),Timestamp.from(now.plus(policy.requestDuration)));
        audit.change(u.personId(),u.organizationId(),"PatientAccessRequest",id,"REQUEST",Map.of("patientId",patient,"reason",reason));
        return requests(patient).stream().filter(x->id.equals(x.get("id"))).findFirst().orElseThrow();
    }
    List<Map<String,Object>> requests(UUID patient) {
        var u=users.current(); requirePatient(patient);
        boolean patientOwner = owner(patient,u);
        if (!patientOwner) staff(false);
        jdbc.update("update patient.access_request set status='EXPIRED' where patient_id=? and status='PENDING' and expires_at<=clock_timestamp()",patient);
        if(patientOwner) return jdbc.queryForList("select r.*, i.first_name || ' ' || i.last_name requester_name, o.name organization_name from patient.access_request r join identity.person i on i.id=r.requester_person_id join organization.organization o on o.id=r.organization_id where r.patient_id=? order by r.created_at desc",patient);
        staff(false);
        return jdbc.queryForList("select r.*, i.first_name || ' ' || i.last_name requester_name, o.name organization_name from patient.access_request r join identity.person i on i.id=r.requester_person_id join organization.organization o on o.id=r.organization_id where r.patient_id=? and r.requester_person_id=? and r.organization_id=? order by r.created_at desc",patient,u.personId(),u.organizationId());
    }
    Map<String,Object> accept(UUID patient,UUID request) {
        var u=users.current(); requireOwner(patient,u);
        var r=lockedRequest(patient,request);
        if(!"PENDING".equals(r.get("status")) || !((Timestamp)r.get("expires_at")).toInstant().isAfter(Instant.now())) throw new org.novasos.healthysv2.shared.api.error.BusinessRuleException("ACCESS_REQUEST_NOT_PENDING", "Request is no longer pending or has expired");
        Instant now=Instant.now(); UUID id=UUID.randomUUID();
        jdbc.update("insert into patient.access_grant values (?,?,?,?,?,?,'PATIENT_ACCEPTED',?,?,?,null)",id,patient,r.get("requester_person_id"),r.get("organization_id"),request,r.get("scope"),r.get("reason"),Timestamp.from(now),Timestamp.from(now.plus(policy.requestDuration)));
        jdbc.update("update patient.access_request set status='ACCEPTED' where id=?",request);
        jdbc.update("delete from patient.access_revocation where patient_id=? and person_id=? and organization_id=?",patient,r.get("requester_person_id"),r.get("organization_id"));
        audit.change(u.personId(),(UUID)r.get("organization_id"),"PatientAccessGrant",id,"ACCEPT",Map.of("patientId",patient));
        return grant(id);
    }
    void revokeRequest(UUID patient,UUID request) {
        var u=users.current(); requireOwner(patient,u); var r=lockedRequest(patient,request);
        jdbc.update("update patient.access_request set status='REVOKED' where id=?",request);
        jdbc.update("update patient.access_grant set revoked_at=clock_timestamp() where request_id=? and revoked_at is null",request);
        audit.change(u.personId(),(UUID)r.get("organization_id"),"PatientAccessRequest",request,"REVOKE",Map.of("patientId",patient));
    }
    Map<String,Object> emergency(UUID patient,String reason) {
        var u=staff(true); requirePatient(patient); UUID id=UUID.randomUUID(); Instant now=Instant.now();
        jdbc.update("insert into patient.access_grant values (?,?,?,?,null,'FULL_RECORD','EMERGENCY',?,?,?,null)",id,patient,u.personId(),u.organizationId(),reason,Timestamp.from(now),Timestamp.from(now.plus(policy.emergencyDuration)));
        audit.change(u.personId(),u.organizationId(),"PatientAccessGrant",id,"EMERGENCY",Map.of("patientId",patient,"reason",reason));
        return grant(id);
    }
    List<Map<String,Object>> grants(UUID patient) {
        var u=users.current(); requirePatient(patient);
        if(owner(patient,u)) return jdbc.queryForList("select g.*, i.first_name || ' ' || i.last_name person_name, o.name organization_name from patient.access_grant g join identity.person i on i.id=g.person_id join organization.organization o on o.id=g.organization_id where g.patient_id=? order by g.granted_at desc",patient);
        staff(false);
        return jdbc.queryForList("select g.*, i.first_name || ' ' || i.last_name person_name, o.name organization_name from patient.access_grant g join identity.person i on i.id=g.person_id join organization.organization o on o.id=g.organization_id where g.patient_id=? and g.person_id=? and g.organization_id=? order by g.granted_at desc",patient,u.personId(),u.organizationId());
    }
    void revokeGrant(UUID patient,UUID id) {
        var u=users.current(); requireOwner(patient,u); var g=grant(id);
        if(!patient.equals(g.get("patient_id"))) throw new ResourceNotFoundException("AccessGrant",id);
        jdbc.update("update patient.access_grant set revoked_at=clock_timestamp() where id=? and revoked_at is null",id);
        audit.change(u.personId(),(UUID)g.get("organization_id"),"PatientAccessGrant",id,"REVOKE",Map.of("patientId",patient));
    }
    List<Map<String,Object>> contexts(UUID patient) {
        var user=users.current();
        requireOwner(patient,user);
        return jdbc.queryForList("""
                select m.person_id, i.first_name || ' ' || i.last_name person_name,
                       e.organization_id, o.name organization_name, r.revoked_at,
                       max(case when e.status='ACTIVE' then greatest(e.expires_at, v.scheduled_at + ? * interval '1 second')
                           else v.scheduled_at + ? * interval '1 second' end) expires_at
                from care.care_episode e
                join care.care_visit v on v.episode_id=e.id and v.status<>'CANCELLED'
                join professional.professional doctor on doctor.id=v.professional_id
                join licensing.member m on m.organization_id=e.organization_id and m.status='ACTIVE'
                    and (m.role='NURSE' or (m.role='DOCTOR' and m.person_id=doctor.person_id))
                join identity.person i on i.id=m.person_id and i.status='ACTIVE'
                join professional.professional p on p.person_id=m.person_id and p.status='ACTIVE'
                    and ((m.role='NURSE' and p.professional_type='NURSE')
                         or (m.role='DOCTOR' and p.professional_type in ('DOCTOR','MEDECIN')))
                join organization.organization o on o.id=e.organization_id and o.status='ACTIVE'
                join licensing.subscription s on s.organization_id=e.organization_id and s.status='active'
                    and s.period_start<=clock_timestamp() and s.period_end>clock_timestamp()
                left join patient.access_revocation r on r.patient_id=e.patient_id
                    and r.person_id=m.person_id and r.organization_id=e.organization_id
                where e.patient_id=? and exists(select 1 from professional.professional_assignment a
                    where a.professional_id=p.id and a.organization_id=e.organization_id and a.status='ACTIVE'
                    and a.start_date<=current_date and (a.end_date is null or a.end_date>=current_date))
                    and ((e.status='ACTIVE' and e.started_at<=clock_timestamp() and e.expires_at>clock_timestamp())
                    or (e.status in ('READY','ACTIVE') and v.status='SCHEDULED'
                        and v.scheduled_at + ? * interval '1 second'>clock_timestamp()))
                group by m.person_id,i.first_name,i.last_name,e.organization_id,o.name,r.revoked_at
                order by o.name,i.last_name,i.first_name
                """,policy.scheduledGrace.getSeconds(),policy.scheduledGrace.getSeconds(),patient,policy.scheduledGrace.getSeconds());
    }

    void revokeContext(UUID patient,UUID person,UUID organization) {
        var u=users.current(); requireOwner(patient,u);
        jdbc.update("insert into patient.access_revocation values (?,?,?,clock_timestamp()) on conflict (patient_id,person_id,organization_id) do update set revoked_at=excluded.revoked_at",patient,person,organization);
        jdbc.update("update patient.access_grant set revoked_at=clock_timestamp() where patient_id=? and person_id=? and organization_id=? and revoked_at is null and source<>'EMERGENCY'",patient,person,organization);
        audit.change(u.personId(),organization,"PatientAccessRevocation",patient,"REVOKE",Map.of("personId",person,"organizationId",organization));
    }
    private CurrentUserContext.UserContext staff(boolean emergency) {
        var u=users.current();
        if(u.personId()==null || u.organizationId()==null || !u.hasAny(emergency?new String[]{"NURSE","HOSPITAL_ADMIN"}:new String[]{"DOCTOR","NURSE","HOSPITAL_ADMIN"})) throw new AccessDeniedException("Licensed care staff required");
        licensing.requireMember(u.organizationId()); licensing.requireActive(u.organizationId()); return u;
    }
    private boolean owner(UUID patient,CurrentUserContext.UserContext u) {
        return u.personId()!=null && Integer.valueOf(1).equals(jdbc.queryForObject("select count(*) from patient.patient where id=? and person_id=?",Integer.class,patient,u.personId()));
    }
    private void requireOwner(UUID patient,CurrentUserContext.UserContext u) {if(!owner(patient,u)) throw new AccessDeniedException("Patient owner required");}
    private void requirePatient(UUID patient) {if(!Integer.valueOf(1).equals(jdbc.queryForObject("select count(*) from patient.patient where id=?",Integer.class,patient))) throw new ResourceNotFoundException("Patient",patient);}
    private Map<String,Object> lockedRequest(UUID patient,UUID id) {var rows=jdbc.queryForList("select * from patient.access_request where id=? and patient_id=? for update",id,patient);if(rows.isEmpty()) throw new ResourceNotFoundException("AccessRequest",id);return rows.getFirst();}
    private Map<String,Object> grant(UUID id) {var rows=jdbc.queryForList("select * from patient.access_grant where id=?",id);if(rows.isEmpty()) throw new ResourceNotFoundException("AccessGrant",id);return rows.getFirst();}
}
