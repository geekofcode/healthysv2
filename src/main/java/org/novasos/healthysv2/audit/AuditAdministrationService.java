package org.novasos.healthysv2.audit;

import static org.novasos.healthysv2.audit.api.AuditDtos.*;
import java.time.Instant;
import java.util.*;
import org.novasos.healthysv2.shared.api.dto.PageResponse;
import org.novasos.healthysv2.shared.api.error.ResourceNotFoundException;
import org.springframework.data.domain.Pageable;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationToken;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@Transactional(readOnly=true)
class AuditAdministrationService {
    private final AuditQueryRepository repository;private final AuditTrail trail;private final JdbcTemplate jdbc;
    AuditAdministrationService(AuditQueryRepository repository,AuditTrail trail,JdbcTemplate jdbc){this.repository=repository;this.trail=trail;this.jdbc=jdbc;}

    PageResponse<AuditLogResponse> auditLogs(String module,String action,UUID actor,UUID organization,Instant from,Instant to,Pageable pageable){return repository.auditLogs(module,action,actor,organization,from,to,pageable);}
    PageResponse<DataAccessLogResponse> accesses(UUID patient,UUID actor,String action,String resource,Instant from,Instant to,Pageable pageable){return repository.accesses(patient,actor,action,resource,from,to,pageable);}
    PageResponse<AuthenticationLogResponse> authentications(Boolean success,String eventType,Instant from,Instant to,Pageable pageable){return repository.authentications(success,eventType,from,to,pageable);}
    List<SecurityEventResponse> securityEvents(Boolean resolved,int limit){return repository.securityEvents(resolved,limit);}

    PlatformOverview overview(){return new PlatformOverview(
            repository.scalar("select count(*) from organization.organization"),
            repository.scalar("select count(*) from patient.patient"),
            repository.scalar("select count(*) from professional.professional"),
            repository.scalar("select count(*) from appointment.appointment where status in ('SCHEDULED','CONFIRMED','CHECKED_IN','IN_PROGRESS')"),
            repository.scalar("select count(*) from billing.invoice where status in ('ISSUED','PARTIALLY_PAID')"),
            repository.scalar("select count(*) from notification.notification_recipient where read_at is null"),
            repository.scalar("select count(*) from audit.audit_log where occurred_at>=current_date"),
            repository.scalar("select count(*) from audit.data_access_log where occurred_at>=current_date and action='DENIED'"),
            repository.scalar("select count(*) from audit.security_event where resolved_at is null"));}

    AuditSecurityDashboard dashboard(int requestedDays){int days=Math.min(Math.max(requestedDays,1),30);var daily=repository.daily(days);return new AuditSecurityDashboard(days,daily.stream().mapToLong(DailyAuditCount::changes).sum(),daily.stream().mapToLong(DailyAuditCount::dataAccesses).sum(),daily.stream().mapToLong(DailyAuditCount::deniedAccesses).sum(),daily.stream().mapToLong(DailyAuditCount::authFailures).sum(),repository.scalar("select count(*) from audit.security_event where resolved_at is null"),daily,repository.securityEvents(false,10));}

    @Transactional
    SecurityEventResponse resolve(UUID id){var before=repository.securityEvent(id);if(before==null)throw new ResourceNotFoundException("SecurityEvent",id);repository.resolveSecurityEvent(id);var after=repository.securityEvent(id);trail.change(actor(),null,"AUDIT","SecurityEvent",id,"RESOLVE_SECURITY_EVENT",before,after);return after;}

    private UUID actor(){var authentication=SecurityContextHolder.getContext().getAuthentication();if(!(authentication instanceof JwtAuthenticationToken jwt))return null;try{UUID subject=UUID.fromString(jwt.getToken().getSubject());return jdbc.query("select id from identity.person where keycloak_user_id=? or id=? limit 1",rs->rs.next()?(UUID)rs.getObject(1):null,subject,subject);}catch(IllegalArgumentException exception){return null;}}
}
