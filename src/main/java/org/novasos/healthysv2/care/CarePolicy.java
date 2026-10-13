package org.novasos.healthysv2.care;

import java.time.Duration;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

@Component
public record CarePolicy(Duration followUpDuration, int retentionYears) {
    public CarePolicy(@Value("${healthys.care.follow-up-duration:PT168H}") Duration followUpDuration,
                      @Value("${healthys.care.retention-years:30}") int retentionYears) {
        if (followUpDuration == null || followUpDuration.isNegative() || followUpDuration.isZero()
                || followUpDuration.getNano() != 0) {
            throw new IllegalArgumentException("Follow-up duration must be a positive whole number of seconds");
        }
        if (retentionYears <= 0) throw new IllegalArgumentException("Retention years must be positive");
        this.followUpDuration = followUpDuration;
        this.retentionYears = retentionYears;
    }
}
