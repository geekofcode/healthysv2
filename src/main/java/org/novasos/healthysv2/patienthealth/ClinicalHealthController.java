package org.novasos.healthysv2.patienthealth;

import java.util.*;
import org.novasos.healthysv2.patienthealth.api.PatientHealthDtos.*;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/v1/patients/{patientId}/health")
class ClinicalHealthController {
  private final PatientHealthService service;

  ClinicalHealthController(PatientHealthService service) {
    this.service = service;
  }

  @GetMapping("/parameters")
  List<Parameter> parameters(@PathVariable UUID patientId) {
    service.clinicalRead(patientId);
    return service.parameters(patientId);
  }

  @GetMapping("/observations")
  ObservationPage observations(
      @PathVariable UUID patientId,
      @RequestParam(required = false) UUID parameterId,
      @RequestParam(defaultValue = "0") int page,
      @RequestParam(defaultValue = "50") int size) {
    service.clinicalRead(patientId);
    return service.observations(patientId, parameterId, page, size);
  }

  @GetMapping("/declarations")
  List<Declaration> declarations(@PathVariable UUID patientId) {
    service.clinicalRead(patientId);
    return service.declarations(patientId);
  }
}
