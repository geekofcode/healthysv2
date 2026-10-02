package org.novasos.healthysv2.document;

import static org.novasos.healthysv2.document.api.PatientDocumentDtos.*;
import java.nio.charset.StandardCharsets;
import java.util.UUID;
import io.swagger.v3.oas.annotations.Operation;
import org.novasos.healthysv2.shared.api.dto.PageResponse;
import org.novasos.healthysv2.shared.api.error.BusinessRuleException;
import org.springframework.core.io.InputStreamResource;
import org.springframework.http.*;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/v1/patients/me/documents")
@PreAuthorize("hasRole('PATIENT')")
class PatientDocumentController {
    private final PatientDocumentService service;
    PatientDocumentController(PatientDocumentService service){this.service=service;}
    @GetMapping @Operation(operationId="getMyPatientDocuments")
    PageResponse<PatientDocumentMetadata> list(@AuthenticationPrincipal Jwt jwt,@RequestParam(required=false)UUID consultationId,
            @RequestParam(defaultValue="0")int page,@RequestParam(defaultValue="20")int size){return service.list(subject(jwt),consultationId,page,size);}
    @GetMapping("/{id}") @Operation(operationId="getMyPatientDocument")
    PatientDocumentMetadata detail(@AuthenticationPrincipal Jwt jwt,@PathVariable UUID id){return service.detail(subject(jwt),id);}
    @GetMapping("/{id}/content") @Operation(operationId="downloadMyPatientDocument")
    ResponseEntity<InputStreamResource> content(@AuthenticationPrincipal Jwt jwt,@PathVariable UUID id){
        var download=service.download(subject(jwt),id);
        return ResponseEntity.ok().contentType(MediaType.parseMediaType(download.contentType())).contentLength(download.size())
                .header(HttpHeaders.CONTENT_DISPOSITION,ContentDisposition.attachment().filename(safeName(download.fileName()),StandardCharsets.UTF_8).build().toString())
                .header(HttpHeaders.CACHE_CONTROL,"no-store").header(HttpHeaders.VARY,"Authorization")
                .header("X-Content-Type-Options","nosniff").body(new InputStreamResource(download.content()));
    }
    private String safeName(String name){return name==null?"document":name.replaceAll("[\\r\\n\\\\/]","_");}
    private UUID subject(Jwt jwt){try{return UUID.fromString(jwt.getSubject());}catch(IllegalArgumentException exception){throw new BusinessRuleException("INVALID_IDENTITY_SUBJECT","error.identity.subject.invalid");}}
}
