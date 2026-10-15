package org.novasos.healthysv2.lifecycle;

import java.util.*;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.springframework.transaction.support.TransactionTemplate;

/** Owned episode graph only. Unknown dependencies are a visible block, never a cascade. */
@Component
class EpisodeRetentionPurger {
  private static final Map<String, String> KEYS =
      Map.ofEntries(
          Map.entry("care.reassignment_request", "id"),
          Map.entry("care.appointment_request", "id"),
          Map.entry("patienthealth.observation", "id"),
          Map.entry("care.care_episode", "id"),
          Map.entry("care.care_visit", "id"),
          Map.entry("care.reception_intake", "episode_id"),
          Map.entry("care.visit_intake", "visit_id"),
          Map.entry("care.visit_report", "visit_id"),
          Map.entry("care.visit_report_revision", "id"),
          Map.entry("care.visit_prescription", "visit_id"),
          Map.entry("care.visit_prescription_revision", "id"),
          Map.entry("care.visit_schedule_history", "id"),
          Map.entry("care.queue_entry", "visit_id"),
          Map.entry("care.follow_up_request", "visit_id"),
          Map.entry("care.ai_job", "id"),
          Map.entry("care.visit_document", "document_id"),
          Map.entry("document.document", "id"),
          Map.entry("document.document_access", "id"),
          Map.entry("document.document_link", "id"),
          Map.entry("billing.care_invoice", "episode_id"),
          Map.entry("billing.invoice", "id"),
          Map.entry("billing.invoice_item", "id"),
          Map.entry("billing.payment", "id"),
          Map.entry("billing.payment_transaction", "id"),
          Map.entry("billing.cash_operation", "id"),
          Map.entry("billing.cash_closure_receipt", "cash_operation_id"),
          Map.entry("billing.cash_closure", "id"));
  private final JdbcTemplate jdbc;
  private final TransactionTemplate tx;
  private final boolean enabled;
  private final PrivateObjectPurger objectPurger;
  private final org.novasos.healthysv2.ObjectStorageConfiguration.StorageProperties
      storageProperties;

  EpisodeRetentionPurger(
      JdbcTemplate jdbc,
      TransactionTemplate tx,
      PrivateObjectPurger objectPurger,
      org.novasos.healthysv2.ObjectStorageConfiguration.StorageProperties storageProperties,
      @Value("${DATA_RETENTION_PURGE_ENABLED:false}") boolean enabled) {
    this.jdbc = jdbc;
    this.tx = tx;
    this.enabled = enabled;
    this.objectPurger = objectPurger;
    this.storageProperties = storageProperties;
  }

  @Scheduled(fixedDelayString = "${DATA_RETENTION_POLL_MILLIS:60000}")
  void tick() {
    if (!enabled) return;
    jdbc.update(
        """
        insert into lifecycle.retention_job(episode_id,status)
        select id,'PENDING' from care.care_episode where retention_expires_at<=now()
        on conflict(episode_id) do nothing
        """);
    var ids =
        jdbc.queryForList(
            "select episode_id from lifecycle.retention_job where status in"
                + " ('PENDING','BLOCKED','FAILED') and next_attempt_at<=now() order by created_at"
                + " limit 10",
            UUID.class);
    for (UUID id : ids)
      try {
        tx.executeWithoutResult(status -> purge(id));
        confirmBlobs(id);
        tx.executeWithoutResult(status -> purge(id));
      } catch (RuntimeException ex) {
        jdbc.update(
            "update lifecycle.retention_job set"
                + " status='FAILED',attempts=attempts+1,reason='DEPENDENCY_OR_STORAGE_RETRY',next_attempt_at=now()+interval"
                + " '1 hour' where episode_id=?",
            id);
      }
  }

