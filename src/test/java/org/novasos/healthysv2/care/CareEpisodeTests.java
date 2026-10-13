package org.novasos.healthysv2.care;

import static org.assertj.core.api.Assertions.*;
import java.time.*;
import java.util.UUID;
import org.junit.jupiter.api.Test;

class CareEpisodeTests {
    final Instant now = Instant.parse("2026-10-12T12:00:00Z");
    final UUID organization = UUID.randomUUID();
    final CarePolicy policy = new CarePolicy(Duration.ofDays(7), 30);
    CareEpisode episode() { return CareEpisode.create(UUID.randomUUID(), organization, "Check-up", policy, now); }

    @Test void paymentAndPostponementDoNotStartCoverage() {
        var episode = episode();
        assertThatThrownBy(() -> CareVisit.schedule(episode, UUID.randomUUID(), now, false, policy, now))
                .isInstanceOf(IllegalStateException.class);
        episode.confirmPayment();
        var visit = CareVisit.schedule(episode, UUID.randomUUID(), now.plusSeconds(86400), false, policy, now);
        visit.reschedule(now.plusSeconds(172800), now);
        visit.reassign(UUID.randomUUID());
        assertThat(episode.getStartedAt()).isNull();
        visit.start(organization, now.plusSeconds(172800));
        assertThat(episode.getExpiresAt()).isEqualTo(now.plusSeconds(172800).plus(Duration.ofDays(7)));
        assertThatThrownBy(() -> visit.reassign(UUID.randomUUID())).isInstanceOf(IllegalStateException.class);
    }
    @Test void followUpCanChangeDoctorButCannotExtendCoverageOrChangeEstablishment() {
        var episode = episode(); episode.confirmPayment();
        var first = CareVisit.schedule(episode, UUID.randomUUID(), now, false, policy, now);
        first.start(organization, now); first.complete(now.plusSeconds(600));
        var expiration = episode.getExpiresAt();
        var followUp = CareVisit.schedule(episode, UUID.randomUUID(), now.plusSeconds(86400), true, policy, now);
        assertThatThrownBy(() -> followUp.start(UUID.randomUUID(), now.plusSeconds(86400)))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> followUp.reschedule(expiration, now)).isInstanceOf(IllegalStateException.class);
        followUp.start(organization, now.plusSeconds(86400));
        assertThat(episode.getExpiresAt()).isEqualTo(expiration);
        assertThat(episode.isActiveAt(expiration)).isFalse();
    }
    @Test void receptionCreatesFirstVisitWithoutStartingOrBypassingCash() {
        var episode=episode();
        var speciality=UUID.randomUUID();
        episode.specifySpeciality(speciality);
        var visit=CareVisit.prepare(episode,UUID.randomUUID(),now.plusSeconds(3600),policy,now);
        assertThat(episode.getSpecialityId()).isEqualTo(speciality);
        assertThat(episode.getStatus()).isEqualTo(CareEpisode.Status.AWAITING_PAYMENT);
        assertThat(visit.getStatus()).isEqualTo(CareVisit.Status.PENDING_PAYMENT);
        assertThat(episode.getStartedAt()).isNull();
        assertThat(episode.getExpiresAt()).isNull();
        assertThatThrownBy(()->visit.start(organization,now)).isInstanceOf(IllegalStateException.class);
        assertThatThrownBy(visit::releaseAfterPayment).isInstanceOf(IllegalStateException.class);
        episode.confirmPayment();
        visit.releaseAfterPayment();
        assertThat(visit.getStatus()).isEqualTo(CareVisit.Status.SCHEDULED);
        assertThat(episode.getStartedAt()).isNull();
        visit.start(organization,now.plusSeconds(3600));
        assertThat(episode.getStartedAt()).isEqualTo(now.plusSeconds(3600));
    }
    @Test void retentionUsesCalendarYearsAndPolicyIsValidated() {
        assertThat(episode().getRetentionExpiresAt()).isEqualTo(Instant.parse("2056-10-12T12:00:00Z"));
        assertThatThrownBy(() -> new CarePolicy(Duration.ZERO, 30)).isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> new CarePolicy(Duration.ofDays(7), 0)).isInstanceOf(IllegalArgumentException.class);
    }
}
