package org.novasos.healthysv2.lifecycle;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.novasos.healthysv2.TestcontainersConfiguration;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.transaction.annotation.Transactional;

@ActiveProfiles("test")
@Import(TestcontainersConfiguration.class)
@SpringBootTest
@Transactional
class RetentionIntegrationTests {
  @Autowired JdbcTemplate jdbc;
  @Autowired EpisodeRetentionPurger purger;
  @Autowired DeletionWorker deletionWorker;
  @Autowired org.springframework.transaction.support.TransactionTemplate tx;

  @org.springframework.test.context.bean.override.mockito.MockitoBean
  software.amazon.awssdk.services.s3.S3Client s3;

  @Test
  void associatesRetentionAtInsertAndPreservesExistingDeadlineAfterPolicyChange() {
    UUID first = person();
    var original =
        jdbc.queryForObject(
            "select retention_expires_at from identity.person where id=?",
            java.time.OffsetDateTime.class,
            first);
    jdbc.update("update lifecycle.retention_policy set years=5 where id");
    UUID second = person();
    var changed =
        jdbc.queryForObject(
            "select retention_expires_at from identity.person where id=?",
            java.time.OffsetDateTime.class,
            second);
    assertThat(changed).isBefore(original.minusYears(20));
    assertThat(
            jdbc.queryForObject(
                "select retention_expires_at from identity.person where id=?",
                java.time.OffsetDateTime.class,
                first))
        .isEqualTo(original);
  }

  @Test
  void reservedBlobRejectsNewReferencesBeforeMinioPurge() {
    UUID person = person(), doc = UUID.randomUUID();
    jdbc.update(
        "insert into"
            + " document.document(id,document_number,file_name,storage_key,storage_provider,uploaded_by)"
            + " values(?,?,?,?,'MINIO',?)",
        doc,
        "DOC-" + doc,
        "file.pdf",
        "private/" + doc,
        person);
    jdbc.update(
        "insert into lifecycle.object_purge(document_id,storage_key) values(?,?)",
        doc,
        "private/" + doc);
    assertThatThrownBy(
            () ->
                jdbc.update(
                    "insert into document.document_link(document_id,resource_type,resource_id)"
                        + " values(?,'CONSULTATION',?)",
                    doc,
                    UUID.randomUUID()))
        .isInstanceOf(org.springframework.dao.DataAccessException.class);
  }

  @Test
  void purgesExpiredCompleteOwnedEpisodeButKeepsPatientIdentity() {
    UUID episode = episode(),
        patient =
            jdbc.queryForObject(
                "select patient_id from care.care_episode where id=?", UUID.class, episode);
    jdbc.update(
        "insert into lifecycle.retention_job(episode_id,status) values(?,'PENDING')", episode);
    purger.purge(episode);
    assertThat(
            jdbc.queryForObject(
                "select count(*) from care.care_episode where id=?", Integer.class, episode))
        .isZero();
    assertThat(
            jdbc.queryForObject(
                "select count(*) from patient.patient where id=?", Integer.class, patient))
        .isEqualTo(1);
    assertThat(
            jdbc.queryForObject(
                "select status from lifecycle.retention_job where episode_id=?",
                String.class,
                episode))
        .isEqualTo("COMPLETED");
  }

  @Test
  void unexpiredIntakeBlocksEntireGraphWithoutPartialDeletes() {
    UUID episode = episode(), actor = person();
    jdbc.update(
        "insert into care.reception_intake(episode_id,recorded_by,recorded_at,retention_expires_at)"
            + " values(?,?,now(),now()+interval '30 years')",
        episode,
        actor);
    jdbc.update(
        "insert into lifecycle.retention_job(episode_id,status) values(?,'PENDING')", episode);
    purger.purge(episode);
    assertThat(
            jdbc.queryForObject(
                "select count(*) from care.care_episode where id=?", Integer.class, episode))
        .isEqualTo(1);
    assertThat(
            jdbc.queryForObject(
                "select status from lifecycle.retention_job where episode_id=?",
                String.class,
                episode))
        .isEqualTo("BLOCKED");
  }

  @Test
  void expiredCashGraphIncludingClosurePurgesUnderRetentionGuard() {
    UUID episode = episode(), invoice = cashGraph(episode, true);
    jdbc.update(
        "insert into lifecycle.retention_job(episode_id,status) values(?,'PENDING')", episode);
    purger.purge(episode);
    assertThat(
            jdbc.queryForObject(
                "select count(*) from billing.invoice where id=?", Integer.class, invoice))
        .isZero();
    assertThat(
            jdbc.queryForObject(
                "select count(*) from care.care_episode where id=?", Integer.class, episode))
        .isZero();
    assertThat(
            jdbc.queryForObject(
                "select status from lifecycle.retention_job where episode_id=?",
                String.class,
                episode))
        .isEqualTo("COMPLETED");
  }

