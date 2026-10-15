package org.novasos.healthysv2.care;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

import java.time.*;
import java.util.*;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentMatchers;
import org.novasos.healthysv2.audit.AuditTrail;
import org.novasos.healthysv2.licensing.api.LicensingAccess;
import org.novasos.healthysv2.patient.CurrentUserContext;
import org.novasos.healthysv2.shared.api.error.*;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.ResultSetExtractor;
import org.springframework.security.access.AccessDeniedException;

class PhysicianQueueServiceTests {
  @Test
  void calendarBoundsRespectLocalDayAcrossDaylightSaving() {
    var zone = ZoneId.of("America/Toronto");
    var spring = PhysicianQueueService.dateWindow(LocalDate.of(2026, 3, 8), zone);
    var fall = PhysicianQueueService.dateWindow(LocalDate.of(2026, 11, 1), zone);
    assertEquals(23, Duration.between(spring.get(0), spring.get(1)).toHours());
    assertEquals(25, Duration.between(fall.get(0), fall.get(1)).toHours());
    assertEquals(Instant.parse("2026-03-08T05:00:00Z"), spring.get(0));
  }

  @Test
  void rejectsLostUpdateBeforeChangingQueue() {
    assertDoesNotThrow(() -> PhysicianQueueService.checkVersion(4, 4));
    assertThrows(ConflictException.class, () -> PhysicianQueueService.checkVersion(5, 4));
  }

  @Test
  void reorderRequiresExactCalendarPermutation() {
    var first = UUID.randomUUID();
    var second = UUID.randomUUID();
    var foreign = UUID.randomUUID();
    assertDoesNotThrow(
        () -> PhysicianQueueService.validateOrder(List.of(first, second), List.of(second, first)));
    assertThrows(
        BusinessRuleException.class,
        () -> PhysicianQueueService.validateOrder(List.of(first, second), List.of(first, first)));
    assertThrows(
        BusinessRuleException.class,
        () -> PhysicianQueueService.validateOrder(List.of(first, second), List.of(first, foreign)));
    assertThrows(
        BusinessRuleException.class,
        () -> PhysicianQueueService.validateOrder(List.of(first, second), List.of(first)));
  }

  @Test
  void platformAdministratorCannotOperatePhysicianQueue() {
    var jdbc = mock(JdbcTemplate.class);
    var users = mock(CurrentUserContext.class);
    var licensing = mock(LicensingAccess.class);
    when(users.current())
        .thenReturn(
            new CurrentUserContext.UserContext(
                UUID.randomUUID(), UUID.randomUUID(), Set.of("PLATFORM_ADMIN")));
    var service = new PhysicianQueueService(jdbc, users, licensing, mock(AuditTrail.class));
    assertThrows(
        AccessDeniedException.class,
        () -> service.next(new PhysicianQueueDtos.Next(UUID.randomUUID(), LocalDate.now(), 0L)));
    verifyNoInteractions(jdbc, licensing);
  }

  @Test
  void revokedDoctorProfileIsRecheckedAfterOrganizationLock() {
    var jdbc = mock(JdbcTemplate.class);
    var users = mock(CurrentUserContext.class);
    var licensing = mock(LicensingAccess.class);
    var org = UUID.randomUUID();
    var person = UUID.randomUUID();
    var profile = UUID.randomUUID();
    when(users.current())
        .thenReturn(
            new CurrentUserContext.UserContext(person, org, Set.of("DOCTOR"), profile, null))
        .thenThrow(new AccessDeniedException("PROFILE_INACTIVE"));
    var service = new PhysicianQueueService(jdbc, users, licensing, mock(AuditTrail.class));
    service.configurePermissions(
        mock(org.novasos.healthysv2.organizationaccess.api.OrganizationPermissionAccess.class));
    assertThrows(
        AccessDeniedException.class,
        () -> service.next(new PhysicianQueueDtos.Next(UUID.randomUUID(), LocalDate.now(), 0L)));
    verify(jdbc)
        .queryForObject(
            "select id from organization.organization where id=? for update", UUID.class, org);
    verify(users, times(2)).current();
    verify(jdbc, never()).queryForList(anyString(), eq(String.class), any(Object[].class));
    verify(jdbc, never()).update(anyString(), any(Object[].class));
  }

  @Test
  void doctorWithoutPresenceGrantCannotMarkReceptionPresence() {
    var jdbc = mock(JdbcTemplate.class);
    var users = mock(CurrentUserContext.class);
    var licensing = mock(LicensingAccess.class);
    var org = UUID.randomUUID();
    when(users.current())
        .thenReturn(
            new CurrentUserContext.UserContext(
                UUID.randomUUID(), org, Set.of("DOCTOR"), UUID.randomUUID(), null));
    var service = new PhysicianQueueService(jdbc, users, licensing, mock(AuditTrail.class));
    var permissions =
        mock(org.novasos.healthysv2.organizationaccess.api.OrganizationPermissionAccess.class);
    doThrow(new AccessDeniedException("ORGANIZATION_PERMISSION_DENIED"))
        .when(permissions)
        .require(org, "queue", "UPDATE");
    service.configurePermissions(permissions);
    assertThrows(
        AccessDeniedException.class,
        () -> service.presence(new PhysicianQueueDtos.Presence(UUID.randomUUID(), true, 0L)));
    verify(licensing).requireMember(org);
    verifyNoInteractions(jdbc);
  }

