package org.novasos.healthysv2.care;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

import com.fasterxml.jackson.databind.ObjectMapper;
import java.time.*;
import java.util.*;
import org.junit.jupiter.api.Test;
import org.novasos.healthysv2.audit.AuditTrail;
import org.novasos.healthysv2.licensing.api.LicensingAccess;
import org.novasos.healthysv2.patient.CurrentUserContext;
import org.novasos.healthysv2.patient.PatientAccessService;
import org.novasos.healthysv2.shared.api.error.BusinessRuleException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.access.AccessDeniedException;

class VisitIntakeServiceTests {
  final UUID org = UUID.randomUUID();
  final CurrentUserContext users = mock(CurrentUserContext.class);
  final CareVisitRepository visits = mock(CareVisitRepository.class);
  final JdbcTemplate jdbc = mock(JdbcTemplate.class);
  final PatientAccessService access = mock(PatientAccessService.class);
  final CarePolicy policy = new CarePolicy(Duration.ofDays(7), 30);
  final VisitIntakeService service =
      new VisitIntakeService(
          visits,
          users,
          mock(LicensingAccess.class),
          access,
          jdbc,
          new ObjectMapper(),
          policy,
          mock(AuditTrail.class));
  final org.novasos.healthysv2.organizationaccess.api.OrganizationPermissionAccess permissions =
      mock(org.novasos.healthysv2.organizationaccess.api.OrganizationPermissionAccess.class);
  final org.novasos.healthysv2.patienthealth.api.PatientObservationAccess observations =
      mock(org.novasos.healthysv2.patienthealth.api.PatientObservationAccess.class);

  {
    service.configurePermissions(permissions);
    service.configureObservations(observations);
  }

  @Test
  void personWithoutIntakePermissionCannotOverwriteTheIntake() {
    doThrow(new AccessDeniedException("ORGANIZATION_PERMISSION_REQUIRED"))
        .when(permissions)
        .require(org, "intakes", "UPDATE");

    when(users.current())
        .thenReturn(
            new CurrentUserContext.UserContext(
                UUID.randomUUID(), org, Set.of("DOCTOR"), UUID.randomUUID()));
    assertThatThrownBy(() -> service.write(UUID.randomUUID(), null))
        .isInstanceOf(AccessDeniedException.class);
    verifyNoInteractions(visits, jdbc);
  }

  @Test
  void startedEncounterIntakeIsPreserved() {
    Instant now = Instant.now();
    var episode =
        CareEpisode.create(UUID.randomUUID(), org, "Reason", policy, now.minusSeconds(3600));
    episode.confirmPayment();
    var visit =
        CareVisit.schedule(episode, UUID.randomUUID(), now, false, policy, now.minusSeconds(3600));
    visit.start(org, now);
    when(users.current())
        .thenReturn(
            new CurrentUserContext.UserContext(
                UUID.randomUUID(), org, Set.of("NURSE"), UUID.randomUUID()));
    when(jdbc.queryForObject(anyString(), eq(Integer.class), any(Object[].class))).thenReturn(1);
    when(visits.findLockedById(visit.getId())).thenReturn(Optional.of(visit));
    assertThatThrownBy(() -> service.write(visit.getId(), null))
        .isInstanceOf(BusinessRuleException.class);
    verify(jdbc, never()).update(anyString(), any(Object[].class));
  }

  @Test
  void paidEpisodeBeforeFirstEncounterAcceptsConfiguredReceptionStaffAndRecordsDynamicHistory() {
    Instant now = Instant.now();
    var episode =
        CareEpisode.create(UUID.randomUUID(), org, "Reason", policy, now.minusSeconds(600));
    episode.confirmPayment();
    var visit =
        CareVisit.schedule(
            episode,
            UUID.randomUUID(),
            now.plusSeconds(3600),
            false,
            policy,
            now.minusSeconds(500));
    when(users.current())
        .thenReturn(
            new CurrentUserContext.UserContext(
                UUID.randomUUID(), org, Set.of("HOSPITAL_AGENT"), UUID.randomUUID()));
    when(visits.findLockedById(visit.getId())).thenReturn(Optional.of(visit));
    var input =
        new org.novasos.healthysv2.patienthealth.api.PatientHealthDtos.ObservationInput(
            UUID.randomUUID(), "42", now, null, null);
    var request =
        new ReceptionDtos.Intake(
            "Accueil",
            new ReceptionDtos.Vitals(null, null, null, null, null, null, null, null),
            List.of(input));
    assertThat(service.write(visit.getId(), request)).isEqualTo(request);
    verify(observations).appendVitals(eq(visit.getId()), anyMap(), any(Instant.class));
    verify(observations).appendMeasured(visit.getId(), List.of(input));
  }
}
