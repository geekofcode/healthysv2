package org.novasos.healthysv2.audit.api;

import com.fasterxml.jackson.databind.JsonNode;
import java.time.*;
import java.util.*;

public final class AuditDtos {
    private AuditDtos(){}
    public record AuditLogResponse(UUID id,UUID actorPersonId,UUID organizationId,String module,String entityType,
                                   UUID entityId,String action,JsonNode oldValue,JsonNode newValue,String correlationId,
                                   String ipAddress,String userAgent,Instant occurredAt){}
    public record DataAccessLogResponse(UUID id,UUID actorPersonId,UUID patientId,UUID organizationId,
                                        String resourceType,UUID resourceId,String action,String accessReason,
                                        JsonNode accessContext,String ipAddress,String correlationId,Instant occurredAt){}
    public record AuthenticationLogResponse(UUID id,UUID keycloakUserId,UUID personId,String eventType,
                                             boolean success,String ipAddress,String userAgent,JsonNode details,
                                             Instant occurredAt){}
    public record SecurityEventResponse(UUID id,UUID actorPersonId,String eventType,String severity,
                                        String description,JsonNode details,String ipAddress,Instant occurredAt,
                                        Instant resolvedAt){}
    public record PlatformOverview(long organizations,long patients,long professionals,long activeAppointments,
                                   long openInvoices,long unreadNotifications,long auditEventsToday,
                                   long deniedAccessesToday,long unresolvedSecurityEvents){}
    public record DailyAuditCount(LocalDate date,long changes,long dataAccesses,long deniedAccesses,long authFailures){}
    public record AuditSecurityDashboard(int days,long totalChanges,long totalDataAccesses,long deniedAccesses,
                                         long authenticationFailures,long unresolvedSecurityEvents,
                                         List<DailyAuditCount> daily,List<SecurityEventResponse> recentSecurityEvents){}
}
