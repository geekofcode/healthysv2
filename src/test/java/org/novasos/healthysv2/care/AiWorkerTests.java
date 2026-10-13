package org.novasos.healthysv2.care;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import java.sql.Timestamp;
import java.time.*;
import java.util.*;
import org.junit.jupiter.api.Test;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.transaction.*;
import org.springframework.transaction.support.SimpleTransactionStatus;

class AiWorkerTests {
  private final JdbcTemplate db = mock(JdbcTemplate.class);
  private final PlatformTransactionManager tm = mock(PlatformTransactionManager.class);
  private final AiAuthorization auth = mock(AiAuthorization.class);
  private final AiSnapshot json = mock(AiSnapshot.class);
  private final AiAudioStorage audio = mock(AiAudioStorage.class);
  private final AiPythonClient python = mock(AiPythonClient.class);
  private final AiPolicy policy =
      new AiPolicy(
          true,
          "http://ai",
          "a".repeat(32),
          Duration.ofSeconds(60),
          Duration.ofMinutes(15),
          Duration.ofHours(1),
          3,
          1000);
  private final AiWorker worker =
      new AiWorker(db, tm, auth, json, policy, audio, python, new SimpleMeterRegistry());
  private final UUID id = UUID.randomUUID(),
      visit = UUID.randomUUID(),
      actor = UUID.randomUUID(),
      org = UUID.randomUUID();

  private Map<String, Object> job() {
    Map<String, Object> j = new HashMap<>();
    j.put("id", id);
    j.put("visit_id", visit);
    j.put("actor_id", actor);
    j.put("organization_id", org);
    j.put("kind", "REPORT");
    j.put("attempts", 0);
    j.put("notes", "Dictation");
    j.put("snapshot", "{}");
    j.put("deadline_at", Timestamp.from(Instant.now().plusSeconds(900)));
    return j;
  }

  private void setup() {
    when(tm.getTransaction(any())).thenAnswer(x -> new SimpleTransactionStatus());
    when(db.queryForList(contains("for update skip locked limit 1"), eq(3)))
        .thenReturn(List.of(job()));
    when(db.queryForObject(
            contains("lease_until>clock_timestamp()"), eq(Integer.class), eq(id), any()))
        .thenReturn(1);
    when(auth.require(visit, actor, org))
        .thenReturn(new AiAuthorization.Context(visit, actor, org, UUID.randomUUID()));
    when(json.decode("{}")).thenReturn(Map.of("sources", List.of()));
    when(json.encode(any())).thenReturn("{}");
    when(db.update(anyString(), any(Object[].class))).thenReturn(1);
    when(python.generate(eq(id), eq(AiDtos.Kind.REPORT), anyMap(), anyString(), isNull(), isNull()))
        .thenReturn(new AiPythonClient.Result("model", Map.of("reason", "Test")));
  }

  @Test
  void queuedJobRechecksPermissionsBeforeProviderAndBeforePersistingOutput() {
    setup();
    worker.poll();
    verify(auth, times(3)).require(visit, actor, org);
    verify(python)
        .generate(eq(id), eq(AiDtos.Kind.REPORT), anyMap(), eq("Dictation"), isNull(), isNull());
    verify(db).update(contains("status='SUCCEEDED'"), eq("{}"), eq("model"), eq(id), any());
  }

  @Test
  void revokedPermissionDuringProviderCallDiscardsResultAndCancelsJob() {
    setup();
    when(auth.require(visit, actor, org))
        .thenReturn(
            new AiAuthorization.Context(visit, actor, org, UUID.randomUUID()),
            new AiAuthorization.Context(visit, actor, org, UUID.randomUUID()))
        .thenThrow(new AccessDeniedException("PATIENT_REVOKED_ACCESS"));
    worker.poll();
    verify(python).generate(any(), any(), anyMap(), anyString(), isNull(), isNull());
    verify(db, never()).update(contains("output=?::jsonb"), any(Object[].class));
    verify(db)
        .update(
            contains("status=?"),
            eq("CANCELLED"),
            eq("AI_ACCESS_REVOKED"),
            anyInt(),
            eq("CANCELLED"),
            eq(id),
            any());
  }

  @Test
  void expiredOrReclaimedLeaseNeverCallsProvider() {
    setup();
    when(db.queryForObject(
            contains("lease_until>clock_timestamp()"), eq(Integer.class), eq(id), any()))
        .thenReturn(0);
    worker.poll();
    verifyNoInteractions(python);
    verify(db)
        .update(
            contains("status=?"),
            eq("FAILED"),
            eq("AI_LEASE_EXPIRED"),
            anyInt(),
            eq("FAILED"),
            eq(id),
            any());
  }

  @Test
  void retryableFailureReturnsToDurableQueueWithBackoff() {
    setup();
    when(python.generate(any(), any(), anyMap(), anyString(), isNull(), isNull()))
        .thenThrow(new AiPythonClient.Failure(true, "AI_PROVIDER_UNAVAILABLE"));
    worker.poll();
    verify(db)
        .update(
            contains("status=?"),
            eq("QUEUED"),
            eq("AI_PROVIDER_UNAVAILABLE"),
            eq(10),
            eq("QUEUED"),
            eq(id),
            any());
  }

  @Test
  void invalidOutputFailsWithoutRetry() {
    setup();
    when(python.generate(any(), any(), anyMap(), anyString(), isNull(), isNull()))
        .thenThrow(new AiPythonClient.Failure(false, "AI_INVALID_OUTPUT"));
    worker.poll();
    verify(db)
        .update(
            contains("status=?"),
            eq("FAILED"),
            eq("AI_INVALID_OUTPUT"),
            anyInt(),
            eq("FAILED"),
            eq(id),
            any());
  }

  @Test
  void purgeRunsWhenAiExecutionDisabledAndKeepsRetryOnDeletionFailure() {
    var disabled =
        new AiPolicy(
            false,
            "http://ai",
            "",
            Duration.ofSeconds(60),
            Duration.ofMinutes(15),
            Duration.ofHours(1),
            3,
            1000);
    var w = new AiWorker(db, tm, auth, json, disabled, audio, python, new SimpleMeterRegistry());
    when(db.queryForList(contains("audio_key is not null")))
        .thenReturn(List.of(Map.of("id", id, "audio_key", "private/audio")));
    doThrow(new RuntimeException("storage unavailable")).when(audio).delete("private/audio");
    w.poll();
    verify(audio).delete("private/audio");
    verify(db, never()).update(contains("audio_key=null"), any(Object[].class));
    verifyNoInteractions(python);
  }
}
