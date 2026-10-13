package org.novasos.healthysv2.care;

import java.sql.Timestamp;
import java.time.*;
import java.util.*;
import org.novasos.healthysv2.audit.AuditTrail;
import org.novasos.healthysv2.patient.CurrentUserContext;
import org.novasos.healthysv2.shared.api.error.*;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.*;
import org.springframework.web.multipart.MultipartFile;

@Service
@Transactional
public class AiJobService {
  private final JdbcTemplate db;
  private final CurrentUserContext users;
  private final AiAuthorization authorization;
  private final AiSnapshot snapshots;
  private final AiPolicy policy;
  private final AiAudioStorage audio;
  private final CarePolicy care;
  private final AuditTrail audit;

  AiJobService(
      JdbcTemplate db,
      CurrentUserContext users,
      AiAuthorization authorization,
      AiSnapshot snapshots,
      AiPolicy policy,
      AiAudioStorage audio,
      CarePolicy care,
      AuditTrail audit) {
    this.db = db;
    this.users = users;
    this.authorization = authorization;
    this.snapshots = snapshots;
    this.policy = policy;
    this.audio = audio;
    this.care = care;
    this.audit = audit;
  }

  public AiDtos.Job create(UUID visitId, String key, AiDtos.Request request, MultipartFile file) {
    if (!policy.enabled())
      throw new BusinessRuleException("AI_UNAVAILABLE", "The assistant is temporarily unavailable");
    var context = current(visitId);
    lock(context.organizationId());
    db.queryForObject(
        "select id from identity.person where id=? for update", UUID.class, context.actorId());
    context = current(visitId);
    if (key == null || !key.matches("[A-Za-z0-9._:-]{1,128}"))
      throw new IllegalArgumentException("INVALID_IDEMPOTENCY_KEY");
    if (request == null || request.kind() == null)
      throw new IllegalArgumentException("AI_KIND_REQUIRED");
    String notes = request.notes() == null ? "" : request.notes().trim();
    if (notes.length() > 30000) throw new IllegalArgumentException("AI_NOTES_TOO_LONG");
    if (request.kind() == AiDtos.Kind.REPORT && notes.isBlank())
      throw new IllegalArgumentException("AI_NOTES_REQUIRED");
    byte[] bytes = readAudio(request.kind(), file);
    String mime = file == null ? null : normalizeMime(file.getContentType());
    String fingerprint =
        AiSnapshot.hashText(
            request.kind()
                + "\n"
                + notes
                + "\n"
                + mime
                + "\n"
                + (bytes == null ? "" : Base64.getEncoder().encodeToString(bytes)));
    var existing =
        db.queryForList(
            "select * from care.ai_job where visit_id=? and actor_id=? and idempotency_key=?",
            visitId,
            context.actorId(),
            key);
    if (!existing.isEmpty()) {
      if (!fingerprint.equals(existing.getFirst().get("request_hash")))
        throw new ConflictException(
            "AI_IDEMPOTENCY_CONFLICT", "The key was used for another request");
      return response(existing.getFirst(), context);
    }
    Integer outstanding =
        db.queryForObject(
            "select count(*) from care.ai_job where actor_id=? and status in ('QUEUED','RUNNING')",
            Integer.class,
            context.actorId());
    if (outstanding != null && outstanding >= 5)
      throw new BusinessRuleException("AI_TOO_MANY_JOBS", "Wait for the current assistant jobs");
    UUID id = UUID.randomUUID();
    Instant now = Instant.now();
    var snapshot =
        request.kind() == AiDtos.Kind.TRANSCRIPTION
            ? Map.<String, Object>of("sources", List.of())
            : snapshots.build(context);
    String audioKey = null;
    if (bytes != null) {
      audioKey = "ai-temporary/" + context.organizationId() + "/" + id;
      audio.put(audioKey, bytes, mime);
      registerRollback(audioKey);
    }
    db.update(
        """
        insert into care.ai_job(id,visit_id,actor_id,organization_id,kind,idempotency_key,request_hash,snapshot,snapshot_hash,notes,
        created_at,deadline_at,audio_key,audio_mime_type,audio_expires_at,retention_expires_at)
        values(?,?,?,?,?,?,?,?::jsonb,?,?,?,?,?,?,?,?)
        """,
        id,
        visitId,
        context.actorId(),
        context.organizationId(),
        request.kind().name(),
        key,
        fingerprint,
        snapshots.encode(snapshot),
        snapshots.hash(snapshot),
        notes,
        Timestamp.from(now),
        Timestamp.from(now.plus(policy.deadline())),
        audioKey,
        mime,
        bytes == null ? null : Timestamp.from(now.plus(policy.audioTtl())),
        Timestamp.from(now.atZone(ZoneOffset.UTC).plusYears(care.retentionYears()).toInstant()));
    audit.change(
        context.actorId(),
        context.organizationId(),
        "CARE",
        "AiJob",
        id,
        "QUEUE",
        null,
        Map.of("kind", request.kind().name()));
    return get(visitId, id);
  }

