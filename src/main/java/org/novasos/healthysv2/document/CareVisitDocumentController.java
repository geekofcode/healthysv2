package org.novasos.healthysv2.document;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.NotBlank;
import java.util.List;
import java.util.UUID;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;
@RestController
@RequestMapping("/api/v1/care/visits/{visitId}/documents")
@PreAuthorize("hasAnyRole('DOCTOR','NURSE','PATIENT')")
class CareVisitDocumentController {
    private final CareVisitDocumentService service;
    CareVisitDocumentController(CareVisitDocumentService service){this.service=service;}
    @GetMapping List<CareVisitDocumentService.VisitDocument> list(@PathVariable UUID visitId){return service.list(visitId);}
    @PostMapping @PreAuthorize("hasRole('DOCTOR')") CareVisitDocumentService.VisitDocument generate(@PathVariable UUID visitId,@Valid @RequestBody GenerateRequest request){return service.generate(visitId,request.type());}
    record GenerateRequest(@NotBlank @Pattern(regexp="REPORT|PRESCRIPTION") String type) {}
}
