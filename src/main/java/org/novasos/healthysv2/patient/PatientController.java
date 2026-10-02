package org.novasos.healthysv2.patient;

import static org.novasos.healthysv2.patient.api.PatientDtos.*;

import java.net.URI;
import java.util.UUID;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import org.novasos.healthysv2.shared.api.ApiPaths;
import org.novasos.healthysv2.shared.api.dto.PageResponse;
import org.springframework.data.domain.Pageable;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping(ApiPaths.V1 + "/patients")
@Tag(name = "Patients", description = "Patient administration, medical profile and registrations")
@PreAuthorize("hasAnyRole('PLATFORM_ADMIN','HOSPITAL_ADMIN','HOSPITAL_AGENT','DOCTOR','NURSE','PHARMACIST','LAB_TECHNICIAN')")
class PatientController {
    private static final String ADMIN_WRITE = "hasAnyRole('PLATFORM_ADMIN','HOSPITAL_ADMIN','HOSPITAL_AGENT')";
    private static final String CLINICAL_WRITE = "hasAnyRole('PLATFORM_ADMIN','HOSPITAL_ADMIN','DOCTOR','NURSE')";
    private final PatientService service;
    private final PatientAccessService access;

    PatientController(PatientService service, PatientAccessService access) { this.service = service; this.access = access; }

    @GetMapping @Operation(summary = "Search patients")
    PageResponse<PatientSummary> list(@RequestParam(defaultValue = "") String query, Pageable pageable) { return service.search(query, pageable); }
    @GetMapping("/{id}") PatientResponse get(@PathVariable UUID id) { access.requireAccess(id,"MEDICAL_RECORD","READ"); return service.find(id); }
    @PostMapping @PreAuthorize(ADMIN_WRITE)
    ResponseEntity<PatientResponse> create(@Valid @RequestBody PatientRequest request) {
        var result = service.create(request);
        return ResponseEntity.created(URI.create(ApiPaths.V1 + "/patients/" + result.id())).body(result);
    }
    @PutMapping("/{id}") @PreAuthorize(ADMIN_WRITE) PatientResponse update(@PathVariable UUID id, @Valid @RequestBody PatientUpdateRequest request) { return service.update(id, request); }
    @DeleteMapping("/{id}") @PreAuthorize(ADMIN_WRITE) ResponseEntity<Void> delete(@PathVariable UUID id) { service.delete(id); return ResponseEntity.noContent().build(); }

    @PostMapping("/{patient}/identifiers") @PreAuthorize(ADMIN_WRITE) ResponseEntity<IdentifierResponse> addIdentifier(@PathVariable UUID patient, @Valid @RequestBody IdentifierRequest r) { return created(service.addIdentifier(patient, r)); }
    @PutMapping("/{patient}/identifiers/{id}") @PreAuthorize(ADMIN_WRITE) IdentifierResponse updateIdentifier(@PathVariable UUID patient, @PathVariable UUID id, @Valid @RequestBody IdentifierRequest r) { return service.updateIdentifier(patient, id, r); }
    @DeleteMapping("/{patient}/identifiers/{id}") @PreAuthorize(ADMIN_WRITE) ResponseEntity<Void> deleteIdentifier(@PathVariable UUID patient, @PathVariable UUID id) { service.deleteIdentifier(patient, id); return noContent(); }
    @PostMapping("/{patient}/insurances") @PreAuthorize(ADMIN_WRITE) ResponseEntity<InsuranceResponse> addInsurance(@PathVariable UUID patient, @Valid @RequestBody InsuranceRequest r) { return created(service.addInsurance(patient, r)); }
    @PutMapping("/{patient}/insurances/{id}") @PreAuthorize(ADMIN_WRITE) InsuranceResponse updateInsurance(@PathVariable UUID patient, @PathVariable UUID id, @Valid @RequestBody InsuranceRequest r) { return service.updateInsurance(patient, id, r); }
    @DeleteMapping("/{patient}/insurances/{id}") @PreAuthorize(ADMIN_WRITE) ResponseEntity<Void> deleteInsurance(@PathVariable UUID patient, @PathVariable UUID id) { service.deleteInsurance(patient, id); return noContent(); }
    @PostMapping("/{patient}/registrations") @PreAuthorize(ADMIN_WRITE) ResponseEntity<RegistrationResponse> addRegistration(@PathVariable UUID patient, @Valid @RequestBody RegistrationRequest r) { return created(service.addRegistration(patient, r)); }
    @PutMapping("/{patient}/registrations/{id}") @PreAuthorize(ADMIN_WRITE) RegistrationResponse updateRegistration(@PathVariable UUID patient, @PathVariable UUID id, @Valid @RequestBody RegistrationRequest r) { return service.updateRegistration(patient, id, r); }
    @DeleteMapping("/{patient}/registrations/{id}") @PreAuthorize(ADMIN_WRITE) ResponseEntity<Void> deleteRegistration(@PathVariable UUID patient, @PathVariable UUID id) { service.deleteRegistration(patient, id); return noContent(); }

