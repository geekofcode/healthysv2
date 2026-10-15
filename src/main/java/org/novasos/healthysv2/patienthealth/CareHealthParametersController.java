package org.novasos.healthysv2.patienthealth;

import jakarta.validation.Valid;
import java.util.*;
import org.novasos.healthysv2.patienthealth.api.PatientHealthDtos.*;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/v1/care/health-parameters")
class CareHealthParametersController {
  private final PatientHealthService service;

  CareHealthParametersController(PatientHealthService service) {
    this.service = service;
  }

  @GetMapping
  List<Parameter> parameters(@RequestParam(required = false) UUID visitId) {
    return service.careParameters(visitId);
  }

  @PostMapping
  Parameter create(@RequestParam UUID visitId, @Valid @RequestBody ParameterInput input) {
    return service.createCareParameter(visitId, input);
  }
}
