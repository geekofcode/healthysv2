package org.novasos.healthysv2.patient;

import static org.novasos.healthysv2.patient.api.PatientAuthorizationDtos.*;
import java.util.*; import jakarta.validation.Valid;
import io.swagger.v3.oas.annotations.Operation; import io.swagger.v3.oas.annotations.tags.Tag;
import org.novasos.healthysv2.shared.api.ApiPaths; import org.springframework.http.ResponseEntity; import org.springframework.security.access.prepost.PreAuthorize; import org.springframework.web.bind.annotation.*;

@RestController @RequestMapping(ApiPaths.V1+"/patients/{patient}")
@Tag(name="Patient authorization",description="Care relationships, consent lifecycle and access decisions")
@PreAuthorize("hasAnyRole('PLATFORM_ADMIN','HOSPITAL_ADMIN','PATIENT','DOCTOR','NURSE','PHARMACIST','LAB_TECHNICIAN')")
class PatientAuthorizationController {
 private static final String ADMIN="hasAnyRole('PLATFORM_ADMIN','HOSPITAL_ADMIN')";private final PatientAuthorizationService service;private final PatientAccessService access;
 PatientAuthorizationController(PatientAuthorizationService service,PatientAccessService access){this.service=service;this.access=access;}
 @GetMapping("/care-relationships") @PreAuthorize(ADMIN) List<CareRelationshipResponse> relationships(@PathVariable UUID patient){return service.relationships(patient);}
 @PostMapping("/care-relationships") @PreAuthorize(ADMIN) ResponseEntity<CareRelationshipResponse> createRelationship(@PathVariable UUID patient,@Valid @RequestBody CareRelationshipRequest r){return ResponseEntity.status(201).body(service.createRelationship(patient,r));}
 @PostMapping("/care-relationships/{id}/end") @PreAuthorize(ADMIN) CareRelationshipResponse endRelationship(@PathVariable UUID patient,@PathVariable UUID id,@Valid @RequestBody EndCareRelationshipRequest r){return service.endRelationship(patient,id,r);}
 @GetMapping("/consents") List<ConsentResponse> consents(@PathVariable UUID patient){return service.consents(patient);}
 @PostMapping("/consents") @Operation(summary="Grant consent") ResponseEntity<ConsentResponse> grant(@PathVariable UUID patient,@Valid @RequestBody GrantConsentRequest r){return ResponseEntity.status(201).body(service.grant(patient,r));}
 @PostMapping("/consents/{id}/revoke") @Operation(summary="Revoke consent") ConsentResponse revoke(@PathVariable UUID patient,@PathVariable UUID id,@Valid @RequestBody RevokeConsentRequest r){return service.revoke(patient,id,r);}
 @GetMapping("/access") AccessCheckResponse access(@PathVariable UUID patient,@RequestParam(defaultValue="MEDICAL_RECORD") String scope,@RequestParam(defaultValue="READ") String action){var result=access.requireAccess(patient,scope,action);return new AccessCheckResponse(patient,scope.toUpperCase(),action.toUpperCase(),result.allowed(),result.reason());}
}
