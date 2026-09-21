package org.novasos.healthysv2.patient;

import com.fasterxml.jackson.core.JsonProcessingException; import com.fasterxml.jackson.databind.ObjectMapper;
import java.util.*; import org.springframework.jdbc.core.JdbcTemplate; import org.springframework.stereotype.Service; import org.springframework.transaction.annotation.*;

@Service
class PatientAuditService {
 private final JdbcTemplate jdbc;private final ObjectMapper json=new ObjectMapper().findAndRegisterModules();PatientAuditService(JdbcTemplate jdbc){this.jdbc=jdbc;}
 void change(UUID actor,UUID organization,String entityType,UUID entityId,String action,Object value){jdbc.update("insert into audit.audit_log(actor_person_id,organization_id,module,entity_type,entity_id,action,new_value) values (?,?,?,?,?,?,?::jsonb)",actor,organization,"PATIENT",entityType,entityId,action,write(value));}
 @Transactional(propagation=Propagation.REQUIRES_NEW) void access(UUID actor,UUID patient,UUID organization,String resource,UUID resourceId,String action,String reason,Object context){jdbc.update("insert into audit.data_access_log(actor_person_id,patient_id,organization_id,resource_type,resource_id,action,access_reason,access_context) values (?,?,?,?,?,?,?,?::jsonb)",actor,patient,organization,resource,resourceId,action,reason,write(context));}
 private String write(Object value){try{return json.writeValueAsString(value);}catch(JsonProcessingException e){throw new IllegalStateException(e);}}
}
