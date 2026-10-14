package org.novasos.healthysv2.lifecycle;

import java.util.*;
import org.novasos.healthysv2.ObjectStorageConfiguration.StorageProperties;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.springframework.transaction.support.TransactionTemplate;

/** Each claimed job has a durable lease; SQL rolls back on errors, S3 DELETE is idempotent. */
@Component
class DeletionWorker {
  private final JdbcTemplate jdbc;
  private final TransactionTemplate tx;
  private final PrivateObjectPurger objectPurger;
  private final StorageProperties properties;
  private final boolean enabled;
  private final boolean retentionEnabled;

  DeletionWorker(
      JdbcTemplate jdbc,
      TransactionTemplate tx,
      PrivateObjectPurger objectPurger,
      StorageProperties properties,
      @Value("${DATA_DELETION_WORKER_ENABLED:false}") boolean enabled,
      @Value("${DATA_RETENTION_PURGE_ENABLED:false}") boolean retentionEnabled) {
    this.jdbc = jdbc;
    this.tx = tx;
    this.objectPurger = objectPurger;
    this.properties = properties;
    this.enabled = enabled;
    this.retentionEnabled = retentionEnabled;
  }

  @Scheduled(fixedDelayString = "${DATA_DELETION_POLL_MILLIS:30000}")
  void tick() {
    if (enabled) {
      Lease lease = tx.execute(status -> claim());
      if (lease != null) process(lease);
    }
    if (retentionEnabled) {
      purgeExpiredDocuments();
      purgeExpiredPrivateRows();
    }
  }

  Lease claim() {
    var rows =
        jdbc.query(
            """
            select id,person_id from lifecycle.deletion_request
            where (status='QUEUED' or (status='RUNNING' and lease_until<now())) and next_attempt_at<=now()
            order by created_at for update skip locked limit 1
            """,
            (rs, n) ->
                new Lease(
                    rs.getObject(1, UUID.class), rs.getObject(2, UUID.class), UUID.randomUUID()));
    if (rows.isEmpty()) return null;
    Lease lease = rows.getFirst();
    jdbc.update(
        "update lifecycle.deletion_request set"
            + " status='RUNNING',lease_token=?,lease_until=now()+interval '10"
            + " minutes',attempts=attempts+1 where id=?",
        lease.token(),
        lease.id());
    return lease;
  }

  void process(Lease lease) {
    try {
      tx.executeWithoutResult(
          status -> {
            if (!lockLease(lease)) return;
            var ids =
                jdbc.query(
                    "select id from document.document where uploaded_by=? order by id for update",
                    (rs, n) -> rs.getObject(1, UUID.class),
                    lease.person());
            for (UUID id : ids) stageDocument(id, lease.id());
          });
      if (!leaseValid(lease)) return;
      drainOutbox(lease.id(), lease);
      tx.executeWithoutResult(status -> processLocked(lease));
    } catch (PartialProgress ex) {
      jdbc.update(
          "update lifecycle.deletion_request set"
              + " status='QUEUED',attempts=greatest(attempts-1,0),next_attempt_at=now(),lease_token=null,lease_until=null"
              + " where id=? and lease_token=?",
          lease.id(),
          lease.token());
    } catch (RuntimeException ex) {
      // Never persist exception strings: they can contain health data, keys or credentials.
      jdbc.update(
          """
          update lifecycle.deletion_request set status=case when failures+1>=5 then 'FAILED' else 'QUEUED' end,failures=failures+1,
          error_code='PURGE_RETRY_REQUIRED',next_attempt_at=now()+interval '5 minutes',lease_token=null,lease_until=null
          where id=? and lease_token=?
          """,
          lease.id(),
          lease.token());
    }
  }

