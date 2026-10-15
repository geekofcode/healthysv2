package org.novasos.healthysv2.patienthealth;

import jakarta.validation.Valid;
import java.util.*;
import org.novasos.healthysv2.identity.api.*;
import org.novasos.healthysv2.patienthealth.api.PatientHealthDtos.*;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/v1/patients/me/health")
class PatientHealthController {
  private final PatientHealthService service;

  PatientHealthController(PatientHealthService service) {
    this.service = service;
  }

  @GetMapping("/parameters")
  List<Parameter> parameters() {
    return service.parameters(service.selectedPatient());
  }

  @PostMapping("/parameters")
  Parameter parameter(@Valid @RequestBody ParameterInput input) {
    return service.createSelfParameter(input);
  }

  @GetMapping("/observations")
  ObservationPage observations(
      @RequestParam(required = false) UUID parameterId,
      @RequestParam(defaultValue = "0") int page,
      @RequestParam(defaultValue = "50") int size) {
    return service.observations(service.selectedPatient(), parameterId, page, size);
  }

  @PostMapping("/observations")
  Observation append(@Valid @RequestBody ObservationInput input) {
    return service.appendSelf(input);
  }

  @GetMapping("/declarations")
  List<Declaration> declarations() {
    return service.declarations(service.selectedPatient());
  }

  @PostMapping("/declarations")
  Declaration declare(@Valid @RequestBody DeclarationInput input) {
    return service.declare(input);
  }

  @GetMapping("/personal")
  PersonProfileResponse personal() {
    return service.personal();
  }

  @PutMapping("/personal")
  PersonProfileResponse personal(@Valid @RequestBody UpdatePersonProfileRequest input) {
    return service.updatePersonal(input);
  }
}
