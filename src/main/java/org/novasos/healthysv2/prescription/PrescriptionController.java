package org.novasos.healthysv2.prescription;

import static org.novasos.healthysv2.prescription.api.PrescriptionDtos.CreatePrescriptionRequest;
import static org.novasos.healthysv2.prescription.api.PrescriptionDtos.PrescriptionResponse;
import static org.novasos.healthysv2.prescription.api.PrescriptionDtos.PrescriptionSummary;

import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import java.net.URI;
import java.util.UUID;
import org.novasos.healthysv2.shared.api.ApiPaths;
import org.novasos.healthysv2.shared.api.dto.PageResponse;
import org.springframework.data.domain.Pageable;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping(ApiPaths.V1 + "/prescriptions")
@Tag(name = "Prescriptions", description = "Prescription lifecycle and medication orders")
@PreAuthorize("hasAnyRole('PLATFORM_ADMIN','HOSPITAL_ADMIN','DOCTOR','NURSE','PHARMACIST','PATIENT')")
class PrescriptionController {
    private static final String PRESCRIBE = "hasAnyRole('PLATFORM_ADMIN','HOSPITAL_ADMIN','DOCTOR')";
    private final PrescriptionService service;

    PrescriptionController(PrescriptionService service) {
        this.service = service;
    }

    @PostMapping
    @PreAuthorize(PRESCRIBE)
    ResponseEntity<PrescriptionResponse> create(@Valid @RequestBody CreatePrescriptionRequest request) {
        var response = service.create(request);
        return ResponseEntity.created(URI.create(ApiPaths.V1 + "/prescriptions/" + response.id())).body(response);
    }

    @GetMapping("/{id}")
    PrescriptionResponse find(@PathVariable UUID id) {
        return service.find(id);
    }

    @GetMapping
    @PreAuthorize("hasAnyRole('PLATFORM_ADMIN','HOSPITAL_ADMIN','DOCTOR','NURSE','PHARMACIST')")
    PageResponse<PrescriptionSummary> search(@RequestParam(required = false) UUID patientId,
                                             @RequestParam(required = false) UUID organizationId,
                                             @RequestParam(required = false) String status,
                                             Pageable pageable) {
        return service.search(patientId, organizationId, status, pageable);
    }

    @PostMapping("/{id}/cancel")
    @PreAuthorize(PRESCRIBE)
    PrescriptionResponse cancel(@PathVariable UUID id) {
        return service.cancel(id);
    }
}