  void processLocked(Lease lease) {
    if (!lockLease(lease)) return;
    jdbc.queryForObject(
        "select id from identity.person where id=? for update", UUID.class, lease.person());
    int removed = 0, retained = 0;
    var documents =
        jdbc.query(
            "select id from document.document where uploaded_by=? order by id for update",
            (rs, n) -> rs.getObject(1, UUID.class),
            lease.person());
    for (UUID document : documents) {
      retained++;
    }
    removed +=
        jdbc.queryForObject(
            "select count(*) from lifecycle.purge_event where request_id=? and outcome='REMOVED'",
            Integer.class,
            lease.id());
    removed +=
        jdbc.update("delete from identity.staff_account_outbox where person_id=?", lease.person());
    removed += jdbc.update("delete from identity.person_contact where person_id=?", lease.person());
    removed +=
        jdbc.update("delete from identity.emergency_contact where person_id=?", lease.person());
    var addresses =
        jdbc.queryForList(
            "select address_id from identity.person_address where person_id=?",
            UUID.class,
            lease.person());
    removed += jdbc.update("delete from identity.person_address where person_id=?", lease.person());
    for (UUID address : addresses) {
      if (!hasReferences("shared.address", address))
        removed += jdbc.update("delete from shared.address where id=?", address);
      else {
        retained++;
        event(
            lease.id(), "shared.address", address, "RETAINED", "Address is used by another record");
      }
    }
    removed +=
        jdbc.update("delete from identity.person_preferences where person_id=?", lease.person());
    removed +=
        jdbc.update("delete from notification.push_outbox where person_id=?", lease.person());
    removed +=
        jdbc.update(
            "delete from notification.notification_recipient where person_id=?", lease.person());
    removed +=
        jdbc.update(
            "delete from notification.notification_preference where person_id=?", lease.person());
    removed +=
        jdbc.update("delete from notification.push_device where person_id=?", lease.person());
    Counts privatePatient = deletePatientPrivateLeaves(lease.person(), lease.id());
    removed += privatePatient.removed();
    retained += privatePatient.retained();
    jdbc.update(
        "update patient.patient set"
            + " occupation=null,marital_status=null,status='INACTIVE',updated_at=now(),version=version+1"
            + " where person_id=?",
        lease.person());
    jdbc.update(
        "update professional.professional set status='INACTIVE',updated_at=now(),version=version+1"
            + " where person_id=?",
        lease.person());
    // Registration proofs are private binary uploads stored in PostgreSQL, not MinIO.
    jdbc.update(
        "update professional.registration_request set"
            + " proof=null,proof_type=null,proof_name=null,identity_front=null,identity_front_type=null,identity_front_name=null,identity_back=null,identity_back_type=null,identity_back_name=null,identity_document_type=null,identity_document_number=null,identity_expires_on=null,speciality_name=null,reason=null,status='SUSPENDED',license_number='REDACTED',issuing_authority='REDACTED',updated_at=now()"
            + " where person_id=?",
        lease.person());
    jdbc.update(
        "update licensing.member set status='INACTIVE' where person_id=? and status<>'INACTIVE'",
        lease.person());
    // Preserve person UUID and subject mapping: clinical authors and own-request polling remain
    // valid.
    jdbc.update(
        """
        update identity.person set first_name='Deleted',last_name='User',middle_name=null,gender=null,birth_date=null,
        preferred_language_id=null,status='INACTIVE',updated_at=now(),version=version+1 where id=?
        """,
        lease.person());
    retained +=
        jdbc.queryForObject(
            "select count(*) from billing.invoice where patient_id in(select id from"
                + " patient.patient where person_id=?)",
            Integer.class,
            lease.person());
    retained +=
        jdbc.queryForObject(
            "select count(*) from care.care_episode where patient_id in(select id from"
                + " patient.patient where person_id=?)",
            Integer.class,
            lease.person());
    retained += retainedAuthorshipCount(lease.person());
    retained +=
        jdbc.queryForObject(
            "select count(*) from patient.patient where person_id=?",
            Integer.class,
            lease.person());
    retained +=
        jdbc.queryForObject(
            "select count(*) from document.document where owner_patient_id in(select id from"
                + " patient.patient where person_id=?) and uploaded_by is distinct from ?",
            Integer.class,
            lease.person(),
            lease.person());
    event(
        lease.id(),
        "identity.person",
        lease.person(),
        "ANONYMIZED",
        "Shared clinical authorship UUID preserved");
    String notes =
        "Private unreferenced uploads, contacts, patient identifiers and insurance removed;"
            + " registration proof cleared; identity anonymized and patient/professional profiles"
            + " inactive. Shared clinical records, invoices, professional credentials, author UUIDs"
            + " and audit remain under the shared-data retention policy. Keycloak account deletion"
            + " requires identity-provider administration; retained subject mapping permits request"
            + " tracking. Shared data remain subject to their own retention deadlines; unknown"
            + " external dependencies require review.";
    jdbc.update(
        """
        update lifecycle.deletion_request set status='COMPLETED',completed_at=now(),removed_count=?,anonymized_count=1,
        retained_count=?,notes=?,lease_token=null,lease_until=null,error_code=null where id=? and lease_token=?
        """,
        removed,
        retained,
        notes,
        lease.id(),
        lease.token());
  }

