package org.novasos.healthysv2.care;

import java.util.List;
import java.util.UUID;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/v1/patients/{patientId}/care-history")
class PatientCareHistoryController {
  private final EncounterService encounters;

  PatientCareHistoryController(EncounterService encounters) {
    this.encounters = encounters;
  }

  @GetMapping
  List<EncounterService.HistoryReport> history(@PathVariable UUID patientId) {
    return encounters.history(patientId);
  }
}
