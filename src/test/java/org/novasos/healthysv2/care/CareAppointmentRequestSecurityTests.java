package org.novasos.healthysv2.care;

import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.Mockito.*;

import java.time.Duration;
import java.time.Instant;
import java.util.Set;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.novasos.healthysv2.licensing.api.LicensingAccess;
import org.novasos.healthysv2.organizationaccess.api.OrganizationPermissionAccess;
import org.novasos.healthysv2.patient.CurrentUserContext;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.access.AccessDeniedException;

class CareAppointmentRequestSecurityTests {
  private final JdbcTemplate jdbc = mock(JdbcTemplate.class);
  private final CurrentUserContext users = mock(CurrentUserContext.class);
  private final OrganizationPermissionAccess permissions = mock(OrganizationPermissionAccess.class);
  private final LicensingAccess licensing = mock(LicensingAccess.class);
  private final ReceptionService reception = mock(ReceptionService.class);
  private final CareAppointmentRequestService service =
      new CareAppointmentRequestService(
          jdbc, users, permissions, licensing, reception, new CarePolicy(Duration.ofDays(7), 30));

  @Test
  void noSelfRequestWithoutSelectedPersistedPatientProfile() {
    when(users.current())
        .thenReturn(new CurrentUserContext.UserContext(UUID.randomUUID(), null, Set.of("PATIENT")));
    assertThrows(
        AccessDeniedException.class,
        () ->
            service.self(
                new CareAppointmentRequestDtos.SelfRequest(
                    UUID.randomUUID(),
                    UUID.randomUUID(),
                    "Visit",
                    Instant.now().plusSeconds(3600))));
    verifyNoInteractions(jdbc, reception);
  }

  @Test
  void selectedPatientCannotActAsHospitalScheduler() {
    when(users.current())
        .thenReturn(
            new CurrentUserContext.UserContext(
                UUID.randomUUID(), null, Set.of("PATIENT"), UUID.randomUUID(), UUID.randomUUID()));
    assertThrows(
        AccessDeniedException.class,
        () ->
            service.approve(
                UUID.randomUUID(),
                new CareAppointmentRequestDtos.Approval(
                    UUID.randomUUID(), Instant.now().plusSeconds(3600))));
    verifyNoInteractions(jdbc, reception, permissions, licensing);
  }

  @Test
  void permissionDenialCannotCreateEpisodeOrReadPendingRequests() {
    UUID org = UUID.randomUUID();
    when(users.current())
        .thenReturn(
            new CurrentUserContext.UserContext(
                UUID.randomUUID(), org, Set.of("NURSE"), UUID.randomUUID()));
    doThrow(new AccessDeniedException("DENIED"))
        .when(permissions)
        .require(org, "scheduling", "UPDATE");
    assertThrows(
        AccessDeniedException.class,
        () ->
            service.approve(
                UUID.randomUUID(),
                new CareAppointmentRequestDtos.Approval(
                    UUID.randomUUID(), Instant.now().plusSeconds(3600))));
    verifyNoInteractions(jdbc, reception, licensing);
  }
}