  Counts deletePatientPrivateLeaves(UUID person, UUID request) {
    int removed = 0, retained = 0;
    for (String table : List.of("patient.patient_identifier", "patient.patient_insurance")) {
      var ids =
          jdbc.queryForList(
              "select id from "
                  + table
                  + " where patient_id in(select id from patient.patient where person_id=?) for"
                  + " update",
              UUID.class,
              person);
      for (UUID id : ids) {
        if (hasReferences(table, id)) {
          retained++;
          event(
              request,
              table,
              id,
              "RETAINED",
              "Private patient leaf has an external shared dependency");
        } else {
          removed += jdbc.update("delete from " + table + " where id=?", id);
          event(
              request, table, id, "REMOVED", "Owned private patient identifier or insurance data");
        }
      }
    }
    return new Counts(removed, retained);
  }

  int retainedAuthorshipCount(UUID person) {
    int count = 0;
    for (String sql :
        List.of(
            "select count(*) from professional.professional_license where professional_id in(select"
                + " id from professional.professional where person_id=?)",
            "select count(*) from care.visit_report_revision where validated_by=?",
            "select count(*) from care.visit_report where updated_by=?",
            "select count(*) from care.reception_intake where recorded_by=?",
            "select count(*) from care.visit_intake where recorded_by=?",
            "select count(*) from care.ai_job where actor_id=?",
            "select count(*) from billing.cash_operation where cashier_person_id=?",
            "select count(*) from billing.cash_closure where cashier_person_id=?",
            "select count(*) from patient.allergy where recorded_by=?",
            "select count(*) from care.visit_prescription_revision where validated_by=?",
            "select count(*) from consultation.consultation_note where author_id in(select id from"
                + " professional.professional where person_id=?)",
            "select count(*) from consultation.consultation where professional_id in(select id from"
                + " professional.professional where person_id=?)")) {
      count += jdbc.queryForObject(sql, Integer.class, person);
    }
    return count;
  }

  record Counts(int removed, int retained) {}

  boolean stageDocument(UUID id, UUID request) {
    var keys =
        jdbc.queryForList(
            "select storage_key from document.document where id=? for update", String.class, id);
    if (keys.isEmpty()) return true;
    if (request != null) {
      Integer anotherPatient =
          jdbc.queryForObject(
              """
              select count(*) from document.document d join patient.patient p on p.id=d.owner_patient_id
              join lifecycle.deletion_request r on r.id=? where d.id=? and p.person_id<>r.person_id
              """,
              Integer.class,
              request,
              id);
      if (anotherPatient != null && anotherPatient > 0) {
        event(
            request,
            "document.document",
            id,
            "RETAINED",
            "Upload belongs to another patient's shared medical record");
        return false;
      }
    }
    if (hasReferences(id)) {
      event(
          request,
          "document.document",
          id,
          "RETAINED",
          "Document is referenced by shared clinical or communication data");
      return false;
    }
    jdbc.update("update document.document set status='ARCHIVED' where id=?", id);
    jdbc.update(
        "insert into lifecycle.object_purge(document_id,storage_key,request_id) values(?,?,?) on"
            + " conflict(document_id) do nothing",
        id,
        keys.getFirst(),
        request);
    return true;
  }

  void drainOutbox(UUID request) {
    drainOutbox(request, null);
  }

  void drainOutbox(UUID request, Lease lease) {
    String filter = request == null ? "request_id is null and episode_id is null" : "request_id=?";
    Object[] args = request == null ? new Object[] {} : new Object[] {request};
    var objects =
        jdbc.query(
            "select document_id,storage_key from lifecycle.object_purge where status='PENDING' and "
                + filter
                + " limit 100",
            (rs, n) -> new Object[] {rs.getObject(1, UUID.class), rs.getString(2)},
            args);
    for (var object : objects) {
      if (lease != null && !leaseValid(lease)) return;
      // The reservation and ARCHIVED metadata are already committed before contacting MinIO.
      objectPurger.delete((String) object[1]);
      tx.executeWithoutResult(
          status -> {
            if (lease != null && !lockLease(lease)) return;
            jdbc.queryForObject(
                "select document_id from lifecycle.object_purge where document_id=? for update",
                UUID.class,
                object[0]);
            jdbc.update("delete from lifecycle.object_purge where document_id=?", object[0]);
            jdbc.update("delete from document.document where id=?", object[0]);
            event(
                request,
                "document.document",
                (UUID) object[0],
                "REMOVED",
                "Durable outbox; blob deleted before metadata");
          });
    }
    if (request != null
        && jdbc.queryForObject(
                "select count(*) from lifecycle.object_purge where status='PENDING' and " + filter,
                Integer.class,
                args)
            > 0) throw new PartialProgress();
  }

