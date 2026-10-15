package org.novasos.healthysv2.care;

import static org.assertj.core.api.Assertions.*;

import org.junit.jupiter.api.Test;
import org.novasos.healthysv2.TestcontainersConfiguration;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.transaction.annotation.Transactional;

@SpringBootTest
@ActiveProfiles("test")
@Import(TestcontainersConfiguration.class)
@Transactional
class CareMigrationIntegrationTests {
  @Autowired JdbcTemplate jdbc;
  @Autowired VisitSlotService slots;
  @Autowired PhysicianQueueService queues;

  @Test
  void pendingReassignmentReleasesOldReservationWhilePreparedPaidVisitKeepsIt() {
    var person = java.util.UUID.randomUUID();
    var patient = java.util.UUID.randomUUID();
    var org = java.util.UUID.randomUUID();
    var episode = java.util.UUID.randomUUID();
    var professional = java.util.UUID.randomUUID();
    var visit = java.util.UUID.randomUUID();
    jdbc.update(
        "insert into identity.person(id,person_number,first_name,last_name) values(?,?,?,?)",
        person,
        "P-" + person,
        "Queue",
        "Clinician");
    jdbc.update(
        "insert into patient.patient(id,person_id,patient_number) values(?,?,?)",
        patient,
        person,
        "PAT-" + patient);
    jdbc.update(
        "insert into organization.organization(id,organization_number,name) values(?,?,?)",
        org,
        "ORG-" + org,
        "Clinic");
    jdbc.update(
        "insert into professional.professional(id,person_id,professional_number,professional_type)"
            + " values(?,?,?,'DOCTOR')",
        professional,
        person,
        "PRO-" + professional);
    jdbc.update(
        "insert into"
            + " care.care_episode(id,patient_id,organization_id,reason,status,follow_up_seconds,created_at,retention_expires_at)"
            + " values(?,?,?,'Consultation','ACTIVE',172800,now(),now()+interval '30 years')",
        episode,
        patient,
        org);
    var start = java.time.Instant.now().plusSeconds(3600);
    var end = start.plusSeconds(1800);
    var excluded = java.util.UUID.randomUUID();
    jdbc.update(
        "insert into"
            + " care.care_visit(id,episode_id,professional_id,scheduled_at,follow_up,status,created_at,retention_expires_at,slot_minutes)"
            + " values(?,?,?,?,false,'AWAITING_SCHEDULING',now(),now()+interval '30 years',30)",
        visit,
        episode,
        professional,
        java.sql.Timestamp.from(start));
    assertThatThrownBy(() -> slots.requireNoOverlap(professional, start, end, excluded))
        .isInstanceOf(org.novasos.healthysv2.shared.api.error.ConflictException.class);
    jdbc.update(
        "insert into"
            + " care.reassignment_request(visit_id,organization_id,source_professional_id,source_date,reason,created_by)"
            + " values(?,?,?,current_date,'Deferred',?)",
        visit,
        org,
        professional,
        person);
    assertThatCode(() -> slots.requireNoOverlap(professional, start, end, excluded))
        .doesNotThrowAnyException();
    jdbc.update(
        "update care.reassignment_request set status='RESOLVED',resolved_at=now(),resolved_by=?"
            + " where visit_id=?",
        person,
        visit);
    assertThatThrownBy(() -> slots.requireNoOverlap(professional, start, end, excluded))
        .isInstanceOf(org.novasos.healthysv2.shared.api.error.ConflictException.class);
    var unpaidEpisode = java.util.UUID.randomUUID();
    var unpaidVisit = java.util.UUID.randomUUID();
    jdbc.update(
        "insert into"
            + " care.care_episode(id,patient_id,organization_id,reason,status,follow_up_seconds,created_at,retention_expires_at)"
            + " values(?,?,?,'Consultation','DRAFT',172800,now(),now()+interval '30 years')",
        unpaidEpisode,
        patient,
        org);
    jdbc.update(
        "insert into"
            + " care.care_visit(id,episode_id,professional_id,scheduled_at,follow_up,status,created_at,retention_expires_at,slot_minutes)"
            + " values(?,?,?,?,false,'PENDING_PAYMENT',now(),now()+interval '30 years',30)",
        unpaidVisit,
        unpaidEpisode,
        professional,
        java.sql.Timestamp.from(start));
    var deferred =
        queues.deferReservations(
            org,
            professional,
            start.atZone(java.time.ZoneOffset.UTC).toLocalDate(),
            start,
            end,
            person,
            "Calendar closure");
    assertThat(deferred).containsExactlyInAnyOrder(visit, unpaidVisit);
    assertThat(
            jdbc.queryForObject(
                "select status from care.care_visit where id=?", String.class, unpaidVisit))
        .isEqualTo("PENDING_PAYMENT");
    assertThat(
            jdbc.queryForObject(
                "select status from care.care_visit where id=?", String.class, visit))
        .isEqualTo("AWAITING_SCHEDULING");
    assertThat(
            jdbc.queryForObject(
                "select count(*) from care.reassignment_request where visit_id in (?,?) and"
                    + " status='PENDING'",
                Integer.class,
                visit,
                unpaidVisit))
        .isEqualTo(2);
    assertThat(
            queues.deferReservations(
                org,
                professional,
                start.atZone(java.time.ZoneOffset.UTC).toLocalDate(),
                start,
                end,
                person,
                "Calendar closure retry"))
        .isEmpty();
    assertThatCode(() -> slots.requireNoOverlap(professional, start, end, excluded))
        .doesNotThrowAnyException();
  }