  @Transactional(readOnly = true)
  public List<AiDtos.Job> list(UUID visitId) {
    var c = current(visitId);
    String currentHash = snapshots.hash(snapshots.build(c));
    return db
        .queryForList(
            "select * from care.ai_job where visit_id=? and actor_id=? and organization_id=? order"
                + " by created_at desc limit 50",
            visitId,
            c.actorId(),
            c.organizationId())
        .stream()
        .map(r -> response(r, c, currentHash))
        .toList();
  }

  @Transactional(readOnly = true)
  public AiDtos.Job get(UUID visitId, UUID jobId) {
    var c = current(visitId);
    return response(row(visitId, jobId, c), c);
  }

  public UUID requireReportProvenance(UUID visitId, UUID jobId) {
    var c = current(visitId);
    var r = row(visitId, jobId, c);
    if (!"REPORT".equals(r.get("kind")) || !"SUCCEEDED".equals(r.get("status")))
      throw new BusinessRuleException(
          "AI_REPORT_NOT_READY", "A successful report proposal is required");
    if (obsolete(r, c))
      throw new ConflictException(
          "AI_RESULT_OBSOLETE", "Refresh the assistant proposal before applying it");
    return jobId;
  }

  private Map<String, Object> row(UUID visit, UUID job, AiAuthorization.Context c) {
    var rows =
        db.queryForList(
            "select * from care.ai_job where id=? and visit_id=? and actor_id=? and"
                + " organization_id=?",
            job,
            visit,
            c.actorId(),
            c.organizationId());
    if (rows.isEmpty()) throw new ResourceNotFoundException("AiJob", job);
    return rows.getFirst();
  }

  private AiAuthorization.Context current(UUID visit) {
    var u = users.current();
    if (!u.has("DOCTOR")) throw new AccessDeniedException("ASSIGNED_DOCTOR_REQUIRED");
    return authorization.require(visit, u.personId(), u.organizationId());
  }

  private void lock(UUID org) {
    db.queryForObject(
        "select id from organization.organization where id=? for update", UUID.class, org);
  }

  private boolean obsolete(Map<String, Object> row, AiAuthorization.Context c) {
    if ("TRANSCRIPTION".equals(row.get("kind"))) return false;
    return !Objects.equals(row.get("snapshot_hash"), snapshots.hash(snapshots.build(c)));
  }

  private AiDtos.Job response(Map<String, Object> r, AiAuthorization.Context c) {
    return response(r, c, null);
  }

  private AiDtos.Job response(
      Map<String, Object> r, AiAuthorization.Context c, String currentHash) {
    return new AiDtos.Job(
        (UUID) r.get("id"),
        AiDtos.Kind.valueOf((String) r.get("kind")),
        (String) r.get("status"),
        r.get("output") == null ? null : snapshots.decode(r.get("output").toString()),
        (currentHash == null
            ? obsolete(r, c)
            : !"TRANSCRIPTION".equals(r.get("kind"))
                && !Objects.equals(currentHash, r.get("snapshot_hash"))),
        (String) r.get("error_code"),
        ((Timestamp) r.get("created_at")).toInstant(),
        (String) r.get("model"));
  }

  private byte[] readAudio(AiDtos.Kind kind, MultipartFile file) {
    if (kind != AiDtos.Kind.TRANSCRIPTION) {
      if (file != null) throw new IllegalArgumentException("AUDIO_NOT_ALLOWED");
      return null;
    }
    if (file == null || file.isEmpty() || file.getSize() > policy.maxAudioBytes())
      throw new IllegalArgumentException("INVALID_AUDIO_SIZE");
    normalizeMime(file.getContentType());
    try (var input = file.getInputStream()) {
      byte[] bytes = input.readNBytes(Math.toIntExact(policy.maxAudioBytes() + 1));
      if (bytes.length > policy.maxAudioBytes())
        throw new IllegalArgumentException("INVALID_AUDIO_SIZE");
      return bytes;
    } catch (java.io.IOException e) {
      throw new BusinessRuleException("AUDIO_UPLOAD_FAILED", "Upload the dictation again");
    }
  }

  static String normalizeMime(String mime) {
    String value = mime == null ? "" : mime.split(";", 2)[0].trim().toLowerCase(Locale.ROOT);
    if (!Set.of("audio/webm", "audio/mp4", "audio/mpeg", "audio/wav", "audio/x-wav", "audio/ogg")
        .contains(value)) throw new IllegalArgumentException("UNSUPPORTED_AUDIO_TYPE");
    return value;
  }

  private void registerRollback(String key) {
    if (TransactionSynchronizationManager.isSynchronizationActive())
      TransactionSynchronizationManager.registerSynchronization(
          new TransactionSynchronization() {
            @Override
            public void afterCompletion(int status) {
              if (status != STATUS_COMMITTED)
                try {
                  audio.delete(key);
                } catch (RuntimeException ignored) {
                  /* Orphan is bounded by MinIO lifecycle fallback configured for ai-temporary/. */
                }
            }
          });
  }
}
