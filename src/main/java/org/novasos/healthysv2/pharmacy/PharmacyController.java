package org.novasos.healthysv2.pharmacy;

import static org.novasos.healthysv2.pharmacy.api.PharmacyDtos.*;

import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import java.util.*;
import org.novasos.healthysv2.shared.api.ApiPaths;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping(ApiPaths.V1)
@Tag(name = "Pharmacy", description = "Dispensing and medication stock")
@PreAuthorize("hasAnyRole('PLATFORM_ADMIN','HOSPITAL_ADMIN','DOCTOR','NURSE','PHARMACIST','PATIENT')")
class PharmacyController {
    private static final String PHARMACY = "hasAnyRole('PLATFORM_ADMIN','HOSPITAL_ADMIN','PHARMACIST')";
    private final PharmacyService service;

    PharmacyController(PharmacyService service) { this.service = service; }

    @PostMapping("/prescriptions/{id}/dispenses")
    @PreAuthorize(PHARMACY)
    ResponseEntity<DispenseResponse> dispense(@PathVariable UUID id,
                                              @Valid @RequestBody CreateDispenseRequest request) {
        return ResponseEntity.status(201).body(service.dispense(id, request));
    }

    @GetMapping("/prescriptions/{id}/dispenses")
    List<DispenseResponse> history(@PathVariable UUID id) { return service.history(id); }

    @PostMapping("/medication-stocks/replenish")
    @PreAuthorize(PHARMACY)
    MedicationStockResponse replenish(@Valid @RequestBody ReplenishStockRequest request) {
        return service.replenish(request);
    }

    @GetMapping("/medication-stocks")
    @PreAuthorize(PHARMACY)
    List<MedicationStockResponse> stock(@RequestParam(required = false) UUID organizationId,
                                        @RequestParam(required = false) UUID medicationCatalogId) {
        return service.stock(organizationId, medicationCatalogId);
    }

    @GetMapping("/medication-catalog")
    List<MedicationCatalogResponse> catalog(@RequestParam(required = false) String query) {
        return service.catalog(query);
    }
}
