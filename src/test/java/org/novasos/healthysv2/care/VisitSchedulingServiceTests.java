package org.novasos.healthysv2.care;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

import java.time.*;
import java.util.*;
import org.junit.jupiter.api.Test;
import org.novasos.healthysv2.audit.AuditTrail;
import org.novasos.healthysv2.licensing.api.LicensingAccess;
import org.novasos.healthysv2.patient.CurrentUserContext;
import org.novasos.healthysv2.shared.api.error.ConflictException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.access.AccessDeniedException;

class VisitSchedulingServiceTests {
  final CareVisitRepository visits = mock(CareVisitRepository.class);
  final CurrentUserContext users = mock(CurrentUserContext.class);
  final LicensingAccess licensing = mock(LicensingAccess.class);
  final VisitSlotService slots = mock(VisitSlotService.class);
  final PhysicianQueueService queues = mock(PhysicianQueueService.class);
  final JdbcTemplate jdbc = mock(JdbcTemplate.class);
  final VisitSchedulingService service =
      new VisitSchedulingService(
          visits, users, licensing, slots, queues, jdbc, mock(AuditTrail.class));
  final UUID org = UUID.randomUUID();
  final org.novasos.healthysv2.organizationaccess.api.OrganizationPermissionAccess permissions =
      mock(org.novasos.healthysv2.organizationaccess.api.OrganizationPermissionAccess.class);

  CareVisit paidVisit() {
    var now = Instant.now();
    var episode =
        CareEpisode.create(
            UUID.randomUUID(), org, "Reason", new CarePolicy(Duration.ofDays(7), 30), now);
    var visit =
        CareVisit.prepare(
            episode,
            UUID.randomUUID(),
            now.plusSeconds(3600),
            new CarePolicy(Duration.ofDays(7), 30),
            now);
    episode.confirmPayment();
    visit.markPaidForScheduling();
    when(users.current())
        .thenReturn(new CurrentUserContext.UserContext(UUID.randomUUID(), org, Set.of("NURSE")));
    when(jdbc.queryForObject(anyString(), eq(Integer.class), any(Object[].class))).thenReturn(1);
    when(visits.findLockedById(visit.getId())).thenReturn(Optional.of(visit));
    return visit;
  }

  @org.junit.jupiter.api.BeforeEach
  void configurePermissionFixture() {
    service.configurePermissions(permissions);
  }

  @Test
  void staleClientCannotOverwriteAnotherUsersScheduling() {
    var visit = paidVisit();
    assertThatThrownBy(
            () ->
                service.program(
                    visit.getId(),
                    new VisitSchedulingDtos.Program(
                        visit.getProfessionalId(), visit.getScheduledAt(), 1L, null)))
        .isInstanceOf(ConflictException.class);
    verifyNoInteractions(slots, queues);
    assertThat(visit.getStatus()).isEqualTo(CareVisit.Status.AWAITING_SCHEDULING);
  }

  @Test
  void doctorWithoutSchedulingGrantCannotUseProgrammingEndpoint() {
    when(users.current())
        .thenReturn(new CurrentUserContext.UserContext(UUID.randomUUID(), org, Set.of("DOCTOR")));
    doThrow(new AccessDeniedException("ORGANIZATION_PERMISSION_DENIED"))
        .when(permissions)
        .require(org, "scheduling", "UPDATE");
    assertThatThrownBy(
            () ->
                service.program(
                    UUID.randomUUID(),
                    new VisitSchedulingDtos.Program(UUID.randomUUID(), Instant.now(), 0L, null)))
        .isInstanceOf(AccessDeniedException.class);
    verifyNoInteractions(visits, slots, queues, jdbc);
  }

  @Test
  void unavailableSlotCannotInvalidateExistingQueueOrChangeVisit() {
    var visit = paidVisit();
    doThrow(new ConflictException("VISIT_SLOT_ALREADY_BOOKED", "Occupied"))
        .when(slots)
        .requireSlot(any(), any(), any(), any(), any());
    assertThatThrownBy(
            () ->
                service.program(
                    visit.getId(),
                    new VisitSchedulingDtos.Program(
                        visit.getProfessionalId(), visit.getScheduledAt(), 0L, null)))
        .isInstanceOf(ConflictException.class);
    verifyNoInteractions(queues);
    assertThat(visit.getStatus()).isEqualTo(CareVisit.Status.AWAITING_SCHEDULING);
  }

  @Test
  void staffRoleRevokedDuringLockWaitCannotProgram() {
    var visit = paidVisit();
    doNothing()
        .doThrow(new AccessDeniedException("ORGANIZATION_PERMISSION_DENIED"))
        .when(permissions)
        .require(org, "scheduling", "UPDATE");
    assertThatThrownBy(
            () ->
                service.program(
                    visit.getId(),
                    new VisitSchedulingDtos.Program(
                        visit.getProfessionalId(), visit.getScheduledAt(), 0L, null)))
        .isInstanceOf(AccessDeniedException.class)
        .hasMessage("ORGANIZATION_PERMISSION_DENIED");
    verifyNoInteractions(slots, queues);
  }
}
