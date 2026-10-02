package org.novasos.healthysv2.consultation;

import static org.novasos.healthysv2.consultation.api.PatientConsultationDtos.*;
import java.util.*;
import org.novasos.healthysv2.audit.AuditTrail;
import org.novasos.healthysv2.identity.api.PersonLookup;
import org.novasos.healthysv2.consultation.api.ConsultationDtos.DiagnosisResponse;
import org.novasos.healthysv2.shared.api.dto.PageResponse;
import org.novasos.healthysv2.shared.api.error.*;
import org.springframework.data.domain.*;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@Transactional(readOnly = true)
class PatientConsultationService {
    private final PersonLookup identities;
    private final ConsultationRepository consultations;
    private final JdbcTemplate jdbc;
    private final AuditTrail audit;

    PatientConsultationService(PersonLookup identities, ConsultationRepository consultations, JdbcTemplate jdbc, AuditTrail audit) {
        this.identities=identities; this.consultations=consultations; this.jdbc=jdbc; this.audit=audit;
    }

    PageResponse<PatientConsultationSummary> list(UUID subject,int page,int size) {
        Self self=self(subject);
        if(page<0 || size<1 || size>100) throw new BusinessRuleException("INVALID_PAGE","error.malformed");
        var result=PageResponse.from(consultations.findByPatientIdAndStatus(self.patient(),"COMPLETED",
                PageRequest.of(page,size,Sort.by(Sort.Direction.DESC,"completedAt","id"))).map(this::summary));
        accessed(self,null,"LIST");
        return result;
    }

    PatientConsultationDetail detail(UUID subject,UUID id) {
        Self self=self(subject);
        var consultation=consultations.findById(id).orElseThrow(()->new ResourceNotFoundException("Consultation",id));
        if(!self.patient().equals(consultation.getPatientId())) {
            accessed(self,id,"DENIED");
            throw new AccessDeniedException("Consultation belongs to another patient");
        }
        if(!"COMPLETED".equals(consultation.getStatus())) throw new ResourceNotFoundException("Completed consultation",id);
        var detail=new PatientConsultationDetail(summary(consultation),
                consultation.getDiagnoses().stream().filter(Diagnosis::isPatientVisible).map(this::diagnosis).toList(),
                consultation.getNotes().stream().filter(ConsultationNote::isPatientVisible).map(note->new SharedConsultationNote(
                        note.getId(),note.getType(),note.getContent(),note.getCreatedAt(),note.getUpdatedAt())).toList());
        accessed(self,id,"READ");
        return detail;
    }

    private Self self(UUID subject) {
        var person=identities.findMe(subject);
        UUID patient=jdbc.query("select id from patient.patient where person_id=?",
                rs->rs.next()?rs.getObject(1,UUID.class):null,person.id());
        if(patient==null) throw new ResourceNotFoundException("Patient for person",person.id());
        return new Self(person.id(),patient);
    }

    private PatientConsultationSummary summary(Consultation consultation) {
        String professionalName=jdbc.query("select concat_ws(' ',i.first_name,i.last_name) from professional.professional p join identity.person i on i.id=p.person_id where p.id=?",
                rs->rs.next()?rs.getString(1):null,consultation.getProfessionalId());
        String organizationName=jdbc.query("select name from organization.organization where id=?",
                rs->rs.next()?rs.getString(1):null,consultation.getOrganizationId());
        return new PatientConsultationSummary(consultation.getId(),consultation.getNumber(),consultation.getPatientId(),
                consultation.getProfessionalId(),professionalName,consultation.getOrganizationId(),organizationName,
                consultation.getType(),consultation.getStartedAt(),consultation.getCompletedAt(),consultation.getStatus());
    }

    private DiagnosisResponse diagnosis(Diagnosis diagnosis) {
        String[] catalog=diagnosis.getCatalogId()==null?null:jdbc.query("select code,label from catalog.diagnosis_catalog where id=?",
                rs->rs.next()?new String[]{rs.getString(1),rs.getString(2)}:null,diagnosis.getCatalogId());
        return new DiagnosisResponse(diagnosis.getId(),diagnosis.getCatalogId(),catalog==null?null:catalog[0],catalog==null?null:catalog[1],
                diagnosis.getType(),diagnosis.getDescription(),diagnosis.getStatus(),diagnosis.getDiagnosedAt());
    }

    private void accessed(Self self,UUID consultation,String action) {
        audit.access(self.person(),self.patient(),null,"CONSULTATION",consultation==null?self.patient():consultation,
                action,"PATIENT_SELF",Map.of("allowed",!"DENIED".equals(action)));
    }
    private record Self(UUID person,UUID patient) {}
}
