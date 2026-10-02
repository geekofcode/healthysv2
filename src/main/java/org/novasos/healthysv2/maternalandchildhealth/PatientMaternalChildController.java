package org.novasos.healthysv2.maternalandchildhealth;
import static org.novasos.healthysv2.maternalandchildhealth.api.PatientMaternalChildDtos.*;
import java.util.UUID;
import io.swagger.v3.oas.annotations.Operation;
import org.novasos.healthysv2.maternalandchildhealth.api.MaternalChildDtos.PregnancySummary;
import org.novasos.healthysv2.shared.api.dto.PageResponse;
import org.novasos.healthysv2.shared.api.error.BusinessRuleException;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.*;
@RestController @RequestMapping("/api/v1/patients/me/maternal-child") @PreAuthorize("hasRole('PATIENT')")
class PatientMaternalChildController {
 private final PatientMaternalChildService service;
 PatientMaternalChildController(PatientMaternalChildService service){this.service=service;}
 @GetMapping("/pregnancies") @Operation(operationId="getMyPregnancies")
 PageResponse<PregnancySummary> pregnancies(@AuthenticationPrincipal Jwt jwt,@RequestParam(defaultValue="0")int page,@RequestParam(defaultValue="20")int size){return service.pregnancies(subject(jwt),page,size);}
 @GetMapping("/pregnancies/{id}") @Operation(operationId="getMyPregnancy")
 PregnancyDetail pregnancy(@AuthenticationPrincipal Jwt jwt,@PathVariable UUID id){return service.pregnancy(subject(jwt),id);}
 @GetMapping("/children") @Operation(operationId="getMyChildHealthRecords")
 PageResponse<ChildSummary> children(@AuthenticationPrincipal Jwt jwt,@RequestParam(defaultValue="0")int page,@RequestParam(defaultValue="20")int size){return service.children(subject(jwt),page,size);}
 @GetMapping("/children/{childPatientId}") @Operation(operationId="getMyChildHealthRecord")
 ChildDetail child(@AuthenticationPrincipal Jwt jwt,@PathVariable UUID childPatientId){return service.child(subject(jwt),childPatientId);}
 private UUID subject(Jwt jwt){try{return UUID.fromString(jwt.getSubject());}catch(IllegalArgumentException exception){throw new BusinessRuleException("INVALID_IDENTITY_SUBJECT","error.identity.subject.invalid");}}
}
