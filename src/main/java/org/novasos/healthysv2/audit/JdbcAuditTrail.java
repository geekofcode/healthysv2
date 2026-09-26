package org.novasos.healthysv2.audit;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.servlet.http.HttpServletRequest;
import java.util.UUID;
import org.novasos.healthysv2.shared.web.CorrelationIdFilter;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.*;
import org.springframework.web.context.request.*;

@Service
class JdbcAuditTrail implements AuditTrail {
    private final JdbcTemplate jdbc;
    private final ObjectMapper json;
    JdbcAuditTrail(JdbcTemplate jdbc,ObjectMapper json){this.jdbc=jdbc;this.json=json;}

    @Override @Transactional
    public void change(UUID actor,UUID organization,String module,String entityType,UUID entityId,
                       String action,Object oldValue,Object newValue){
        var request=request();
        jdbc.update("insert into audit.audit_log(actor_person_id,organization_id,module,entity_type,entity_id,action,old_value,new_value,correlation_id,ip_address,user_agent) values (?,?,?,?,?,?,?::jsonb,?::jsonb,?,?::inet,?)",
                actor,organization,normalize(module),required(entityType),entityId,normalize(action),write(oldValue),write(newValue),correlation(request),ip(request),userAgent(request));
    }

    @Override @Transactional(propagation=Propagation.REQUIRES_NEW)
    public void access(UUID actor,UUID patient,UUID organization,String resourceType,UUID resourceId,
                       String action,String reason,Object context){
        var request=request();
        jdbc.update("insert into audit.data_access_log(actor_person_id,patient_id,organization_id,resource_type,resource_id,action,access_reason,access_context,ip_address,correlation_id) values (?,?,?,?,?,?,?,?,?::inet,?)",
                actor,patient,organization,required(resourceType),resourceId,normalize(action),blank(reason),write(context),ip(request),correlation(request));
    }

    private HttpServletRequest request(){var attributes=RequestContextHolder.getRequestAttributes();return attributes instanceof ServletRequestAttributes servlet?servlet.getRequest():null;}
    private String correlation(HttpServletRequest request){if(request==null)return null;Object value=request.getAttribute(CorrelationIdFilter.REQUEST_ATTRIBUTE);return value==null?null:value.toString();}
    private String ip(HttpServletRequest request){if(request==null)return null;String value=request.getRemoteAddr();return value==null||value.isBlank()?null:value;}
    private String userAgent(HttpServletRequest request){return request==null?null:blank(request.getHeader("User-Agent"));}
    private String write(Object value){if(value==null)return null;try{return json.writeValueAsString(value);}catch(JsonProcessingException exception){throw new IllegalArgumentException("Audit value cannot be serialized",exception);}}
    private String required(String value){if(value==null||value.isBlank())throw new IllegalArgumentException("Audit value is required");return value.trim();}
    private String normalize(String value){return required(value).toUpperCase();}
    private String blank(String value){return value==null||value.isBlank()?null:value.trim();}
}
