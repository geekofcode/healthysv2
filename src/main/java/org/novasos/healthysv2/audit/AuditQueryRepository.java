package org.novasos.healthysv2.audit;

import static org.novasos.healthysv2.audit.api.AuditDtos.*;
import com.fasterxml.jackson.databind.*;
import java.sql.*;
import java.time.*;
import java.util.*;
import org.novasos.healthysv2.shared.api.dto.PageResponse;
import org.springframework.data.domain.Pageable;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;

@Repository
class AuditQueryRepository {
    private final JdbcTemplate jdbc;private final ObjectMapper json;
    AuditQueryRepository(JdbcTemplate jdbc,ObjectMapper json){this.jdbc=jdbc;this.json=json;}

    PageResponse<AuditLogResponse> auditLogs(String module,String action,UUID actor,UUID organization,Instant from,Instant to,Pageable pageable){
        var q=new Filter(" from audit.audit_log where 1=1");q.text("module",module);q.text("action",action);q.uuid("actor_person_id",actor);q.uuid("organization_id",organization);q.time("occurred_at",from,to);
        long total=count("select count(*)"+q.sql,q.args);var page=page(pageable,total);
        var args=new ArrayList<>(q.args);args.add(page.size);args.add(page.offset);
        var values=jdbc.query("select *"+q.sql+" order by occurred_at desc limit ? offset ?",this::audit,args.toArray());
        return response(values,page,total);
    }

    PageResponse<DataAccessLogResponse> accesses(UUID patient,UUID actor,String action,String resource,Instant from,Instant to,Pageable pageable){
        var q=new Filter(" from audit.data_access_log where 1=1");q.uuid("patient_id",patient);q.uuid("actor_person_id",actor);q.text("action",action);q.text("resource_type",resource);q.time("occurred_at",from,to);
        long total=count("select count(*)"+q.sql,q.args);var page=page(pageable,total);var args=new ArrayList<>(q.args);args.add(page.size);args.add(page.offset);
        return response(jdbc.query("select *"+q.sql+" order by occurred_at desc limit ? offset ?",this::access,args.toArray()),page,total);
    }

    PageResponse<AuthenticationLogResponse> authentications(Boolean success,String eventType,Instant from,Instant to,Pageable pageable){
        var q=new Filter(" from audit.authentication_log where 1=1");if(success!=null){q.sql+=" and success=?";q.args.add(success);}q.text("event_type",eventType);q.time("occurred_at",from,to);
        long total=count("select count(*)"+q.sql,q.args);var page=page(pageable,total);var args=new ArrayList<>(q.args);args.add(page.size);args.add(page.offset);
        return response(jdbc.query("select *"+q.sql+" order by occurred_at desc limit ? offset ?",this::authentication,args.toArray()),page,total);
    }

    List<SecurityEventResponse> securityEvents(Boolean resolved,int limit){
        String condition=resolved==null?"":resolved?" where resolved_at is not null":" where resolved_at is null";
        return jdbc.query("select * from audit.security_event"+condition+" order by occurred_at desc limit ?",this::security,Math.min(Math.max(limit,1),200));
    }

    List<DailyAuditCount> daily(int days){return jdbc.query("""
            select d::date as day,
              (select count(*) from audit.audit_log a where a.occurred_at>=d and a.occurred_at<d+interval '1 day') changes,
              (select count(*) from audit.data_access_log x where x.occurred_at>=d and x.occurred_at<d+interval '1 day') accesses,
              (select count(*) from audit.data_access_log x where x.occurred_at>=d and x.occurred_at<d+interval '1 day' and x.action='DENIED') denied,
              (select count(*) from audit.authentication_log x where x.occurred_at>=d and x.occurred_at<d+interval '1 day' and not x.success) failures
            from generate_series(current_date-(? * interval '1 day'),current_date,interval '1 day') d order by d
            """,(rs,n)->new DailyAuditCount(rs.getDate("day").toLocalDate(),rs.getLong("changes"),rs.getLong("accesses"),rs.getLong("denied"),rs.getLong("failures")),days-1);}

