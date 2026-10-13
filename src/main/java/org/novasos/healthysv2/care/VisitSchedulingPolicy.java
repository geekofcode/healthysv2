package org.novasos.healthysv2.care;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

@Component
record VisitSchedulingPolicy(int defaultSlotMinutes) {
    VisitSchedulingPolicy(@Value("${healthys.care.visit-slot-minutes:30}") int defaultSlotMinutes) {
        if (defaultSlotMinutes < 1 || defaultSlotMinutes > 240) {
            throw new IllegalArgumentException("Visit slot duration must be between 1 and 240 minutes");
        }
        this.defaultSlotMinutes = defaultSlotMinutes;
    }
}