    @PostMapping("/{patient}/allergies") @PreAuthorize(CLINICAL_WRITE) ResponseEntity<AllergyResponse> addAllergy(@PathVariable UUID patient, @Valid @RequestBody AllergyRequest r) { return created(service.addAllergy(patient, r)); }
    @PutMapping("/{patient}/allergies/{id}") @PreAuthorize(CLINICAL_WRITE) AllergyResponse updateAllergy(@PathVariable UUID patient, @PathVariable UUID id, @Valid @RequestBody AllergyRequest r) { return service.updateAllergy(patient, id, r); }
    @DeleteMapping("/{patient}/allergies/{id}") @PreAuthorize(CLINICAL_WRITE) ResponseEntity<Void> deleteAllergy(@PathVariable UUID patient, @PathVariable UUID id) { service.deleteAllergy(patient, id); return noContent(); }
    @PostMapping("/{patient}/chronic-diseases") @PreAuthorize(CLINICAL_WRITE) ResponseEntity<ChronicDiseaseResponse> addDisease(@PathVariable UUID patient, @Valid @RequestBody ChronicDiseaseRequest r) { return created(service.addChronicDisease(patient, r)); }
    @PutMapping("/{patient}/chronic-diseases/{id}") @PreAuthorize(CLINICAL_WRITE) ChronicDiseaseResponse updateDisease(@PathVariable UUID patient, @PathVariable UUID id, @Valid @RequestBody ChronicDiseaseRequest r) { return service.updateChronicDisease(patient, id, r); }
    @DeleteMapping("/{patient}/chronic-diseases/{id}") @PreAuthorize(CLINICAL_WRITE) ResponseEntity<Void> deleteDisease(@PathVariable UUID patient, @PathVariable UUID id) { service.deleteChronicDisease(patient, id); return noContent(); }
    @PostMapping("/{patient}/medical-histories") @PreAuthorize(CLINICAL_WRITE) ResponseEntity<MedicalHistoryResponse> addMedicalHistory(@PathVariable UUID patient, @Valid @RequestBody MedicalHistoryRequest r) { return created(service.addMedicalHistory(patient, r)); }
    @PutMapping("/{patient}/medical-histories/{id}") @PreAuthorize(CLINICAL_WRITE) MedicalHistoryResponse updateMedicalHistory(@PathVariable UUID patient, @PathVariable UUID id, @Valid @RequestBody MedicalHistoryRequest r) { return service.updateMedicalHistory(patient, id, r); }
    @DeleteMapping("/{patient}/medical-histories/{id}") @PreAuthorize(CLINICAL_WRITE) ResponseEntity<Void> deleteMedicalHistory(@PathVariable UUID patient, @PathVariable UUID id) { service.deleteMedicalHistory(patient, id); return noContent(); }
    @PostMapping("/{patient}/surgical-histories") @PreAuthorize(CLINICAL_WRITE) ResponseEntity<SurgicalHistoryResponse> addSurgicalHistory(@PathVariable UUID patient, @Valid @RequestBody SurgicalHistoryRequest r) { return created(service.addSurgicalHistory(patient, r)); }
    @PutMapping("/{patient}/surgical-histories/{id}") @PreAuthorize(CLINICAL_WRITE) SurgicalHistoryResponse updateSurgicalHistory(@PathVariable UUID patient, @PathVariable UUID id, @Valid @RequestBody SurgicalHistoryRequest r) { return service.updateSurgicalHistory(patient, id, r); }
    @DeleteMapping("/{patient}/surgical-histories/{id}") @PreAuthorize(CLINICAL_WRITE) ResponseEntity<Void> deleteSurgicalHistory(@PathVariable UUID patient, @PathVariable UUID id) { service.deleteSurgicalHistory(patient, id); return noContent(); }
    @PostMapping("/{patient}/family-histories") @PreAuthorize(CLINICAL_WRITE) ResponseEntity<FamilyHistoryResponse> addFamilyHistory(@PathVariable UUID patient, @Valid @RequestBody FamilyHistoryRequest r) { return created(service.addFamilyHistory(patient, r)); }
    @PutMapping("/{patient}/family-histories/{id}") @PreAuthorize(CLINICAL_WRITE) FamilyHistoryResponse updateFamilyHistory(@PathVariable UUID patient, @PathVariable UUID id, @Valid @RequestBody FamilyHistoryRequest r) { return service.updateFamilyHistory(patient, id, r); }
    @DeleteMapping("/{patient}/family-histories/{id}") @PreAuthorize(CLINICAL_WRITE) ResponseEntity<Void> deleteFamilyHistory(@PathVariable UUID patient, @PathVariable UUID id) { service.deleteFamilyHistory(patient, id); return noContent(); }
    @PostMapping("/{patient}/disabilities") @PreAuthorize(CLINICAL_WRITE) ResponseEntity<DisabilityResponse> addDisability(@PathVariable UUID patient, @Valid @RequestBody DisabilityRequest r) { return created(service.addDisability(patient, r)); }
    @PutMapping("/{patient}/disabilities/{id}") @PreAuthorize(CLINICAL_WRITE) DisabilityResponse updateDisability(@PathVariable UUID patient, @PathVariable UUID id, @Valid @RequestBody DisabilityRequest r) { return service.updateDisability(patient, id, r); }
    @DeleteMapping("/{patient}/disabilities/{id}") @PreAuthorize(CLINICAL_WRITE) ResponseEntity<Void> deleteDisability(@PathVariable UUID patient, @PathVariable UUID id) { service.deleteDisability(patient, id); return noContent(); }
    @PostMapping("/{patient}/notes") @PreAuthorize(CLINICAL_WRITE) ResponseEntity<NoteResponse> addNote(@PathVariable UUID patient, @Valid @RequestBody NoteRequest r) { return created(service.addNote(patient, r)); }
    @PutMapping("/{patient}/notes/{id}") @PreAuthorize(CLINICAL_WRITE) NoteResponse updateNote(@PathVariable UUID patient, @PathVariable UUID id, @Valid @RequestBody NoteRequest r) { return service.updateNote(patient, id, r); }
    @DeleteMapping("/{patient}/notes/{id}") @PreAuthorize(CLINICAL_WRITE) ResponseEntity<Void> deleteNote(@PathVariable UUID patient, @PathVariable UUID id) { service.deleteNote(patient, id); return noContent(); }
    @PostMapping("/{patient}/flags") @PreAuthorize(CLINICAL_WRITE) ResponseEntity<FlagResponse> addFlag(@PathVariable UUID patient, @Valid @RequestBody FlagRequest r) { return created(service.addFlag(patient, r)); }
    @PutMapping("/{patient}/flags/{id}") @PreAuthorize(CLINICAL_WRITE) FlagResponse updateFlag(@PathVariable UUID patient, @PathVariable UUID id, @Valid @RequestBody FlagRequest r) { return service.updateFlag(patient, id, r); }
    @DeleteMapping("/{patient}/flags/{id}") @PreAuthorize(CLINICAL_WRITE) ResponseEntity<Void> deleteFlag(@PathVariable UUID patient, @PathVariable UUID id) { service.deleteFlag(patient, id); return noContent(); }
    @PutMapping("/{patient}/emergency-profile") @PreAuthorize(CLINICAL_WRITE) EmergencyProfileResponse emergencyProfile(@PathVariable UUID patient, @Valid @RequestBody EmergencyProfileRequest r) { return service.setEmergencyProfile(patient, r); }

    private <T> ResponseEntity<T> created(T body) { return ResponseEntity.status(201).body(body); }
    private ResponseEntity<Void> noContent() { return ResponseEntity.noContent().build(); }
}
