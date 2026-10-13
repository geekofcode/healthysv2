package org.novasos.healthysv2.patient;

import static org.novasos.healthysv2.patient.api.PatientIdentityDtos.*;

import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import java.net.URI;
import java.util.UUID;
import org.novasos.healthysv2.shared.api.ApiPaths;
import org.novasos.healthysv2.shared.api.dto.PageResponse;
import org.springframework.data.domain.Pageable;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping(ApiPaths.V1 + "/patients")
@PreAuthorize("hasAnyRole('PLATFORM_ADMIN','HOSPITAL_ADMIN','HOSPITAL_AGENT','NURSE','DOCTOR')")
@Tag(name = "Patient identities", description = "Platform identities, independent of clinical access")
class PatientIdentityController {
    private final PatientIdentityService service;
    PatientIdentityController(PatientIdentityService service) { this.service = service; }

    @GetMapping("/identities")
    PageResponse<PatientIdentityResponse> search(@RequestParam(defaultValue = "") String query, Pageable pageable) {
        return service.search(query, pageable);
    }

    @GetMapping("/person-identities")
    PageResponse<PersonIdentityResponse> persons(@RequestParam(defaultValue = "") String query, Pageable pageable) {
        return service.searchPersons(query, pageable);
    }

    @GetMapping("/{id}/identity")
    PatientIdentityResponse find(@PathVariable UUID id) { return service.find(id); }

    @PutMapping("/{id}/identity")
    PatientIdentityResponse update(@PathVariable UUID id,
            @Valid @RequestBody org.novasos.healthysv2.identity.api.ReceptionIdentityUpdate request) {
        return service.update(id, request);
    }

    @PostMapping("/identities")
    ResponseEntity<PatientIdentityResponse> create(@Valid @RequestBody CreateIdentityRequest request) {
        var result = service.create(request);
        return ResponseEntity.created(URI.create(ApiPaths.V1 + "/patients/" + result.id() + "/identity")).body(result);
    }
}
