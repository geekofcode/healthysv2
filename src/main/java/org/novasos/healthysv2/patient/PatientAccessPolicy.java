package org.novasos.healthysv2.patient;

import java.time.Duration;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

@Component
class PatientAccessPolicy {
    final Duration requestDuration;
    final Duration emergencyDuration;
    final Duration scheduledGrace;
    PatientAccessPolicy(@Value("${healthys.access.request-duration:PT48H}") Duration requestDuration,
                        @Value("${healthys.access.emergency-duration:PT1H}") Duration emergencyDuration,
                        @Value("${healthys.access.scheduled-grace:PT1H}") Duration scheduledGrace) {
        this.requestDuration = positive(requestDuration);
        this.emergencyDuration = positive(emergencyDuration);
        this.scheduledGrace = positive(scheduledGrace);
    }
    private Duration positive(Duration duration) {
        if (duration.isNegative() || duration.isZero()) throw new IllegalArgumentException("Access durations must be positive");
        return duration;
    }
}
