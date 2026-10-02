package org.novasos.healthysv2.audit;

import static org.novasos.healthysv2.audit.api.AuditDtos.*;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import java.time.Instant;
import java.util.*;
import org.novasos.healthysv2.shared.api.ApiPaths;
import org.novasos.healthysv2.shared.api.dto.PageResponse;
import org.springframework.data.domain.Pageable;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping(ApiPaths.V1+"/admin")
@PreAuthorize("hasRole('PLATFORM_ADMIN')")
@Tag(name="Platform administration",description="Platform overview, immutable audit trail and security monitoring")
class AuditAdministrationController {
    private final AuditAdministrationService service;
    AuditAdministrationController(AuditAdministrationService service){this.service=service;}

    @GetMapping("/overview") PlatformOverview overview(){return service.overview();}
    @GetMapping("/audit-dashboard") AuditSecurityDashboard dashboard(@RequestParam(defaultValue="7")int days){return service.dashboard(days);}
    @GetMapping("/audit-logs") @Operation(summary="Search immutable business audit events")
    PageResponse<AuditLogResponse> auditLogs(@RequestParam(required=false)String module,@RequestParam(required=false)String action,@RequestParam(required=false)UUID actorPersonId,@RequestParam(required=false)UUID organizationId,@RequestParam(required=false)@DateTimeFormat(iso=DateTimeFormat.ISO.DATE_TIME)Instant from,@RequestParam(required=false)@DateTimeFormat(iso=DateTimeFormat.ISO.DATE_TIME)Instant to,Pageable pageable){return service.auditLogs(module,action,actorPersonId,organizationId,from,to,pageable);}
    @GetMapping("/data-access-logs") @Operation(summary="Search sensitive patient data access decisions")
    PageResponse<DataAccessLogResponse> accesses(@RequestParam(required=false)UUID patientId,@RequestParam(required=false)UUID actorPersonId,@RequestParam(required=false)String action,@RequestParam(required=false)String resourceType,@RequestParam(required=false)@DateTimeFormat(iso=DateTimeFormat.ISO.DATE_TIME)Instant from,@RequestParam(required=false)@DateTimeFormat(iso=DateTimeFormat.ISO.DATE_TIME)Instant to,Pageable pageable){return service.accesses(patientId,actorPersonId,action,resourceType,from,to,pageable);}
    @GetMapping("/authentication-logs") PageResponse<AuthenticationLogResponse> authentications(@RequestParam(required=false)Boolean success,@RequestParam(required=false)String eventType,@RequestParam(required=false)@DateTimeFormat(iso=DateTimeFormat.ISO.DATE_TIME)Instant from,@RequestParam(required=false)@DateTimeFormat(iso=DateTimeFormat.ISO.DATE_TIME)Instant to,Pageable pageable){return service.authentications(success,eventType,from,to,pageable);}
    @GetMapping("/security-events") List<SecurityEventResponse> securityEvents(@RequestParam(required=false)Boolean resolved,@RequestParam(defaultValue="50")int limit){return service.securityEvents(resolved,limit);}
    @PostMapping("/security-events/{id}/resolve") SecurityEventResponse resolve(@PathVariable UUID id){return service.resolve(id);}
}
