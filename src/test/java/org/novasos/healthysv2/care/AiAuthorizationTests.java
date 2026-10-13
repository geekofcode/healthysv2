package org.novasos.healthysv2.care;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

import java.util.*;
import org.junit.jupiter.api.Test;
import org.novasos.healthysv2.patient.*;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.access.AccessDeniedException;

class AiAuthorizationTests {
  private final JdbcTemplate db = mock(JdbcTemplate.class);
  private final PatientAccessService access = mock(PatientAccessService.class);
  private final AiAuthorization auth = new AiAuthorization(db, access);
  private final UUID visit = UUID.randomUUID(),
      actor = UUID.randomUUID(),
      org = UUID.randomUUID(),
      patient = UUID.randomUUID(),
      professional = UUID.randomUUID();

  private void setup() {
    when(db.queryForList(contains("e.patient_id,v.professional_id"), eq(visit), eq(org)))
        .thenReturn(List.of(Map.of("patient_id", patient, "professional_id", professional)));
    when(db.queryForObject(
            contains("professional.professional p"),
            eq(Integer.class),
            eq(professional),
            eq(actor),
            eq(org),
            eq(org)))
        .thenReturn(1);
    when(db.queryForObject(contains("licensing.subscription"), eq(Integer.class), eq(org)))
        .thenReturn(1);
  }

  @Test
  void schedulerUsesPersistedActorWithoutAuthenticationContext() {
    setup();
    var context = auth.require(visit, actor, org);
    assertEquals(patient, context.patientId());
    verify(access)
        .requireAccessForActor(
            eq(new CurrentUserContext.UserContext(actor, org, Set.of("DOCTOR"))),
            eq(patient),
            eq("MEDICAL_RECORD"),
            eq("READ"));
  }

  @Test
  void removedMembershipStopsGenerationEvenWhenOriginalJobWasAuthorized() {
    setup();
    when(db.queryForObject(
            contains("professional.professional p"),
            eq(Integer.class),
            eq(professional),
            eq(actor),
            eq(org),
            eq(org)))
        .thenReturn(0);
    assertThrows(AccessDeniedException.class, () -> auth.require(visit, actor, org));
    verifyNoInteractions(access);
  }

  @Test
  void subscriptionWithoutAiCannotExecuteOrReadResults() {
    setup();
    when(db.queryForObject(contains("licensing.subscription"), eq(Integer.class), eq(org)))
        .thenReturn(0);
    assertThrows(AccessDeniedException.class, () -> auth.require(visit, actor, org));
    verifyNoInteractions(access);
  }

  @Test
  void revocationAfterQueuingIsReevaluated() {
    setup();
    doThrow(new AccessDeniedException("PATIENT_REVOKED_ACCESS"))
        .when(access)
        .requireAccessForActor(any(), eq(patient), eq("MEDICAL_RECORD"), eq("READ"));
    assertThrows(AccessDeniedException.class, () -> auth.require(visit, actor, org));
  }

  @Test
  void noOrganizationCannotUsePatientSelfShortcut() {
    assertThrows(AccessDeniedException.class, () -> auth.require(visit, actor, null));
    verifyNoInteractions(db, access);
  }
}
