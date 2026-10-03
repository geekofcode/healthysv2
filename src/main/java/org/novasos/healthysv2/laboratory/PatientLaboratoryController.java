package org.novasos.healthysv2.laboratory;
import static org.novasos.healthysv2.laboratory.api.PatientLaboratoryDtos.*;
import java.util.UUID;
import io.swagger.v3.oas.annotations.Operation;
import org.novasos.healthysv2.shared.api.dto.PageResponse;
import org.novasos.healthysv2.shared.api.error.BusinessRuleException;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.*;
@RestController
@RequestMapping("/api/v1/patients/me/lab-results")
@PreAuthorize("hasRole('PATIENT')")
class PatientLaboratoryController {
 private final PatientLaboratoryService service;
 PatientLaboratoryController(PatientLaboratoryService service){this.service=service;}
 @GetMapping @Operation(operationId="getMyPatientLaboratoryList")
 PageResponse<ResultSummary> list(@AuthenticationPrincipal Jwt jwt,@RequestParam(defaultValue="0")int page,@RequestParam(defaultValue="20")int size){return service.list(subject(jwt),page,size);}
 @GetMapping("/{id}") @Operation(operationId="getMyPatientLaboratory")
 ResultDetail detail(@AuthenticationPrincipal Jwt jwt,@PathVariable UUID id){return service.detail(subject(jwt),id);}
 private UUID subject(Jwt jwt){try{return UUID.fromString(jwt.getSubject());}catch(IllegalArgumentException exception){throw new BusinessRuleException("INVALID_IDENTITY_SUBJECT","error.identity.subject.invalid");}}
}
