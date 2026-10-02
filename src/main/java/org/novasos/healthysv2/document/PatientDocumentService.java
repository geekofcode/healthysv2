package org.novasos.healthysv2.document;

import static org.novasos.healthysv2.document.api.PatientDocumentDtos.*;
import java.io.InputStream;
import java.util.*;
import org.novasos.healthysv2.audit.AuditTrail;
import org.novasos.healthysv2.identity.api.PersonLookup;
import org.novasos.healthysv2.shared.api.dto.PageResponse;
import org.novasos.healthysv2.shared.api.error.*;
import org.springframework.data.domain.PageRequest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@Transactional(readOnly=true)
class PatientDocumentService {
    private static final Set<String> TYPES=Set.of("application/pdf","image/jpeg","image/png","text/plain");
    private final PersonLookup identities;
    private final HealthDocumentRepository documents;
    private final DocumentStorage storage;
    private final JdbcTemplate jdbc;
    private final AuditTrail audit;

    PatientDocumentService(PersonLookup identities,HealthDocumentRepository documents,DocumentStorage storage,JdbcTemplate jdbc,AuditTrail audit){
        this.identities=identities;this.documents=documents;this.storage=storage;this.jdbc=jdbc;this.audit=audit;
    }

    PageResponse<PatientDocumentMetadata> list(UUID subject,UUID consultationId,int page,int size){
        Self self=self(subject);
        if(page<0||size<1||size>100)throw new BusinessRuleException("INVALID_PAGE","error.malformed");
        if(consultationId!=null) requireConsultation(self,consultationId);
        var result=PageResponse.from(documents.searchVisible(self.patient(),null,consultationId,PageRequest.of(page,size)).map(this::metadata));
        accessed(self,null,"LIST");
        return result;
    }

    PatientDocumentMetadata detail(UUID subject,UUID id){Self self=self(subject);var document=allowed(self,id);accessed(self,id,"READ");return metadata(document);}

    Download download(UUID subject,UUID id){
        Self self=self(subject);var document=allowed(self,id);
        if(!TYPES.contains(document.mimeType())||document.sizeBytes()==null||document.sizeBytes()<1||document.sizeBytes()>25L*1024*1024){
            throw new BusinessRuleException("DOCUMENT_TYPE_NOT_ALLOWED","error.document.type");
        }
        var object=storage.get(document.storageKey());
        if(object.size()!=document.sizeBytes()){
            try{object.content().close();}catch(java.io.IOException ignored){}
            throw new BusinessRuleException("DOCUMENT_CONTENT_MISMATCH","error.document.type");
        }
        try{accessed(self,id,"DOWNLOAD");}catch(RuntimeException exception){try{object.content().close();}catch(java.io.IOException ignored){}throw exception;}
        return new Download(object.content(),object.size(),document.mimeType(),document.fileName());
    }

    private Self self(UUID subject){
        var person=identities.findMe(subject);
        UUID patient=jdbc.query("select id from patient.patient where person_id=?",rs->rs.next()?rs.getObject(1,UUID.class):null,person.id());
        if(patient==null)throw new ResourceNotFoundException("Patient for person",person.id());
        return new Self(person.id(),patient);
    }
    private HealthDocument allowed(Self self,UUID id){
        var document=documents.findById(id).orElseThrow(()->new ResourceNotFoundException("Document",id));
        if(!self.patient().equals(document.patientId())){accessed(self,id,"DENIED");throw new AccessDeniedException("Document belongs to another patient");}
        if(!document.patientVisible()||!"ACTIVE".equals(document.status()))throw new ResourceNotFoundException("Published document",id);
        return document;
    }
    private void requireConsultation(Self self,UUID id){
        UUID patient=jdbc.query("select patient_id from consultation.consultation where id=? and status='COMPLETED'",rs->rs.next()?rs.getObject(1,UUID.class):null,id);
        if(patient==null)throw new ResourceNotFoundException("Completed consultation",id);
        if(!self.patient().equals(patient))throw new AccessDeniedException("Consultation belongs to another patient");
    }
    private PatientDocumentMetadata metadata(HealthDocument document){
        String[] category=document.categoryId()==null?null:jdbc.query("select code,name from document.document_category where id=?",rs->rs.next()?new String[]{rs.getString(1),rs.getString(2)}:null,document.categoryId());
        return new PatientDocumentMetadata(document.id(),document.number(),document.patientId(),document.categoryId(),category==null?null:category[0],category==null?null:category[1],
                document.fileName(),document.mimeType(),document.sizeBytes()==null?0:document.sizeBytes(),document.uploadedAt(),document.status());
    }
    private void accessed(Self self,UUID id,String action){audit.access(self.person(),self.patient(),null,"DOCUMENT",id==null?self.patient():id,action,"PATIENT_SELF",Map.of("allowed",!"DENIED".equals(action)));}
    private record Self(UUID person,UUID patient){}
    record Download(InputStream content,long size,String contentType,String fileName){}
}
