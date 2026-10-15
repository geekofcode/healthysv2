package org.novasos.healthysv2.care;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

import java.time.*;
import java.util.*;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.novasos.healthysv2.audit.AuditTrail;
import org.novasos.healthysv2.licensing.api.LicensingAccess;
import org.novasos.healthysv2.patient.CurrentUserContext;
import org.novasos.healthysv2.shared.api.error.*;
import org.springframework.jdbc.core.JdbcTemplate;

class FollowUpServiceTests {
  final UUID org = UUID.randomUUID();
  final CareEpisodeRepository episodes = mock(CareEpisodeRepository.class);
  final CareVisitRepository visits = mock(CareVisitRepository.class);
  final VisitSlotService slots = mock(VisitSlotService.class);
  final CurrentUserContext users = mock(CurrentUserContext.class);
  final JdbcTemplate jdbc = mock(JdbcTemplate.class);
  final CarePolicy policy = new CarePolicy(Duration.ofDays(7), 30);
  final FollowUpService service =
      new FollowUpService(
          episodes,
          visits,
          users,
          mock(LicensingAccess.class),
          slots,
          mock(VisitSchedulingService.class),
          jdbc,
          policy,
          mock(AuditTrail.class));

  CareEpisode episode(Duration elapsed) {
    Instant start = Instant.now().minus(elapsed);
    var episode =
        CareEpisode.create(
            UUID.randomUUID(), org, "Initial reason", policy, start.minusSeconds(3600));
    episode.specifySpeciality(UUID.randomUUID());
    episode.confirmPayment();
    episode.startVisit(org, false, start);
    when(episodes.findLockedById(episode.getId())).thenReturn(Optional.of(episode));
    when(users.current())
        .thenReturn(new CurrentUserContext.UserContext(UUID.randomUUID(), org, Set.of("NURSE")));
    when(jdbc.queryForObject(anyString(), eq(Integer.class), any(Object[].class))).thenReturn(1);
    when(jdbc.query(
            contains("status='COMPLETED'"),
            (org.springframework.jdbc.core.ResultSetExtractor<UUID>) any(),
            any(Object[].class)))
        .thenReturn(UUID.randomUUID());
    when(slots.requireSlot(any(), any(), any(), any(), any())).thenReturn(30);
    return episode;
  }

  @org.junit.jupiter.api.BeforeEach
  void configurePermissionFixture() {
    service.configurePermissions(
        mock(org.novasos.healthysv2.organizationaccess.api.OrganizationPermissionAccess.class));
  }

  @Test
  void newFollowUpKeepsOriginalCoverageAndCreatesItsOwnVisit() {
    var episode = episode(Duration.ofDays(2));
    Instant start = episode.getStartedAt(), expiry = episode.getExpiresAt();
    UUID doctor = UUID.randomUUID();
    service.create(
        episode.getId(),
        "follow-up-001",
        new FollowUpController.Request(
            doctor, Instant.now().plusSeconds(3600), "Second examination"));
    var saved = ArgumentCaptor.forClass(CareVisit.class);
    verify(visits).saveAndFlush(saved.capture());
    assertThat(saved.getValue().isFollowUp()).isTrue();
    assertThat(saved.getValue().getProfessionalId()).isEqualTo(doctor);
    assertThat(saved.getValue().getReason()).isEqualTo("Second examination");
    assertThat(saved.getValue().getStatus()).isEqualTo(CareVisit.Status.SCHEDULED);
    assertThat(episode.getStartedAt()).isEqualTo(start);
    assertThat(episode.getExpiresAt()).isEqualTo(expiry);
  }

  @Test
  void coverageExpiredCannotCreateFreeFollowUp() {
    var episode = episode(Duration.ofDays(8));
    assertThatThrownBy(
            () ->
                service.create(
                    episode.getId(),
                    "follow-up-002",
                    new FollowUpController.Request(
                        UUID.randomUUID(), Instant.now().plusSeconds(60), null)))
        .isInstanceOf(BusinessRuleException.class);
    verifyNoInteractions(slots, visits);
  }

  @Test
  void requestedDateAtExpiryIsNotCovered() {
    var episode = episode(Duration.ofDays(2));
    assertThatThrownBy(
            () ->
                service.create(
                    episode.getId(),
                    "follow-up-003",
                    new FollowUpController.Request(
                        UUID.randomUUID(), episode.getExpiresAt(), null)))
        .isInstanceOf(BusinessRuleException.class);
    verifyNoInteractions(slots, visits);
  }

  @Test
  void anotherEstablishmentCannotReuseCoveredEpisode() {
    var episode = episode(Duration.ofDays(2));
    when(users.current())
        .thenReturn(
            new CurrentUserContext.UserContext(
                UUID.randomUUID(), UUID.randomUUID(), Set.of("NURSE")));
    assertThatThrownBy(
            () ->
                service.create(
                    episode.getId(),
                    "follow-up-004",
                    new FollowUpController.Request(
                        UUID.randomUUID(), Instant.now().plusSeconds(60), null)))
        .isInstanceOf(ResourceNotFoundException.class);
    verifyNoInteractions(slots, visits);
  }
}
