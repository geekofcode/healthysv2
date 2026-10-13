package org.novasos.healthysv2.care;

import io.micrometer.core.instrument.MeterRegistry;
import java.sql.Timestamp;
import java.time.*;
import java.util.*;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.stereotype.Component;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;

@Component
class AiWorker {
  private final JdbcTemplate db;
  private final TransactionTemplate tx;
  private final AiAuthorization authorization;
  private final AiSnapshot snapshots;
  private final AiPolicy policy;
  private final AiAudioStorage audio;
  private final AiPythonClient python;
  private final MeterRegistry metrics;

  AiWorker(
      JdbcTemplate db,
      PlatformTransactionManager tm,
      AiAuthorization authorization,
      AiSnapshot snapshots,
      AiPolicy policy,
      AiAudioStorage audio,
      AiPythonClient python,
      MeterRegistry metrics) {
    this.db = db;
    this.tx = new TransactionTemplate(tm);
    this.authorization = authorization;
    this.snapshots = snapshots;
    this.policy = policy;
    this.audio = audio;
    this.python = python;
    this.metrics = metrics;
  }

  @Scheduled(fixedDelayString = "${healthys.ai.poll-interval-ms:2000}")
  public void poll() {
    try {
      purge();
      if (!policy.enabled()) return;
      var job = tx.execute(s -> claim());
      if (job != null) execute(job);
    } catch (RuntimeException ignored) {
      metrics.counter("healthys.ai.worker.errors", "stage", "poll").increment();
    }
  }

  private Map<String, Object> claim() {
    db.update(
        "update care.ai_job set"
            + " status='FAILED',error_code='AI_DEADLINE_EXCEEDED',finished_at=clock_timestamp(),lease_token=null,lease_until=null"
            + " where status in ('QUEUED','RUNNING') and deadline_at<=clock_timestamp()");
    var rows =
        db.queryForList(
            """
            select * from care.ai_job where deadline_at>clock_timestamp() and attempts<? and
            ((status='QUEUED' and next_attempt_at<=clock_timestamp()) or (status='RUNNING' and lease_until<=clock_timestamp()))
            order by created_at for update skip locked limit 1
            """,
            policy.maxAttempts());
    if (rows.isEmpty()) return null;
    var job = rows.getFirst();
    UUID lease = UUID.randomUUID();
    db.update(
        "update care.ai_job set"
            + " status='RUNNING',attempts=attempts+1,lease_token=?,lease_until=clock_timestamp()+?*interval"
            + " '1 second' where id=?",
        lease,
        policy.timeout().plusSeconds(30).getSeconds(),
        job.get("id"));
    job.put("lease_token", lease);
    job.put("attempts", ((Number) job.get("attempts")).intValue() + 1);
    return job;
  }

  private void execute(Map<String, Object> job) {
    long started = System.nanoTime();
    String outcome = "failed";
    UUID id = (UUID) job.get("id");
    try {
      // Separate committed claim, short authorization transaction, remote call, then fresh
      // authorization on result commit.
      tx.executeWithoutResult(
          s -> {
            lockOrg(job);
            requireLease(job);
            authorize(job);
          });
      byte[] bytes =
          job.get("audio_key") == null ? null : audio.get(job.get("audio_key").toString());
      if (bytes != null && bytes.length > policy.maxAudioBytes())
        throw new AiPythonClient.Failure(false, "INVALID_AUDIO_SIZE");
      tx.executeWithoutResult(
          s -> {
            lockOrg(job);
            requireLease(job);
            authorize(job);
          });
      var result =
          python.generate(
              id,
              AiDtos.Kind.valueOf(job.get("kind").toString()),
              snapshots.decode(job.get("snapshot").toString()),
              (String) job.get("notes"),
              bytes,
              (String) job.get("audio_mime_type"));
      tx.executeWithoutResult(
          s -> {
            lockOrg(job);
            requireLease(job);
            authorize(job);
            int updated =
                db.update(
                    """
                    update care.ai_job set status='SUCCEEDED',output=?::jsonb,model=?,finished_at=clock_timestamp(),error_code=null,lease_token=null,lease_until=null
                    where id=? and status='RUNNING' and lease_token=? and deadline_at>clock_timestamp()
                    """,
                    snapshots.encode(result.output()),
                    result.model(),
                    id,
                    job.get("lease_token"));
            if (updated != 1) throw new AiPythonClient.Failure(false, "AI_DEADLINE_EXCEEDED");
          });
      outcome = "succeeded";
    } catch (AccessDeniedException
        | org.novasos.healthysv2.shared.api.error.ResourceNotFoundException denied) {
      fail(job, false, "AI_ACCESS_REVOKED", "CANCELLED");
      outcome = "cancelled";
    } catch (AiPythonClient.Failure failure) {
      fail(job, failure.retryable, failure.code, "FAILED");
    } catch (RuntimeException failure) {
      fail(job, true, "AI_PROVIDER_UNAVAILABLE", "FAILED");
    } finally {
      metrics
          .counter("healthys.ai.jobs", "kind", job.get("kind").toString(), "outcome", outcome)
          .increment();
      metrics
          .timer("healthys.ai.execution", "kind", job.get("kind").toString())
          .record(Duration.ofNanos(System.nanoTime() - started));
      purge();
    }
  }

