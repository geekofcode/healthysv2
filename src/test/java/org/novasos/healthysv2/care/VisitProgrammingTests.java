package org.novasos.healthysv2.care;

import static org.assertj.core.api.Assertions.*;

import java.time.*;
import java.util.UUID;
import org.junit.jupiter.api.Test;

class VisitProgrammingTests {
  final Instant now = Instant.parse("2026-10-13T10:00:00Z");
  final UUID org = UUID.randomUUID();
  final CarePolicy policy = new CarePolicy(Duration.ofDays(7), 30);

  @Test
  void cashSettlementDoesNotCreateAProgrammedAppointmentOrStartFollowUp() {
    var episode = CareEpisode.create(UUID.randomUUID(), org, "Reason", policy, now);
    var visit = CareVisit.prepare(episode, UUID.randomUUID(), now.plusSeconds(3600), policy, now);
    episode.confirmPayment();
    visit.markPaidForScheduling();
    assertThat(visit.getStatus()).isEqualTo(CareVisit.Status.AWAITING_SCHEDULING);
    assertThat(episode.getStartedAt()).isNull();
    assertThat(episode.getExpiresAt()).isNull();
    assertThatThrownBy(() -> visit.start(org, now.plusSeconds(60)))
        .isInstanceOf(IllegalStateException.class);
  }

  @Test
  void changesTheSameVisitWithoutRecreatingEpisodeOrStartingFollowUp() {
    var episode = CareEpisode.create(UUID.randomUUID(), org, "Reason", policy, now);
    var visit = CareVisit.prepare(episode, UUID.randomUUID(), now.plusSeconds(3600), policy, now);
    UUID id = visit.getId();
    UUID episodeId = episode.getId();
    episode.confirmPayment();
    visit.markPaidForScheduling();
    visit.program(UUID.randomUUID(), now.plusSeconds(3600), 20, now);
    UUID replacement = UUID.randomUUID();
    visit.program(replacement, now.plusSeconds(7200), 20, now);
    assertThat(visit.getId()).isEqualTo(id);
    assertThat(visit.episode().getId()).isEqualTo(episodeId);
    assertThat(visit.getProfessionalId()).isEqualTo(replacement);
    assertThat(episode.getStatus()).isEqualTo(CareEpisode.Status.ACTIVE);
    assertThat(episode.getStartedAt()).isNull();
    assertThatThrownBy(() -> visit.program(replacement, now.plusSeconds(3600), 20, now))
        .isInstanceOf(IllegalArgumentException.class);
  }

  @Test
  void refundSuspendsTheVisitButPreservesItsIdentityAndCoverageSnapshot() {
    var episode = CareEpisode.create(UUID.randomUUID(), org, "Reason", policy, now);
    var visit = CareVisit.prepare(episode, UUID.randomUUID(), now.plusSeconds(3600), policy, now);
    UUID id = visit.getId();
    long coverage = episode.getFollowUpSeconds();
    episode.confirmPayment();
    visit.markPaidForScheduling();
    visit.program(visit.getProfessionalId(), visit.getScheduledAt(), 30, now);
    episode.reopenPayment();
    visit.suspendForOutstandingBalance();
    assertThat(episode.getStatus()).isEqualTo(CareEpisode.Status.DRAFT);
    assertThat(visit.getStatus()).isEqualTo(CareVisit.Status.PENDING_PAYMENT);
    assertThat(visit.getId()).isEqualTo(id);
    assertThat(episode.getFollowUpSeconds()).isEqualTo(coverage);
  }

  @Test
  void deferredVisitRequiresExplicitProgrammingAndKeepsEpisodeCoverage() {
    var episode = CareEpisode.create(UUID.randomUUID(), org, "Reason", policy, now);
    episode.confirmPayment();
    var visit =
        CareVisit.schedule(episode, UUID.randomUUID(), now.plusSeconds(3600), false, policy, now);
    UUID id = visit.getId();
    visit.deferForReassignment();
    assertThat(visit.getStatus()).isEqualTo(CareVisit.Status.AWAITING_SCHEDULING);
    assertThatThrownBy(() -> visit.start(org, now.plusSeconds(3600)))
        .isInstanceOf(IllegalStateException.class);
    visit.program(UUID.randomUUID(), now.plusSeconds(86400), 30, now);
    assertThat(visit.getId()).isEqualTo(id);
    assertThat(episode.getStartedAt()).isNull();
    assertThat(visit.getStatus()).isEqualTo(CareVisit.Status.SCHEDULED);
  }

  @Test
  void followUpParentCannotBeAssignedToInitialVisit() {
    var e = CareEpisode.create(UUID.randomUUID(), org, "Reason", policy, now);
    e.confirmPayment();
    var v = CareVisit.schedule(e, UUID.randomUUID(), now, false, policy, now);
    assertThatThrownBy(() -> v.specifyParent(UUID.randomUUID()))
        .isInstanceOf(IllegalArgumentException.class);
  }

  @Test
  void aStartedVisitCannotBeReassignedPostponedOrRefunded() {
    var episode = CareEpisode.create(UUID.randomUUID(), org, "Reason", policy, now);
    episode.confirmPayment();
    var visit =
        CareVisit.schedule(episode, UUID.randomUUID(), now.plusSeconds(3600), false, policy, now);
    visit.start(org, now.plusSeconds(3600));
    assertThatThrownBy(() -> visit.program(UUID.randomUUID(), now.plusSeconds(7200), 30, now))
        .isInstanceOf(IllegalStateException.class);
    assertThatThrownBy(episode::reopenPayment).isInstanceOf(IllegalStateException.class);
  }
}