  @Test
  void cashActivatedEpisodeCanWaitBeforeFirstEncounterAndDraftCannotHaveAClock() {
    java.util.UUID person = java.util.UUID.randomUUID(),
        patient = java.util.UUID.randomUUID(),
        org = java.util.UUID.randomUUID(),
        episode = java.util.UUID.randomUUID();
    jdbc.update(
        "insert into identity.person(id,person_number,first_name,last_name) values(?,?,?,?)",
        person,
        "P-" + person,
        "Cash",
        "Patient");
    jdbc.update(
        "insert into patient.patient(id,person_id,patient_number) values(?,?,?)",
        patient,
        person,
        "PAT-" + patient);
    jdbc.update(
        "insert into organization.organization(id,organization_number,name) values(?,?,?)",
        org,
        "ORG-" + org,
        "Clinic");
    jdbc.update(
        "insert into"
            + " care.care_episode(id,patient_id,organization_id,reason,status,follow_up_seconds,created_at,retention_expires_at)"
            + " values(?,?,?,'Consultation','DRAFT',172800,now(),now()+interval '30 years')",
        episode,
        patient,
        org);
    jdbc.update("update care.care_episode set status='ACTIVE' where id=?", episode);
    assertThat(
            jdbc.queryForObject(
                "select started_at is null and expires_at is null from care.care_episode where"
                    + " id=?",
                Boolean.class,
                episode))
        .isTrue();
    jdbc.update(
        "update care.care_episode set started_at=now(),expires_at=now()+follow_up_seconds*interval"
            + " '1 second' where id=?",
        episode);
    assertThat(
            jdbc.queryForObject(
                "select expires_at-started_at=interval '48 hours' from care.care_episode where"
                    + " id=?",
                Boolean.class,
                episode))
        .isTrue();
    assertThatThrownBy(
            () -> jdbc.update("update care.care_episode set status='DRAFT' where id=?", episode))
        .isInstanceOf(org.springframework.dao.DataIntegrityViolationException.class);
  }

  @Test
  void addsCareTablesWithoutRemovingLegacyTablesAndEnforcesInitialUniqueness() {
    assertThat(
            jdbc.queryForObject(
                "SELECT to_regclass('care.care_episode') IS NOT NULL AND"
                    + " to_regclass('care.care_visit') IS NOT NULL AND"
                    + " to_regclass('consultation.consultation') IS NOT NULL AND"
                    + " to_regclass('appointment.appointment') IS NOT NULL",
                Boolean.class))
        .isTrue();
    assertThat(
            jdbc.queryForObject(
                "SELECT indexdef FROM pg_indexes WHERE schemaname='care' AND"
                    + " indexname='uq_care_visit_initial'",
                String.class))
        .contains("UNIQUE")
        .contains("follow_up")
        .contains("CANCELLED");
    assertThat(
            jdbc.queryForObject(
                "SELECT to_regclass('care.reception_intake') IS NOT NULL", Boolean.class))
        .isTrue();
    assertThat(
            jdbc.queryForObject(
                "SELECT pg_get_constraintdef(oid) FROM pg_constraint WHERE"
                    + " conname='chk_visit_status' AND conrelid='care.care_visit'::regclass",
                String.class))
        .contains("PENDING_PAYMENT");
    assertThat(
            jdbc.queryForObject(
                "SELECT count(*) FROM information_schema.table_constraints WHERE"
                    + " table_schema='care' AND constraint_type='FOREIGN KEY'",
                Integer.class))
        .isGreaterThanOrEqualTo(16);
  }
}
