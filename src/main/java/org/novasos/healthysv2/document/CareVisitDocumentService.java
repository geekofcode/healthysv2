package org.novasos.healthysv2.document;

import java.io.ByteArrayInputStream;
import java.security.*;
import java.time.Instant;
import java.util.*;
import org.novasos.healthysv2.care.api.VisitReportReader;
import org.novasos.healthysv2.licensing.api.LicensingAccess;
import org.novasos.healthysv2.patient.CurrentUserContext;
import org.novasos.healthysv2.patient.PatientAccessService;
import org.novasos.healthysv2.prescription.api.CareVisitPrescriptionReader;
import org.novasos.healthysv2.shared.api.error.*;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.*;

@Service
@Transactional
class CareVisitDocumentService {
    private final JdbcTemplate jdbc;private final CurrentUserContext users;private final PatientAccessService access;
    private final LicensingAccess licensing;private final VisitReportReader reports;
    private final CareVisitPrescriptionReader prescriptions;private final CareVisitPdf pdf;
    private final DocumentStorage storage;private final HealthDocumentRepository documents;
    CareVisitDocumentService(JdbcTemplate jdbc,CurrentUserContext users,PatientAccessService access,
            LicensingAccess licensing,VisitReportReader reports,CareVisitPrescriptionReader prescriptions,
            CareVisitPdf pdf,DocumentStorage storage,HealthDocumentRepository documents) {
        this.jdbc=jdbc;this.users=users;this.access=access;this.licensing=licensing;this.reports=reports;
        this.prescriptions=prescriptions;this.pdf=pdf;this.storage=storage;this.documents=documents;
    }
    @Transactional(readOnly=true)
    List<VisitDocument> list(UUID visitId) {
        var visit=visit(visitId,false);var user=users.current();
        if(user.organizationId()!=null&&!user.organizationId().equals(visit.organization()))throw new AccessDeniedException("Visit belongs to another establishment");
        access.requireAccess(visit.patient(),"DOCUMENTS","READ");
        boolean patientOnly=user.has("PATIENT")&&!clinicalReader(visit,user);
        return jdbc.query("""
            select vd.document_id,vd.visit_id,vd.document_type,vd.report_revision,vd.prescription_revision,d.file_name,vd.created_at
            from care.visit_document vd join document.document d on d.id=vd.document_id
            where vd.visit_id=? and d.status='ACTIVE' and (not ? or d.patient_visible=true)
            order by vd.created_at desc,vd.document_id
            """,(rs,n)->response(rs),visitId,patientOnly);
    }
    VisitDocument generate(UUID visitId,String type) {
        if(!Set.of("REPORT","PRESCRIPTION").contains(type))throw new BusinessRuleException("INVALID_DOCUMENT_TYPE","Choose REPORT or PRESCRIPTION");
        var actor=users.current();
        if(!actor.has("DOCTOR")||actor.personId()==null||actor.organizationId()==null)
            throw new AccessDeniedException("Selected establishment doctor context required");
        jdbc.queryForObject("select id from organization.organization where id=? for update",UUID.class,actor.organizationId());
        var visit=visit(visitId,true);
        if(!actor.has("DOCTOR")||!visit.doctorPerson().equals(actor.personId())||!visit.organization().equals(actor.organizationId()))
            throw new AccessDeniedException("Only the assigned establishment doctor may generate medical documents");
        licensing.requireMember(visit.organization());licensing.requireActive(visit.organization());
        requireActiveDoctor(visitId,visit.organization(),actor.personId());
        access.requireAccess(visit.patient(),"DOCUMENTS","WRITE");
        var report=reports.validatedReport(visitId);
        var prescription="PRESCRIPTION".equals(type)?prescriptions.validatedPrescription(visitId):null;
        int prescriptionRevision=prescription==null?0:prescription.revision();
        var existing=jdbc.query("""
            select vd.document_id,vd.visit_id,vd.document_type,vd.report_revision,vd.prescription_revision,d.file_name,vd.created_at
            from care.visit_document vd join document.document d on d.id=vd.document_id
            where vd.visit_id=? and vd.document_type=? and vd.report_revision=? and vd.prescription_revision=?
            """,(rs,n)->response(rs),visitId,type,report.revision(),prescriptionRevision);
        if(!existing.isEmpty())return existing.getFirst();
        byte[] content=prescription==null?pdf.report(report,visit.patientName(),visit.organizationName()):pdf.prescription(report,prescription,visit.patientName(),visit.organizationName());
        String name=(prescription==null?"rapport":"ordonnance")+"-"+visitId+"-r"+report.revision()+(prescription==null?"":"-p"+prescriptionRevision)+".pdf";
        String key="patients/"+visit.patient()+"/visits/"+visitId+"/"+UUID.randomUUID()+"/"+name;
        try {
            storage.put(key,new ByteArrayInputStream(content),content.length,"application/pdf");
            cleanupOnRollback(key);
            String checksum=HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(content));
            var document=HealthDocument.create(visit.patient(),null,name,key,"application/pdf",content.length,checksum,actor.personId());
            document.setPatientVisible(report.patientVisible());documents.saveAndFlush(document);
            jdbc.update("insert into document.document_link(document_id,resource_type,resource_id) values (?,'CARE_VISIT',?)",document.id(),visitId);
            jdbc.update("""
                insert into care.visit_document(document_id,visit_id,organization_id,document_type,report_revision_id,report_revision,prescription_revision_id,prescription_revision,retention_expires_at)
                values (?,?,?,?,?,?,?,?,?)
                """,document.id(),visitId,visit.organization(),type,report.revisionId(),report.revision(),prescription==null?null:prescription.revisionId(),prescriptionRevision,visit.retention());
            jdbc.update("insert into audit.audit_log(actor_person_id,module,entity_type,entity_id,action,new_value) values (?,'DOCUMENT','CareVisitDocument',?,'GENERATED',?::jsonb)",actor.personId(),document.id(),"{\"visitId\":\""+visitId+"\",\"type\":\""+type+"\",\"reportRevision\":"+report.revision()+",\"prescriptionRevision\":"+prescriptionRevision+"}");
            return new VisitDocument(document.id(),visitId,type,report.revision(),prescriptionRevision,name,document.uploadedAt());
        }catch(NoSuchAlgorithmException e){safeDelete(key);throw new IllegalStateException("Cannot checksum medical document",e);}
        catch(RuntimeException e){safeDelete(key);throw e;}
    }
    private boolean clinicalReader(Visit visit,CurrentUserContext.UserContext user) {
        if(!hasClinicalContext(user,visit.organization()))return false;
        Integer active=jdbc.queryForObject("""
            select count(*) from licensing.member m
            join professional.professional p on p.person_id=m.person_id and p.status='ACTIVE'
            join identity.person person on person.id=m.person_id and person.status='ACTIVE'
            where m.organization_id=? and m.person_id=? and m.status='ACTIVE'
              and ((m.role='DOCTOR' and p.professional_type in ('DOCTOR','MEDECIN') and ?)
                or (m.role='NURSE' and p.professional_type='NURSE' and ?))
              and exists(select 1 from professional.professional_assignment a where a.professional_id=p.id
                and a.organization_id=m.organization_id and a.status='ACTIVE' and a.start_date<=current_date
                and (a.end_date is null or a.end_date>=current_date))
            """,Integer.class,user.organizationId(),user.personId(),user.has("DOCTOR"),user.has("NURSE"));
        if(active==null||active==0)return false;
        try{licensing.requireMember(visit.organization());licensing.requireActive(visit.organization());return true;}
        catch(AccessDeniedException denied){return false;}
    }
    static boolean hasClinicalContext(CurrentUserContext.UserContext user,UUID organization) {
        return user.personId()!=null&&organization.equals(user.organizationId())&&user.hasAny("DOCTOR","NURSE");
    }
    private void requireActiveDoctor(UUID visitId,UUID organization,UUID person) {
        Integer eligible=jdbc.queryForObject("""
            select count(*) from care.care_visit v join professional.professional p on p.id=v.professional_id
            where v.id=? and p.person_id=? and p.status='ACTIVE' and p.professional_type in ('DOCTOR','MEDECIN')
              and exists(select 1 from professional.professional_assignment a where a.professional_id=p.id
                and a.organization_id=? and a.status='ACTIVE' and a.start_date<=current_date and (a.end_date is null or a.end_date>=current_date))
              and exists(select 1 from licensing.member m where m.person_id=p.person_id and m.organization_id=? and m.status='ACTIVE' and m.role='DOCTOR')
            """,Integer.class,visitId,person,organization,organization);
        if(!Integer.valueOf(1).equals(eligible))throw new AccessDeniedException("Active assigned doctor required");
    }
    private Visit visit(UUID visitId,boolean lock) {
        var rows=jdbc.query("""
            select v.id,e.patient_id,e.organization_id,p.person_id,v.retention_expires_at,
                   patient_person.first_name||' '||patient_person.last_name as patient_name,o.name
            from care.care_visit v join care.care_episode e on e.id=v.episode_id
            join professional.professional p on p.id=v.professional_id
            join patient.patient patient on patient.id=e.patient_id
            join identity.person patient_person on patient_person.id=patient.person_id
            join organization.organization o on o.id=e.organization_id where v.id=?
            """+(lock?" for update of v":""),(rs,n)->new Visit(rs.getObject(1,UUID.class),rs.getObject(2,UUID.class),rs.getObject(3,UUID.class),rs.getObject(4,UUID.class),rs.getTimestamp(5).toInstant(),rs.getString(6),rs.getString(7)),visitId);
        if(rows.isEmpty())throw new ResourceNotFoundException("CareVisit",visitId);return rows.getFirst();
    }
    private VisitDocument response(java.sql.ResultSet rs) throws java.sql.SQLException {
        return new VisitDocument(rs.getObject(1,UUID.class),rs.getObject(2,UUID.class),rs.getString(3),rs.getLong(4),rs.getInt(5),rs.getString(6),rs.getTimestamp(7).toInstant());
    }
    private void cleanupOnRollback(String key) {
        if(TransactionSynchronizationManager.isSynchronizationActive())TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {
            @Override public void afterCompletion(int status){if(status!=STATUS_COMMITTED)safeDelete(key);}
        });
    }
    private void safeDelete(String key){try{storage.delete(key);}catch(RuntimeException ignored){}}
    record VisitDocument(UUID id,UUID visitId,String type,long reportRevision,int prescriptionRevision,String fileName,Instant createdAt) {}
    private record Visit(UUID id,UUID patient,UUID organization,UUID doctorPerson,Instant retention,String patientName,String organizationName) {}
}