  @Test
  void unexpiredCashRecordBlocksGraphAndCannotUseRetentionFlagToDelete() {
    UUID episode = episode(), invoice = cashGraph(episode, false);
    jdbc.update(
        "insert into lifecycle.retention_job(episode_id,status) values(?,'PENDING')", episode);
    purger.purge(episode);
    assertThat(
            jdbc.queryForObject(
                "select status from lifecycle.retention_job where episode_id=?",
                String.class,
                episode))
        .isEqualTo("BLOCKED");
    assertThat(
            jdbc.queryForObject(
                "select count(*) from billing.invoice where id=?", Integer.class, invoice))
        .isEqualTo(1);
    jdbc.queryForObject("select set_config('healthys.retention_purge','on',true)", String.class);
    assertThatThrownBy(
            () -> jdbc.update("delete from billing.cash_operation where invoice_id=?", invoice))
        .isInstanceOf(org.springframework.dao.DataAccessException.class);
  }

  @Test
  void frozenEpisodeRejectsNewDependencyDuringObjectDeletionWindow() {
    UUID episode = episode(), actor = person();
    jdbc.update(
        "insert into lifecycle.retention_reservation(resource_table,resource_id,episode_id)"
            + " values('care.care_episode',?,?)",
        episode,
        episode);
    assertThatThrownBy(
            () ->
                jdbc.update(
                    "insert into"
                        + " care.reception_intake(episode_id,recorded_by,recorded_at,retention_expires_at)"
                        + " values(?,?,now(),now()+interval '30 years')",
                    episode,
                    actor))
        .isInstanceOf(org.springframework.dao.DataAccessException.class);
  }

  @Test
  @Transactional(propagation = org.springframework.transaction.annotation.Propagation.NOT_SUPPORTED)
  void documentGraphCommitsReservationThenPurgesVersionsAndFinalizesWithoutFreezeDeadlock() {
    UUID episode = episode(),
        doctor = person(),
        professional = UUID.randomUUID(),
        visit = UUID.randomUUID(),
        revision = UUID.randomUUID(),
        document = UUID.randomUUID();
    UUID patient =
        jdbc.queryForObject(
            "select patient_id from care.care_episode where id=?", UUID.class, episode);
    UUID patientPerson =
        jdbc.queryForObject(
            "select person_id from patient.patient where id=?", UUID.class, patient);
    UUID org =
        jdbc.queryForObject(
            "select organization_id from care.care_episode where id=?", UUID.class, episode);
    String key = "private/" + document;
    jdbc.update(
        "insert into professional.professional(id,person_id,professional_number,professional_type)"
            + " values(?,?,?,'DOCTOR')",
        professional,
        doctor,
        "PRO-" + professional);
    jdbc.update(
        "insert into"
            + " care.care_visit(id,episode_id,professional_id,scheduled_at,follow_up,status,started_at,completed_at,created_at,retention_expires_at)"
            + " values(?,?,?,now()-interval '31 years'+interval '1"
            + " day',false,'COMPLETED',now()-interval '30 years',now()-interval '30 years'+interval"
            + " '1 hour',now()-interval '31 years',now()-interval '1 year')",
        visit,
        episode,
        professional);
    jdbc.update(
        "insert into"
            + " care.visit_report_revision(id,visit_id,revision,reason,observations,diagnosis,treatment,follow_up,validated_at,validated_by,retention_expires_at)"
            + " values(?,?,1,'historical','','diagnosis','','',now()-interval '30"
            + " years',?,now()-interval '1 year')",
        revision,
        visit,
        doctor);
    jdbc.update(
        "insert into"
            + " document.document(id,document_number,file_name,storage_key,storage_provider,uploaded_by,owner_patient_id,retention_expires_at)"
            + " values(?,?,'report.pdf',?,'MINIO',?,?,now()-interval '1 year')",
        document,
        "DOC-" + document,
        key,
        doctor,
        patient);
    jdbc.update(
        "insert into"
            + " care.visit_document(document_id,visit_id,organization_id,document_type,report_revision_id,report_revision,retention_expires_at)"
            + " values(?,?,?,'REPORT',?,1,now()-interval '1 year')",
        document,
        visit,
        org,
        revision);
    jdbc.update(
        "insert into"
            + " document.document_link(document_id,resource_type,resource_id,retention_expires_at)"
            + " values(?,'CARE_VISIT',?,now()-interval '1 year')",
        document,
        visit);
    jdbc.update(
        "insert into lifecycle.retention_job(episode_id,status) values(?,'PENDING')", episode);
    tx.executeWithoutResult(status -> purger.purge(episode));
    assertThat(
            jdbc.queryForObject(
                "select status from document.document where id=?", String.class, document))
        .isEqualTo("ARCHIVED");
    assertThat(
            jdbc.queryForObject(
                "select count(*) from lifecycle.retention_reservation where episode_id=?",
                Integer.class,
                episode))
        .isGreaterThan(1);
    when(s3.getBucketVersioning(
            any(software.amazon.awssdk.services.s3.model.GetBucketVersioningRequest.class)))
        .thenReturn(
            software.amazon.awssdk.services.s3.model.GetBucketVersioningResponse.builder()
                .status(software.amazon.awssdk.services.s3.model.BucketVersioningStatus.ENABLED)
                .build());
    when(s3.listObjectVersions(
            any(software.amazon.awssdk.services.s3.model.ListObjectVersionsRequest.class)))
        .thenReturn(
            software.amazon.awssdk.services.s3.model.ListObjectVersionsResponse.builder()
                .isTruncated(false)
                .versions(
                    software.amazon.awssdk.services.s3.model.ObjectVersion.builder()
                        .key(key)
                        .versionId("v1")
                        .build())
                .deleteMarkers(
                    software.amazon.awssdk.services.s3.model.DeleteMarkerEntry.builder()
                        .key(key)
                        .versionId("marker")
                        .build())
                .build(),
            software.amazon.awssdk.services.s3.model.ListObjectVersionsResponse.builder()
                .isTruncated(false)
                .build());
    purger.confirmBlobs(episode);
    assertThat(
            jdbc.queryForObject(
                "select status from lifecycle.object_purge where document_id=?",
                String.class,
                document))
        .isEqualTo("DONE");
    tx.executeWithoutResult(status -> purger.purge(episode));
    assertThat(
            jdbc.queryForObject(
                "select status from lifecycle.retention_job where episode_id=?",
                String.class,
                episode))
        .isEqualTo("COMPLETED");
    assertThat(
            jdbc.queryForObject(
                "select count(*) from lifecycle.retention_reservation where episode_id=?",
                Integer.class,
                episode))
        .isZero();
    assertThat(
            jdbc.queryForObject(
                "select count(*) from document.document where id=?", Integer.class, document))
        .isZero();
    verify(s3, times(2))
        .deleteObject(any(software.amazon.awssdk.services.s3.model.DeleteObjectRequest.class));
    // Fixtures committed for the genuine two-transaction test are cleaned explicitly.
    jdbc.update("delete from professional.professional where id=?", professional);
    jdbc.update("delete from patient.patient where id=?", patient);
    jdbc.update("delete from organization.organization where id=?", org);
    jdbc.update("delete from identity.person where id in (?,?)", doctor, patientPerson);
  }

