package org.novasos.healthysv2.patient;

import java.util.UUID;
import org.novasos.healthysv2.audit.AuditTrail;
import org.springframework.stereotype.Service;

@Service
class PatientAuditService {
    private final AuditTrail audit;
    PatientAuditService(AuditTrail audit){this.audit=audit;}
    void change(UUID actor,UUID organization,String entityType,UUID entityId,String action,Object value){audit.change(actor,organization,"PATIENT",entityType,entityId,action,null,value);}
    void access(UUID actor,UUID patient,UUID organization,String resource,UUID resourceId,String action,String reason,Object context){audit.access(actor,patient,organization,resource,resourceId,action,reason,context);}
}
