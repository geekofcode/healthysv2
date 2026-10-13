package org.novasos.healthysv2.care;

import static org.junit.jupiter.api.Assertions.*;

import java.util.*;
import org.junit.jupiter.api.Test;
import org.novasos.healthysv2.TestcontainersConfiguration;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.dao.DataAccessException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;

@SpringBootTest
@ActiveProfiles("test")
@Import(TestcontainersConfiguration.class)
class AiMigrationIntegrationTests {
  @Autowired JdbcTemplate db;
  @Autowired PlatformTransactionManager tm;

  private record Fixture(UUID visit, UUID person, UUID org) {}

  private Fixture fixture() {
    UUID person = UUID.randomUUID(),
        patient = UUID.randomUUID(),
        org = UUID.randomUUID(),
        professional = UUID.randomUUID(),
        episode = UUID.randomUUID(),
        visit = UUID.randomUUID();
    db.update(
        "insert into identity.person(id,person_number,first_name,last_name)"
            + " values(?,?,'AI','Test')",
        person,
        "AI-" + person);
    db.update(
        "insert into patient.patient(id,person_id,patient_number) values(?,?,?)",
        patient,
        person,
        "AI-" + patient);
    db.update(
        "insert into organization.organization(id,organization_number,name) values(?,?,'AI test')",
        org,
        "AI-" + org);
    db.update(
        "insert into professional.professional(id,person_id,professional_number,professional_type)"
            + " values(?,?,?,'DOCTOR')",
        professional,
        person,
        "AI-" + professional);
    db.update(
        "insert into"
            + " care.care_episode(id,patient_id,organization_id,reason,status,follow_up_seconds,created_at,retention_expires_at)"
            + " values(?,?,?,'Test','READY',604800,now(),now()+interval '30 years')",
        episode,
        patient,
        org);
    db.update(
        "insert into"
            + " care.care_visit(id,episode_id,professional_id,scheduled_at,follow_up,status,created_at,retention_expires_at)"
            + " values(?,?,?,now()+interval '1 hour',false,'SCHEDULED',now(),now()+interval '30"
            + " years')",
        visit,
        episode,
        professional);
    return new Fixture(visit, person, org);
  }

  private UUID insert(Fixture f, String key) {
    UUID id = UUID.randomUUID();
    db.update(
        "insert into"
            + " care.ai_job(id,visit_id,actor_id,organization_id,kind,idempotency_key,request_hash,snapshot,snapshot_hash,deadline_at,retention_expires_at)"
            + " values(?,?,?,?,'REPORT',?,'hash','{\"sources\":[]}'::jsonb,'hash',now()+interval"
            + " '15 minutes',now()+interval '30 years')",
        id,
        f.visit,
        f.person,
        f.org,
        key);
    return id;
  }

  @Test
  void replayKeyCannotCreateSecondJobAndCompletedProvenanceIsImmutable() {
    var f = fixture();
    UUID id = insert(f, "key");
    assertThrows(DataAccessException.class, () -> insert(f, "key"));
    assertThrows(
        DataAccessException.class,
        () -> db.update("update care.ai_job set snapshot='{}'::jsonb where id=?", id));
    db.update(
        "update care.ai_job set"
            + " status='SUCCEEDED',output='{\"reason\":\"Test\"}'::jsonb,model='test-model' where"
            + " id=?",
        id);
    assertThrows(
        DataAccessException.class,
        () -> db.update("update care.ai_job set output='{}'::jsonb where id=?", id));
    assertThrows(
        DataAccessException.class,
        () -> db.update("update care.ai_job set status='QUEUED' where id=?", id));
  }

  @Test
  void runningLeaseSurvivesRestartAndCanBeClaimedOnceExpired() {
    var f = fixture();
    UUID id = insert(f, "lease");
    UUID token = UUID.randomUUID();
    db.update(
        "update care.ai_job set"
            + " status='RUNNING',attempts=1,lease_token=?,lease_until=now()-interval '1 minute'"
            + " where id=?",
        token,
        id);
    var tx = new TransactionTemplate(tm);
    tx.executeWithoutResult(
        s -> {
          var ids =
              db.queryForList(
                  "select id from care.ai_job where id=? and status='RUNNING' and"
                      + " lease_until<=clock_timestamp() for update skip locked",
                  UUID.class,
                  id);
          assertEquals(List.of(id), ids);
          db.update(
              "update care.ai_job set attempts=attempts+1,lease_token=?,lease_until=now()+interval"
                  + " '2 minutes' where id=?",
              UUID.randomUUID(),
              id);
        });
    assertEquals(
        2, db.queryForObject("select attempts from care.ai_job where id=?", Integer.class, id));
    assertNotEquals(
        token, db.queryForObject("select lease_token from care.ai_job where id=?", UUID.class, id));
  }

  @Test
  void temporaryAudioCanBePurgedWithoutErasingSuccessfulJobProvenance() {
    var f = fixture();
    UUID id = insert(f, "audio");
    assertThrows(
        DataAccessException.class,
        () ->
            db.update(
                "update care.ai_job set audio_key='audio',audio_expires_at=now() where id=?", id));
    UUID transcription = UUID.randomUUID();
    db.update(
        "insert into"
            + " care.ai_job(id,visit_id,actor_id,organization_id,kind,idempotency_key,request_hash,snapshot,snapshot_hash,deadline_at,retention_expires_at,audio_key,audio_expires_at,status,output)"
            + " values(?,?,?,?,'TRANSCRIPTION','audio','hash','{}'::jsonb,'hash',now()+interval '15"
            + " minutes',now()+interval '30 years','private/key',now()+interval '1"
            + " hour','SUCCEEDED','{\"text\":\"Dictée\"}'::jsonb)",
        transcription,
        f.visit,
        f.person,
        f.org);
    db.update("update care.ai_job set audio_key=null where id=?", transcription);
    assertEquals(
        "Dictée",
        db.queryForObject(
            "select output->>'text' from care.ai_job where id=?", String.class, transcription));
  }
}