  @Test
  void switchingMembershipSourceDuringLockCannotBorrowOtherProfilePermissions() {
    var jdbc = mock(JdbcTemplate.class);
    var users = mock(CurrentUserContext.class);
    var licensing = mock(LicensingAccess.class);
    var org = UUID.randomUUID();
    var person = UUID.randomUUID();
    when(users.current())
        .thenReturn(
            new CurrentUserContext.UserContext(
                person, org, Set.of("DOCTOR"), UUID.randomUUID(), null),
            new CurrentUserContext.UserContext(
                person, org, Set.of("DOCTOR"), UUID.randomUUID(), null));
    var service = new PhysicianQueueService(jdbc, users, licensing, mock(AuditTrail.class));
    service.configurePermissions(
        mock(org.novasos.healthysv2.organizationaccess.api.OrganizationPermissionAccess.class));
    var failure =
        assertThrows(
            AccessDeniedException.class,
            () ->
                service.next(new PhysicianQueueDtos.Next(UUID.randomUUID(), LocalDate.now(), 0L)));
    assertEquals("PROFILE_CONTEXT_CHANGED", failure.getMessage());
    verify(jdbc, never()).update(anyString(), any(Object[].class));
  }

  @Test
  void selectedDoctorCannotBorrowAnAdministrativeProfileToReadAnotherDoctorsCalendar() {
    var jdbc = mock(JdbcTemplate.class);
    var users = mock(CurrentUserContext.class);
    var licensing = mock(LicensingAccess.class);
    var org = UUID.randomUUID();
    var person = UUID.randomUUID();
    var ownDoctor = UUID.randomUUID();
    when(users.current())
        .thenReturn(
            new CurrentUserContext.UserContext(
                person, org, Set.of("DOCTOR"), UUID.randomUUID(), null));
    when(jdbc.query(
            eq(
                "select id from professional.professional where person_id=? and status='ACTIVE' and"
                    + " professional_type in ('DOCTOR','MEDECIN','NURSE')"),
            ArgumentMatchers.<ResultSetExtractor<UUID>>any(),
            eq(person)))
        .thenReturn(ownDoctor);
    var service = new PhysicianQueueService(jdbc, users, licensing, mock(AuditTrail.class));
    service.configurePermissions(
        mock(org.novasos.healthysv2.organizationaccess.api.OrganizationPermissionAccess.class));
    var failure =
        assertThrows(
            AccessDeniedException.class,
            () -> service.calendar(UUID.randomUUID(), LocalDate.now()));
    assertEquals("OWN_DOCTOR_QUEUE_REQUIRED", failure.getMessage());
    verify(users, times(2)).current();
    verify(jdbc, never()).queryForList(anyString(), eq(String.class), any(Object[].class));
    verify(jdbc, never()).update(anyString(), any(Object[].class));
  }

  @Test
  void selectedNurseCanOnlyCallTheQueueOfTheirOwnProfessionalIdentity() {
    var jdbc = mock(JdbcTemplate.class);
    var users = mock(CurrentUserContext.class);
    var licensing = mock(LicensingAccess.class);
    var org = UUID.randomUUID();
    var person = UUID.randomUUID();
    var ownDoctor = UUID.randomUUID();
    when(users.current())
        .thenReturn(
            new CurrentUserContext.UserContext(
                person, org, Set.of("NURSE"), UUID.randomUUID(), null));
    when(jdbc.query(
            eq(
                "select id from professional.professional where person_id=? and status='ACTIVE' and"
                    + " professional_type in ('DOCTOR','MEDECIN','NURSE')"),
            ArgumentMatchers.<ResultSetExtractor<UUID>>any(),
            eq(person)))
        .thenReturn(ownDoctor);
    var service = new PhysicianQueueService(jdbc, users, licensing, mock(AuditTrail.class));
    service.configurePermissions(
        mock(org.novasos.healthysv2.organizationaccess.api.OrganizationPermissionAccess.class));
    var failure =
        assertThrows(
            AccessDeniedException.class,
            () ->
                service.next(new PhysicianQueueDtos.Next(UUID.randomUUID(), LocalDate.now(), 0L)));
    assertEquals("OWN_DOCTOR_QUEUE_REQUIRED", failure.getMessage());
    verify(users, times(2)).current();
    verify(jdbc, never()).queryForList(anyString(), eq(String.class), any(Object[].class));
    verify(jdbc, never()).update(anyString(), any(Object[].class));
  }
}
