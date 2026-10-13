package org.novasos.healthysv2.care;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

import java.sql.Timestamp;
import java.time.*;
import java.util.*;
import org.junit.jupiter.api.Test;
import org.novasos.healthysv2.audit.AuditTrail;
import org.novasos.healthysv2.patient.CurrentUserContext;
import org.novasos.healthysv2.shared.api.error.*;
import org.springframework.jdbc.core.JdbcTemplate;

class AiJobServiceTests {
  final JdbcTemplate db = mock(JdbcTemplate.class);
  final CurrentUserContext users = mock(CurrentUserContext.class);
  final AiAuthorization authorization = mock(AiAuthorization.class);
  final AiSnapshot snapshots = mock(AiSnapshot.class);
  final AiAudioStorage audio = mock(AiAudioStorage.class);
  final UUID visit = UUID.randomUUID(),
      actor = UUID.randomUUID(),
      org = UUID.randomUUID(),
      job = UUID.randomUUID();
  final AiAuthorization.Context context =
      new AiAuthorization.Context(visit, actor, org, UUID.randomUUID());
  final AiPolicy policy =
      new AiPolicy(
          true,
          "http://ai",
          "s".repeat(32),
          Duration.ofSeconds(60),
          Duration.ofMinutes(15),
          Duration.ofHours(1),
          3,
          1000);
  final AiJobService service =
      new AiJobService(
          db,
          users,
          authorization,
          snapshots,
          policy,
          audio,
          new CarePolicy(Duration.ofDays(7), 30),
          mock(AuditTrail.class));

  void setup() {
    when(users.current())
        .thenReturn(new CurrentUserContext.UserContext(actor, org, Set.of("DOCTOR")));
    when(authorization.require(visit, actor, org)).thenReturn(context);
    when(snapshots.build(context)).thenReturn(Map.of("sources", List.of()));
    when(snapshots.hash(any())).thenReturn("current");
  }

  Map<String, Object> existing(String kind, String hash) {
    return new HashMap<>(
        Map.of(
            "id",
            job,
            "kind",
            kind,
            "status",
            "SUCCEEDED",
            "snapshot_hash",
            hash,
            "created_at",
            Timestamp.from(Instant.now()),
            "request_hash",
            AiSnapshot.hashText("SUMMARY\n\nnull\n")));
  }

  @Test
  void repeatedRequestReturnsSameJobWithoutNewInputOrUpload() {
    setup();
    when(db.queryForList(contains("idempotency_key=?"), eq(visit), eq(actor), eq("same")))
        .thenReturn(List.of(existing("SUMMARY", "current")));
    assertEquals(
        job,
        service.create(visit, "same", new AiDtos.Request(AiDtos.Kind.SUMMARY, null), null).id());
    verify(db, never()).update(anyString(), any(Object[].class));
    verifyNoInteractions(audio);
  }

  @Test
  void reusedKeyWithDifferentNotesDoesNotCreateAnotherBillableJob() {
    setup();
    when(db.queryForList(contains("idempotency_key=?"), eq(visit), eq(actor), eq("same")))
        .thenReturn(List.of(existing("SUMMARY", "current")));
    assertThrows(
        ConflictException.class,
        () ->
            service.create(
                visit, "same", new AiDtos.Request(AiDtos.Kind.REPORT, "Different notes"), null));
    verify(db, never()).update(anyString(), any(Object[].class));
    verifyNoInteractions(audio);
  }

  @Test
  void proposalMustBelongToCurrentActorVisitAndOrganization() {
    setup();
    assertThrows(
        ResourceNotFoundException.class, () -> service.requireReportProvenance(visit, job));
    verify(db)
        .queryForList(
            contains("id=? and visit_id=? and actor_id=? and organization_id=?"),
            eq(job),
            eq(visit),
            eq(actor),
            eq(org));
  }

  @Test
  void staleProposalCannotBeAppliedToCurrentReport() {
    setup();
    when(db.queryForList(contains("id=? and visit_id=?"), eq(job), eq(visit), eq(actor), eq(org)))
        .thenReturn(List.of(existing("REPORT", "old")));
    assertThrows(ConflictException.class, () -> service.requireReportProvenance(visit, job));
  }

  @Test
  void transcriptionIsNotAReportProposalAndDoesNotReadClinicalSnapshot() {
    setup();
    when(db.queryForList(contains("id=? and visit_id=?"), eq(job), eq(visit), eq(actor), eq(org)))
        .thenReturn(List.of(existing("TRANSCRIPTION", "unused")));
    assertFalse(service.get(visit, job).obsolete());
    assertThrows(BusinessRuleException.class, () -> service.requireReportProvenance(visit, job));
    verify(snapshots, never()).build(any());
  }

  @Test
  void globalRolesCannotBypassActiveAssignedDoctor() {
    when(users.current())
        .thenReturn(new CurrentUserContext.UserContext(actor, org, Set.of("PLATFORM_ADMIN")));
    assertThrows(
        org.springframework.security.access.AccessDeniedException.class, () -> service.list(visit));
    verifyNoInteractions(authorization, db, audio);
  }

  @Test
  void listingManyResultsBuildsTheCurrentSnapshotOnce() {
    setup();
    when(db.queryForList(contains("order by created_at desc"), eq(visit), eq(actor), eq(org)))
        .thenReturn(List.of(existing("REPORT", "current"), existing("SUMMARY", "current")));
    assertEquals(2, service.list(visit).size());
    verify(snapshots, times(1)).build(context);
  }
}