  @Test
  void
      selfDeletionRemovesPrivatePatientIdentityLeavesAndDisablesProfessionalWithoutErasingAuthorship() {
    UUID owner = person(),
        patient = UUID.randomUUID(),
        professional = UUID.randomUUID(),
        insurer = UUID.randomUUID(),
        request = UUID.randomUUID(),
        token = UUID.randomUUID();
    jdbc.update(
        "insert into"
            + " patient.patient(id,person_id,patient_number,blood_group,rhesus,occupation,marital_status)"
            + " values(?,?,?,'O','POSITIVE','Private job','MARRIED')",
        patient,
        owner,
        "PAT-" + patient);
    jdbc.update(
        "insert into patient.patient_identifier(patient_id,type,value,issuer)"
            + " values(?,'PASSPORT',?,'PRIVATE')",
        patient,
        "passport-" + patient);
    jdbc.update(
        "insert into catalog.insurance_company(id,name) values(?,'Private insurer')", insurer);
    jdbc.update(
        "insert into"
            + " patient.patient_insurance(patient_id,insurance_company_id,policy_number,member_number)"
            + " values(?,?,'PRIVATE-POLICY','PRIVATE-MEMBER')",
        patient,
        insurer);
    jdbc.update(
        "insert into professional.professional(id,person_id,professional_number,professional_type)"
            + " values(?,?,?,'DOCTOR')",
        professional,
        owner,
        "PRO-" + professional);
    jdbc.update(
        "insert into"
            + " professional.professional_license(professional_id,license_number,issuing_authority)"
            + " values(?,?,'Authority')",
        professional,
        "LICENSE-" + professional);
    jdbc.update(
        "insert into"
            + " lifecycle.deletion_request(id,person_id,idempotency_key,status,lease_token,lease_until)"
            + " values(?,?,?,'RUNNING',?,now()+interval '10 minutes')",
        request,
        owner,
        request.toString(),
        token);
    deletionWorker.processLocked(new DeletionWorker.Lease(request, owner, token));
    assertThat(
            jdbc.queryForObject(
                "select count(*) from patient.patient_identifier where patient_id=?",
                Integer.class,
                patient))
        .isZero();
    assertThat(
            jdbc.queryForObject(
                "select count(*) from patient.patient_insurance where patient_id=?",
                Integer.class,
                patient))
        .isZero();
    assertThat(
            jdbc.queryForObject(
                "select status from patient.patient where id=?", String.class, patient))
        .isEqualTo("INACTIVE");
    assertThat(
            jdbc.queryForObject(
                "select occupation from patient.patient where id=?", String.class, patient))
        .isNull();
    assertThat(
            jdbc.queryForObject(
                "select marital_status from patient.patient where id=?", String.class, patient))
        .isNull();
    assertThat(
            jdbc.queryForObject(
                "select blood_group from patient.patient where id=?", String.class, patient))
        .isEqualTo("O");
    assertThat(
            jdbc.queryForObject(
                "select status from professional.professional where id=?",
                String.class,
                professional))
        .isEqualTo("INACTIVE");
    assertThat(
            jdbc.queryForObject(
                "select count(*) from professional.professional_license where professional_id=?",
                Integer.class,
                professional))
        .isEqualTo(1);
    assertThat(
            jdbc.queryForObject(
                "select retained_count from lifecycle.deletion_request where id=?",
                Integer.class,
                request))
        .isGreaterThanOrEqualTo(1);
    assertThat(
            jdbc.queryForObject(
                "select status from lifecycle.deletion_request where id=?", String.class, request))
        .isEqualTo("COMPLETED");
  }

