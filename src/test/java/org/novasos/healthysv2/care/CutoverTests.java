package org.novasos.healthysv2.care;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

import com.fasterxml.jackson.databind.ObjectMapper;
import java.util.*;
import org.junit.jupiter.api.Test;
import org.novasos.healthysv2.audit.AuditTrail;
import org.novasos.healthysv2.patient.CurrentUserContext;
import org.novasos.healthysv2.shared.api.error.BusinessRuleException;
import org.springframework.jdbc.core.JdbcTemplate;

class CutoverTests {
  final JdbcTemplate db = mock(JdbcTemplate.class);
  final CurrentUserContext users = mock(CurrentUserContext.class);
  final CutoverService service =
      new CutoverService(db, new ObjectMapper(), mock(AuditTrail.class), users);
  final UUID run = UUID.randomUUID();

  void administrator() {
    when(users.current())
        .thenReturn(
            new CurrentUserContext.UserContext(UUID.randomUUID(), null, Set.of("PLATFORM_ADMIN")));
  }

  @Test
  void cutoverCannotBeActivatedByAnEstablishmentAdministrator() {
    when(users.current())
        .thenReturn(
            new CurrentUserContext.UserContext(
                UUID.randomUUID(), UUID.randomUUID(), Set.of("HOSPITAL_ADMIN")));
    assertThrows(
        org.springframework.security.access.AccessDeniedException.class,
        () -> service.activate(run));
    verifyNoInteractions(db);
  }

  @Test
  void dryRunDoesNotQualifyForRemovingTheHistoricalPath() {
    administrator();
    when(db.queryForList(contains("where id=? for update"), eq(run)))
        .thenReturn(List.of(Map.of("status", "PLANNED")));
    assertThrows(BusinessRuleException.class, () -> service.activate(run));
    verify(db, never()).update(anyString(), any(Object[].class));
  }

  @Test
  void verifiedImportWithoutRealRestoreEvidenceCannotActivate() {
    administrator();
    when(db.queryForList(contains("where id=? for update"), eq(run)))
        .thenReturn(
            List.of(
                Map.of("status", "VERIFIED", "evidence", "{\"journey\":{\"validated\":true}}")));
    assertThrows(BusinessRuleException.class, () -> service.activate(run));
    verify(db, never()).update(anyString(), any(Object[].class));
  }

  @Test
  void successFlagsWithoutVerificationArtifactFingerprintsCannotActivate() {
    administrator();
    when(db.queryForList(contains("where id=? for update"), eq(run)))
        .thenReturn(
            List.of(
                Map.of(
                    "status",
                    "VERIFIED",
                    "evidence",
                    "{\"journey\":{\"validated\":true},\"restoration\":{\"validated\":true}}")));
    assertThrows(BusinessRuleException.class, () -> service.activate(run));
    verify(db, never()).update(anyString(), any(Object[].class));
  }

  @Test
  void defaultStateKeepsLegacyUntilVerification() {
    assertEquals("LEGACY", service.read().mode());
    assertFalse(service.read().migrationValidated());
  }

  @Test
  void retiredCommandsAreRejectedEvenIfOldUrlIsCalledDirectly() {
    when(db.queryForList(contains("left join care.migration_run")))
        .thenReturn(List.of(Map.of("mode", "CARE_ONLY", "status", "VERIFIED", "evidence", "{}")));
    assertThrows(BusinessRuleException.class, service::requireLegacyWrite);
  }
}