  void purge(UUID episode) {
    var jobs =
        jdbc.queryForList(
            "select episode_id from lifecycle.retention_job where episode_id=? and"
                + " status<>'COMPLETED' for update skip locked",
            UUID.class,
            episode);
    if (jobs.isEmpty()) return;
    var roots =
        jdbc.queryForList(
            "select id from care.care_episode where id=? and retention_expires_at<=now() and"
                + " (expires_at is null or expires_at<=now()) for update",
            UUID.class,
            episode);
    if (roots.isEmpty()) {
      block(episode, "EPISODE_NOT_EXPIRED_OR_ACTIVE");
      return;
    }
    Map<String, Set<UUID>> graph = new LinkedHashMap<>();
    graph.put("care.care_episode", new LinkedHashSet<>(roots));
    boolean changed = true;
    while (changed) {
      changed = false;
      for (var parent : new ArrayList<>(graph.entrySet())) {
        for (Reference ref : references(parent.getKey())) {
          var ids = childIds(ref, parent.getValue());
          if (ids.isEmpty()) continue;
          if (!KEYS.containsKey(ref.table())) {
            block(episode, "DEPENDENCY_REQUIRES_REVIEW:" + ref.table());
            return;
          }
          Set<UUID> target = graph.computeIfAbsent(ref.table(), x -> new LinkedHashSet<>());
          if (target.addAll(ids)) changed = true;
        }
        if (parent.getKey().equals("document.document_link")) {
          var links =
              jdbc.query(
                  "select resource_type,resource_id from document.document_link where id in ("
                      + placeholders(parent.getValue())
                      + ")",
                  (rs, n) -> new Object[] {rs.getString(1), rs.getObject(2, UUID.class)},
                  parent.getValue().toArray());
          for (var link : links) {
            if (!"CARE_VISIT".equals(link[0])) {
              block(episode, "DOCUMENT_SHARED_RESOURCE_REQUIRES_REVIEW");
              return;
            }
            if (graph
                .computeIfAbsent("care.care_visit", x -> new LinkedHashSet<>())
                .add((UUID) link[1])) changed = true;
          }
        }
        for (Outgoing ref : outgoing(parent.getKey())) {
          if (!KEYS.containsKey(ref.table())) continue;
          var ids =
              jdbc.queryForList(
                  "select "
                      + DeletionWorker.quote(ref.column())
                      + " from "
                      + parent.getKey()
                      + " where "
                      + KEYS.get(parent.getKey())
                      + " in ("
                      + placeholders(parent.getValue())
                      + ") and "
                      + DeletionWorker.quote(ref.column())
                      + " is not null",
                  UUID.class,
                  parent.getValue().toArray());
          if (ids.isEmpty()) continue;
          Set<UUID> target = graph.computeIfAbsent(ref.table(), x -> new LinkedHashSet<>());
          if (target.addAll(ids)) changed = true;
        }
      }
    }
    for (var entry : graph.entrySet()) {
      String table = entry.getKey(),
          key = KEYS.get(table),
          placeholders = placeholders(entry.getValue());
      var expired =
          jdbc.queryForList(
              "select "
                  + key
                  + " from "
                  + table
                  + " where "
                  + key
                  + " in ("
                  + placeholders
                  + ") and retention_expires_at<=now() for update",
              UUID.class,
              entry.getValue().toArray());
      if (expired.size() != entry.getValue().size()) {
        block(episode, "DEPENDENCY_NOT_EXPIRED:" + table);
        return;
      }
    }
    var episodes = graph.getOrDefault("care.care_episode", Set.of());
    if (jdbc.queryForObject(
            "select count(*) from care.care_episode where id in ("
                + placeholders(episodes)
                + ") and expires_at>now()",
            Integer.class,
            episodes.toArray())
        > 0) {
      block(episode, "SHARED_EPISODE_STILL_ACTIVE");
      return;
    }
    var visits = graph.getOrDefault("care.care_visit", Set.of());
    if (!visits.isEmpty()
        && jdbc.queryForObject(
                "select count(*) from care.care_visit where id in ("
                    + placeholders(visits)
                    + ") and status in"
                    + " ('IN_PROGRESS','SCHEDULED','AWAITING_SCHEDULING','PENDING_PAYMENT')",
                Integer.class,
                visits.toArray())
            > 0) {
      block(episode, "VISIT_STILL_ACTIVE");
      return;
    }
    var ai = graph.getOrDefault("care.ai_job", Set.of());
    if (!ai.isEmpty()
        && jdbc.queryForObject(
                "select count(*) from care.ai_job where id in ("
                    + placeholders(ai)
                    + ") and (audio_key is not null or status in ('RUNNING','QUEUED'))",
                Integer.class,
                ai.toArray())
            > 0) {
      block(episode, "AI_AUDIO_OR_JOB_STILL_ACTIVE");
      return;
    }
    var documents = graph.getOrDefault("document.document", Set.of());
    boolean pending = false;
    for (UUID document : documents) {
      String key =
          jdbc.queryForObject(
              "select storage_key from document.document where id=?", String.class, document);
      jdbc.update(
          "update document.document set status='ARCHIVED' where id=? and status<>'ARCHIVED'",
          document);
      jdbc.update(
          "insert into lifecycle.object_purge(document_id,storage_key,episode_id) values(?,?,?) on"
              + " conflict(document_id) do nothing",
          document,
          key,
          episode);
      String objectStatus =
          jdbc.queryForObject(
              "select status from lifecycle.object_purge where document_id=?",
              String.class,
              document);
      if (!"DONE".equals(objectStatus)) pending = true;
    }
    if (pending) {
      for (var node : graph.entrySet())
        for (UUID id : node.getValue())
          jdbc.update(
              "insert into lifecycle.retention_reservation(resource_table,resource_id,episode_id)"
                  + " values(?,?,?) on conflict do nothing",
              node.getKey(),
              id,
              episode);
      jdbc.update(
          "update lifecycle.retention_job set reason='PRIVATE_BLOBS_RESERVED' where episode_id=?",
          episode);
      return;
    }
    // Flag is transaction-local and set only after validating every node and object.
    jdbc.queryForObject("select set_config('healthys.retention_purge','on',true)", String.class);
    for (UUID document : documents)
      jdbc.update(
          "delete from lifecycle.object_purge where document_id=? and status='DONE'", document);
    // Delete leaf tables first. Self-referential revision chains are removed in one statement.
    int removed = 0;
    while (!graph.isEmpty()) {
      boolean progress = false;
      for (String table : new ArrayList<>(graph.keySet())) {
        boolean children =
            references(table).stream()
                .anyMatch(ref -> !ref.table().equals(table) && graph.containsKey(ref.table()));
        if (children) continue;
        var ids = graph.get(table);
        removed +=
            jdbc.update(
                "delete from "
                    + table
                    + " where "
                    + KEYS.get(table)
                    + " in ("
                    + placeholders(ids)
                    + ")",
                ids.toArray());
        graph.remove(table);
        progress = true;
      }
      if (!progress) throw new IllegalStateException("CYCLIC_RETENTION_GRAPH");
    }
    for (var document : documents)
      jdbc.update(
          "delete from lifecycle.retention_reservation where episode_id in (select episode_id from"
              + " lifecycle.retention_reservation where resource_table='document.document' and"
              + " resource_id=?)",
          document);
    jdbc.update("delete from lifecycle.retention_reservation where episode_id=?", episode);
    for (UUID completed : episodes)
      jdbc.update(
          "update lifecycle.retention_job set"
              + " status='COMPLETED',attempts=attempts+1,removed_count=?,reason='EXPIRED_OWNED_GRAPH_PURGED',completed_at=now()"
              + " where episode_id=?",
          removed,
          completed);
    jdbc.update(
        "insert into lifecycle.purge_event(resource_type,resource_id,outcome,reason)"
            + " values('care.care_episode',?,'REMOVED','Expired complete owned graph; identity and"
            + " shared external records preserved')",
        episode);
  }