  private UUID cashGraph(UUID episode, boolean expired) {
    UUID invoice = UUID.randomUUID(),
        operation = UUID.randomUUID(),
        closure = UUID.randomUUID(),
        actor = person();
    UUID patient =
        jdbc.queryForObject(
            "select patient_id from care.care_episode where id=?", UUID.class, episode);
    UUID org =
        jdbc.queryForObject(
            "select organization_id from care.care_episode where id=?", UUID.class, episode);
    jdbc.update(
        "insert into"
            + " billing.invoice(id,invoice_number,patient_id,organization_id,currency,retention_expires_at)"
            + " values(?,?,?,?,'CAD',now()-interval '1 year')",
        invoice,
        "INV-" + invoice,
        patient,
        org);
    jdbc.update(
        "insert into"
            + " billing.care_invoice(episode_id,invoice_id,organization_id,idempotency_key,fingerprint,retention_expires_at)"
            + " values(?,?,?,?,?,now()-interval '1 year')",
        episode,
        invoice,
        org,
        invoice.toString(),
        "fingerprint");
    String deadline = expired ? "now()-interval '1 year'" : "now()+interval '1 year'";
    jdbc.update(
        "insert into"
            + " billing.cash_operation(id,receipt_number,invoice_id,operation_type,amount,currency,cashier_person_id,idempotency_key,fingerprint,retention_expires_at)"
            + " values(?,?,?,'PAYMENT',10,'CAD',?,?,?,"
            + deadline
            + ")",
        operation,
        "REC-" + operation,
        invoice,
        actor,
        operation.toString(),
        "fingerprint");
    jdbc.update(
        "insert into"
            + " billing.cash_closure(id,closure_number,organization_id,cashier_person_id,period_start,period_end,created_by_person_id,amounts,idempotency_key,fingerprint,retention_expires_at)"
            + " values(?,?,?,?,now()-interval '33 years',now()-interval '32"
            + " years',?,'{}'::jsonb,?,?,now()-interval '1 year')",
        closure,
        "CLO-" + closure,
        org,
        actor,
        actor,
        closure.toString(),
        "fingerprint");
    jdbc.update(
        "insert into"
            + " billing.cash_closure_receipt(closure_id,cash_operation_id,retention_expires_at)"
            + " values(?,?,now()-interval '1 year')",
        closure,
        operation);
    return invoice;
  }

  private UUID episode() {
    UUID person = person(),
        patient = UUID.randomUUID(),
        org = UUID.randomUUID(),
        episode = UUID.randomUUID();
    jdbc.update(
        "insert into patient.patient(id,person_id,patient_number) values(?,?,?)",
        patient,
        person,
        "PAT-" + patient);
    jdbc.update(
        "insert into organization.organization(id,organization_number,name) values(?,?,'Pilot')",
        org,
        "ORG-" + org);
    jdbc.update(
        "insert into"
            + " care.care_episode(id,patient_id,organization_id,reason,status,follow_up_seconds,created_at,retention_expires_at)"
            + " values(?,?,?,'historic','CLOSED',3600,now()-interval '31 years',now()-interval '1"
            + " year')",
        episode,
        patient,
        org);
    return episode;
  }

  private UUID person() {
    UUID id = UUID.randomUUID();
    jdbc.update(
        "insert into identity.person(id,person_number,first_name,last_name)"
            + " values(?,?,'Test','Person')",
        id,
        "PER-" + id);
    return id;
  }
}
