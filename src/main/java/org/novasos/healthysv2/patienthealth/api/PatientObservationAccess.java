package org.novasos.healthysv2.patienthealth.api;

import java.time.Instant;
import java.util.*;
import org.novasos.healthysv2.patienthealth.api.PatientHealthDtos.*;

public interface PatientObservationAccess {
  List<Observation> appendMeasured(UUID visitId, List<ObservationInput> measurements);

  void appendVitals(UUID visitId, Map<String, Object> vitals, Instant measuredAt);

  List<Observation> forVisit(UUID visitId);
}