  boolean hasReferences(UUID id) {
    return hasReferences("document.document", id);
  }

  boolean hasReferences(String table, UUID id) {
    // Catalog-derived identifiers are quoted, never supplied by a request. Every FK is checked,
    // including pharmacy/lab/communication dependencies, rather than just known care links.
    var refs =
        jdbc.query(
            """
            select ns.nspname,t.relname,a.attname from pg_constraint c
            join pg_class t on t.oid=c.conrelid join pg_namespace ns on ns.oid=t.relnamespace
            join pg_attribute a on a.attrelid=t.oid and a.attnum=any(c.conkey)
            where c.contype='f' and c.confrelid=?::regclass and ns.nspname<>'lifecycle'
            """,
            (rs, n) -> List.of(rs.getString(1), rs.getString(2), rs.getString(3)),
            table);
    for (var ref : refs) {
      String sql =
          "select count(*) from "
              + quote(ref.get(0))
              + "."
              + quote(ref.get(1))
              + " where "
              + quote(ref.get(2))
              + "=?";
      if (jdbc.queryForObject(sql, Integer.class, id) > 0) return true;
    }
    return false;
  }

  static String quote(String identifier) {
    return "\"" + identifier.replace("\"", "\"\"") + "\"";
  }

  void purgeExpiredDocuments() {
    tx.executeWithoutResult(
        status -> {
          var ids =
              jdbc.query(
                  "select id from document.document where retention_expires_at<=now() and not"
                      + " exists(select 1 from lifecycle.purge_event e where"
                      + " e.resource_id=document.document.id and e.outcome='RETAINED' and"
                      + " e.occurred_at>now()-interval '1 day') order by retention_expires_at for"
                      + " update skip locked limit 25",
                  (rs, n) -> rs.getObject(1, UUID.class));
          for (UUID id : ids) stageDocument(id, null);
        });
    drainOutbox(null);
  }

  void purgeExpiredPrivateRows() {
    // Deliberately fixed whitelist: no caller controls table/column names. Shared clinical,
    // cash and audit graphs are never fed into a generic cascading DELETE.
    Map<String, String> tables =
        Map.of(
            "identity.person_contact",
            "id",
            "identity.emergency_contact",
            "id",
            "identity.person_preferences",
            "person_id",
            "notification.notification_preference",
            "person_id",
            "notification.push_outbox",
            "id");
    for (var entry : tables.entrySet())
      tx.executeWithoutResult(
          status -> {
            String table = entry.getKey(), column = entry.getValue();
            var ids =
                jdbc.query(
                    "select "
                        + column
                        + " from "
                        + table
                        + " where retention_expires_at<=now() for update skip locked limit 25",
                    (rs, n) -> rs.getObject(1, UUID.class));
            for (UUID id : ids) {
              if (hasReferences(table, id)) continue;
              jdbc.update(
                  "delete from "
                      + table
                      + " where "
                      + column
                      + "=? and retention_expires_at<=now()",
                  id);
              event(
                  null, table, id, "REMOVED", "Expired private numerical row; dependency checked");
            }
          });
  }

  void event(UUID request, String type, UUID id, String outcome, String reason) {
    jdbc.update(
        "insert into lifecycle.purge_event(request_id,resource_type,resource_id,outcome,reason)"
            + " values(?,?,?,?,?)",
        request,
        type,
        id,
        outcome,
        reason);
  }

  boolean leaseValid(Lease lease) {
    return Integer.valueOf(1)
        .equals(
            jdbc.queryForObject(
                "select count(*) from lifecycle.deletion_request where id=? and lease_token=? and"
                    + " status='RUNNING' and lease_until>clock_timestamp()",
                Integer.class,
                lease.id(),
                lease.token()));
  }

  boolean lockLease(Lease lease) {
    var rows =
        jdbc.queryForList(
            "select id from lifecycle.deletion_request where id=? and lease_token=? and"
                + " status='RUNNING' and lease_until>clock_timestamp() for update",
            UUID.class,
            lease.id(),
            lease.token());
    return !rows.isEmpty();
  }

  static final class PartialProgress extends RuntimeException {}

  record Lease(UUID id, UUID person, UUID token) {}
}
