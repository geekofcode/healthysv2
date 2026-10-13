package org.novasos.healthysv2.prescription;
import static org.novasos.healthysv2.prescription.api.CareVisitPrescriptionDtos.*;
import jakarta.validation.Valid;
import java.util.UUID;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;
@RestController
@RequestMapping("/api/v1/care/visits/{visitId}/prescription")
@PreAuthorize("hasAnyRole('DOCTOR','NURSE','PATIENT')")
class CareVisitPrescriptionController {
    private final CareVisitPrescriptionService service;
    CareVisitPrescriptionController(CareVisitPrescriptionService service){this.service=service;}
    @GetMapping VisitPrescriptionResponse read(@PathVariable UUID visitId){return service.read(visitId);}
    @GetMapping("/revisions") java.util.List<Revision> revisions(@PathVariable UUID visitId){return service.revisions(visitId);}
    @PutMapping @PreAuthorize("hasRole('DOCTOR')") VisitPrescriptionResponse save(@PathVariable UUID visitId,@Valid @RequestBody SaveRequest request){return service.save(visitId,request);}
    @PostMapping("/finalize") @PreAuthorize("hasRole('DOCTOR')") VisitPrescriptionResponse finalizePrescription(@PathVariable UUID visitId,@Valid @RequestBody VersionRequest request){return service.finalizePrescription(visitId,request.expectedVersion());}
    @PostMapping("/corrections") @PreAuthorize("hasRole('DOCTOR')") VisitPrescriptionResponse correction(@PathVariable UUID visitId,@Valid @RequestBody CorrectionRequest request){return service.correction(visitId,request);}
}