  private List<Reference> references(String table) {
    return jdbc.query(
        """
        select ns.nspname||'.'||t.relname,a.attname from pg_constraint c
        join pg_class t on t.oid=c.conrelid join pg_namespace ns on ns.oid=t.relnamespace
        join pg_attribute a on a.attrelid=t.oid and a.attnum=any(c.conkey)
        where c.contype='f' and c.confrelid=?::regclass and ns.nspname<>'lifecycle'
        """,
        (rs, n) -> new Reference(rs.getString(1), rs.getString(2)),
        table);
  }

  void confirmBlobs(UUID episode) {
    var rows =
        jdbc.query(
            "select document_id,storage_key from lifecycle.object_purge where episode_id=? and"
                + " status='PENDING'",
            (rs, n) -> new Object[] {rs.getObject(1, UUID.class), rs.getString(2)},
            episode);
    for (var row : rows) {
      objectPurger.delete((String) row[1]);
      jdbc.update(
          "update lifecycle.object_purge set status='DONE',completed_at=now() where document_id=?",
          row[0]);
    }
  }

  private List<Outgoing> outgoing(String table) {
    return jdbc.query(
        """
        select ns.nspname||'.'||t.relname,a.attname from pg_constraint c
        join pg_class t on t.oid=c.confrelid join pg_namespace ns on ns.oid=t.relnamespace
        join pg_attribute a on a.attrelid=c.conrelid and a.attnum=any(c.conkey)
        where c.contype='f' and c.conrelid=?::regclass
        """,
        (rs, n) -> new Outgoing(rs.getString(1), rs.getString(2)),
        table);
  }

  private List<UUID> childIds(Reference ref, Set<UUID> parents) {
    String key = KEYS.get(ref.table());
    if (key == null) {
      Integer count =
          jdbc.queryForObject(
              "select count(*) from "
                  + quotedTable(ref.table())
                  + " where "
                  + DeletionWorker.quote(ref.column())
                  + " in ("
                  + placeholders(parents)
                  + ")",
              Integer.class,
              parents.toArray());
      return count == 0 ? List.of() : List.of(new UUID(0, 0));
    }
    return jdbc.queryForList(
        "select "
            + key
            + " from "
            + ref.table()
            + " where "
            + DeletionWorker.quote(ref.column())
            + " in ("
            + placeholders(parents)
            + ")",
        UUID.class,
        parents.toArray());
  }

  static String quotedTable(String value) {
    int separator = value.indexOf('.');
    if (separator < 1 || separator == value.length() - 1)
      throw new IllegalArgumentException("Qualified table is required");
    return DeletionWorker.quote(value.substring(0, separator))
        + "."
        + DeletionWorker.quote(value.substring(separator + 1));
  }

  private static String placeholders(Collection<?> ids) {
    return String.join(",", Collections.nCopies(ids.size(), "?"));
  }

  private void block(UUID episode, String reason) {
    jdbc.update(
        "update lifecycle.retention_job set"
            + " status='BLOCKED',attempts=attempts+1,reason=?,next_attempt_at=now()+interval '1"
            + " day' where episode_id=?",
        reason,
        episode);
  }

  record Reference(String table, String column) {}

  record Outgoing(String table, String column) {}
}