  private void requireLease(Map<String, Object> job) {
    Integer n =
        db.queryForObject(
            "select count(*) from care.ai_job where id=? and status='RUNNING' and lease_token=? and"
                + " lease_until>clock_timestamp() and deadline_at>clock_timestamp()",
            Integer.class,
            job.get("id"),
            job.get("lease_token"));
    if (n == null || n != 1) throw new AiPythonClient.Failure(false, "AI_LEASE_EXPIRED");
  }

  private void lockOrg(Map<String, Object> job) {
    db.queryForObject(
        "select id from organization.organization where id=? for update",
        UUID.class,
        job.get("organization_id"));
  }

  private void authorize(Map<String, Object> job) {
    authorization.require(
        (UUID) job.get("visit_id"), (UUID) job.get("actor_id"), (UUID) job.get("organization_id"));
    if (job.get("audio_expires_at") instanceof Timestamp expiry
        && !expiry.toInstant().isAfter(Instant.now()))
      throw new AiPythonClient.Failure(false, "AI_AUDIO_EXPIRED");
  }

  private void fail(Map<String, Object> job, boolean retryable, String code, String terminal) {
    tx.executeWithoutResult(
        s -> {
          int attempt = ((Number) job.get("attempts")).intValue();
          boolean retry =
              retryable
                  && attempt < policy.maxAttempts()
                  && ((Timestamp) job.get("deadline_at"))
                      .toInstant()
                      .isAfter(Instant.now().plusSeconds(10));
          db.update(
              """
              update care.ai_job set status=?,error_code=?,next_attempt_at=clock_timestamp()+?*interval '1 second',finished_at=case when ?='QUEUED' then null else clock_timestamp() end,lease_token=null,lease_until=null
              where id=? and status='RUNNING' and lease_token=?
              """,
              retry ? "QUEUED" : terminal,
              code,
              Math.min(60, 5 * (1 << Math.min(attempt, 5))),
              retry ? "QUEUED" : terminal,
              job.get("id"),
              job.get("lease_token"));
        });
  }

  @Scheduled(fixedDelayString = "${healthys.ai.audio-sweep-interval-ms:60000}")
  public void sweepOrphanAudio() {
    try {
      audio.sweep(policy.audioTtl());
    } catch (RuntimeException ignored) {
      metrics.counter("healthys.ai.audio.purge.errors").increment();
    }
  }

  private void purge() {
    db.update(
        "update care.ai_job set"
            + " status='FAILED',error_code='AI_DEADLINE_EXCEEDED',finished_at=clock_timestamp(),lease_token=null,lease_until=null"
            + " where status in ('QUEUED','RUNNING') and deadline_at<=clock_timestamp()");
    // Audio is disposable, even after failures, cancellation, process restart, or while the feature
    // is disabled.
    db.update(
        "update care.ai_job set"
            + " status='FAILED',error_code='AI_ATTEMPTS_EXHAUSTED',finished_at=clock_timestamp(),lease_token=null,lease_until=null"
            + " where status='RUNNING' and lease_until<=clock_timestamp() and attempts>=?",
        policy.maxAttempts());
    var rows =
        db.queryForList(
            "select id,audio_key from care.ai_job where audio_key is not null and"
                + " (audio_expires_at<=clock_timestamp() or status in"
                + " ('SUCCEEDED','FAILED','CANCELLED')) limit 100");
    for (var row : rows)
      try {
        audio.delete(row.get("audio_key").toString());
        db.update(
            "update care.ai_job set audio_key=null where id=? and audio_key=?",
            row.get("id"),
            row.get("audio_key"));
      } catch (RuntimeException ignored) {
        metrics.counter("healthys.ai.audio.purge.errors").increment();
      }
  }
}
