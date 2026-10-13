package org.novasos.healthysv2.patient;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import java.util.*;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;
@RestController
@RequestMapping("/api/v1/patients/{patient}/")
@PreAuthorize("hasAnyRole('PATIENT','DOCTOR','NURSE','HOSPITAL_ADMIN')")
class TemporaryPatientAccessController {
 private final TemporaryPatientAccessService service;
 TemporaryPatientAccessController(TemporaryPatientAccessService service){this.service=service;}
 record AccessRequest(String scope,@NotBlank @Size(max=2000) String reason){}
 record RevokeContextRequest(@NotNull UUID personId,@NotNull UUID organizationId){}
 @PostMapping("access-revocations") void revokeContext(@PathVariable UUID patient,@Valid @RequestBody RevokeContextRequest r){service.revokeContext(patient,r.personId(),r.organizationId());}
 record EmergencyRequest(@NotBlank @Size(max=2000) String reason){}
 @PostMapping("access-requests") Map<String,Object> request(@PathVariable UUID patient,@Valid @RequestBody AccessRequest r){return service.request(patient,r.scope(),r.reason());}
 @GetMapping("access-requests") List<Map<String,Object>> requests(@PathVariable UUID patient){return service.requests(patient);}
 @PostMapping("access-requests/{id}/accept") Map<String,Object> accept(@PathVariable UUID patient,@PathVariable UUID id){return service.accept(patient,id);}
 @PostMapping("access-requests/{id}/revoke") void revoke(@PathVariable UUID patient,@PathVariable UUID id){service.revokeRequest(patient,id);}
 @PostMapping("emergency-access") Map<String,Object> emergency(@PathVariable UUID patient,@Valid @RequestBody EmergencyRequest r){return service.emergency(patient,r.reason());}
 @GetMapping("access-contexts") List<Map<String,Object>> contexts(@PathVariable UUID patient){return service.contexts(patient);}
 @GetMapping("access-grants") List<Map<String,Object>> grants(@PathVariable UUID patient){return service.grants(patient);}
 @DeleteMapping("access-grants/{id}") void revokeGrant(@PathVariable UUID patient,@PathVariable UUID id){service.revokeGrant(patient,id);}
}