    long scalar(String sql,Object...args){Long value=jdbc.queryForObject(sql,Long.class,args);return value==null?0:value;}
    int resolveSecurityEvent(UUID id){return jdbc.update("update audit.security_event set resolved_at=coalesce(resolved_at,now()) where id=?",id);}
    SecurityEventResponse securityEvent(UUID id){return jdbc.query("select * from audit.security_event where id=?",rs->rs.next()?security(rs,0):null,id);}

    private AuditLogResponse audit(ResultSet rs,int n)throws SQLException{return new AuditLogResponse(uuid(rs,"id"),uuid(rs,"actor_person_id"),uuid(rs,"organization_id"),rs.getString("module"),rs.getString("entity_type"),uuid(rs,"entity_id"),rs.getString("action"),node(rs.getString("old_value")),node(rs.getString("new_value")),rs.getString("correlation_id"),rs.getString("ip_address"),rs.getString("user_agent"),instant(rs,"occurred_at"));}
    private DataAccessLogResponse access(ResultSet rs,int n)throws SQLException{return new DataAccessLogResponse(uuid(rs,"id"),uuid(rs,"actor_person_id"),uuid(rs,"patient_id"),uuid(rs,"organization_id"),rs.getString("resource_type"),uuid(rs,"resource_id"),rs.getString("action"),rs.getString("access_reason"),node(rs.getString("access_context")),rs.getString("ip_address"),rs.getString("correlation_id"),instant(rs,"occurred_at"));}
    private AuthenticationLogResponse authentication(ResultSet rs,int n)throws SQLException{return new AuthenticationLogResponse(uuid(rs,"id"),uuid(rs,"keycloak_user_id"),uuid(rs,"person_id"),rs.getString("event_type"),rs.getBoolean("success"),rs.getString("ip_address"),rs.getString("user_agent"),node(rs.getString("details")),instant(rs,"occurred_at"));}
    private SecurityEventResponse security(ResultSet rs,int n)throws SQLException{return new SecurityEventResponse(uuid(rs,"id"),uuid(rs,"actor_person_id"),rs.getString("event_type"),rs.getString("severity"),rs.getString("description"),node(rs.getString("details")),rs.getString("ip_address"),instant(rs,"occurred_at"),instant(rs,"resolved_at"));}
    private JsonNode node(String value){if(value==null)return null;try{return json.readTree(value);}catch(Exception exception){return json.getNodeFactory().textNode(value);}}
    private UUID uuid(ResultSet rs,String name)throws SQLException{Object value=rs.getObject(name);return value==null?null:(UUID)value;}
    private Instant instant(ResultSet rs,String name)throws SQLException{Timestamp value=rs.getTimestamp(name);return value==null?null:value.toInstant();}
    private long count(String sql,List<Object> args){return scalar(sql,args.toArray());}
    private Page page(Pageable pageable,long total){int size=Math.min(Math.max(pageable.getPageSize(),1),100),number=Math.max(pageable.getPageNumber(),0);return new Page(number,size,(long)number*size);}
    private <T> PageResponse<T> response(List<T> values,Page page,long total){int pages=(int)Math.ceil(total/(double)page.size);return new PageResponse<>(values,new PageResponse.PageMetadata(page.number,page.size,total,pages,page.number==0,page.number>=Math.max(pages-1,0)));}
    private record Page(int number,int size,long offset){}
    private static class Filter{String sql;final List<Object> args=new ArrayList<>();Filter(String sql){this.sql=sql;}void text(String column,String value){if(value!=null&&!value.isBlank()){sql+=" and "+column+"=?";args.add(value.trim().toUpperCase());}}void uuid(String column,UUID value){if(value!=null){sql+=" and "+column+"=?";args.add(value);}}void time(String column,Instant from,Instant to){if(from!=null){sql+=" and "+column+">=?";args.add(Timestamp.from(from));}if(to!=null){sql+=" and "+column+"<=?";args.add(Timestamp.from(to));}}}
}
