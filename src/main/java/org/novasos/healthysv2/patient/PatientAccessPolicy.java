package org.novasos.healthysv2.patient;

import java.time.Duration;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

@Component
class PatientAccessPolicy {
    final Duration requestDuration;
    final Duration emergencyDuration;
    final Duration scheduledGrace;
    final Duration inProgressDuration;
    @org.springframework.beans.factory.annotation.Autowired
    PatientAccessPolicy(@Value("${healthys.access.request-duration:PT48H}") Duration requestDuration,
                        @Value("${healthys.access.emergency-duration:PT1H}") Duration emergencyDuration,
                        @Value("${healthys.access.scheduled-grace:PT1H}") Duration scheduledGrace,
                        @Value("${healthys.access.in-progress-duration:PT4H}") Duration inProgressDuration) {
        this.requestDuration = positive(requestDuration);
        this.emergencyDuration = positive(emergencyDuration);
        this.scheduledGrace = positive(scheduledGrace);
        this.inProgressDuration = positive(inProgressDuration);
    }
    PatientAccessPolicy(Duration requestDuration, Duration emergencyDuration, Duration scheduledGrace) {
        this(requestDuration, emergencyDuration, scheduledGrace, Duration.ofHours(4));
    }
    private Duration positive(Duration duration) {
        if (duration.isNegative() || duration.isZero()) throw new IllegalArgumentException("Access durations must be positive");
        return duration;
    }
}
