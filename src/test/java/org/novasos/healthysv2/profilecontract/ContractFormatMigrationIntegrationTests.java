package org.novasos.healthysv2.profilecontract;

import static org.assertj.core.api.Assertions.*;

import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.novasos.healthysv2.TestcontainersConfiguration;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.ActiveProfiles;

@ActiveProfiles("test")
@Import(TestcontainersConfiguration.class)
@SpringBootTest(
    properties = {
      "PROFILE_CONTRACT_WORKER_ENABLED=false",
      "STAFF_ACCOUNT_PROVISIONING_ENABLED=false",
      "EMAIL_ENABLED=false"
    })
class ContractFormatMigrationIntegrationTests {
  @Autowired JdbcTemplate db;

  private UUID person() {
    UUID id = UUID.randomUUID();
    db.update(
        "insert into identity.person(id,person_number,first_name,last_name,status)"
            + " values(?,?,?,?,'ACTIVE')",
        id,
        "HTML-" + id,
        "Contract",
        "Author");
    return id;
  }

  private UUID template(UUID person, String format) {
    UUID id = UUID.randomUUID();
    int version = 1 + java.util.concurrent.ThreadLocalRandom.current().nextInt(1_000_000);
    db.update(
        "insert into"
            + " profilecontract.template(id,category,version,terms_url,terms_version,body_text,created_by,body_format,published_at)"
            + " values(?,'PATIENT',?,'https://example.test/terms','v1',?, ?,?,now())",
        id,
        version,
        "<p>Contract {{signature}}</p>",
        person,
        format);
    return id;
  }

  @Test
  void legacyTemplatesDefaultToTextAndPublishedFormatIsImmutable() {
    UUID person = person(), id = UUID.randomUUID();
    int version = 1 + java.util.concurrent.ThreadLocalRandom.current().nextInt(1_000_000);
    db.update(
        "insert into"
            + " profilecontract.template(id,category,version,terms_url,terms_version,body_text,created_by,published_at)"
            + " values(?,'ADMIN',?,'https://example.test/terms','v1','Legacy"
            + " {{signature}}',?,now())",
        id,
        version,
        person);
    assertThat(
            db.queryForObject(
                "select body_format from profilecontract.template where id=?", String.class, id))
        .isEqualTo("TEXT");
    assertThatThrownBy(
            () ->
                db.update("update profilecontract.template set body_format='HTML' where id=?", id))
        .isInstanceOf(DataIntegrityViolationException.class);
  }

  @Test
  void signedHtmlSnapshotIsImmutableWhilePdfDeliveryCanProgress() {
    UUID person = person(), template = template(person, "HTML"), agreement = UUID.randomUUID();
    db.update(
        """
        insert into profilecontract.agreement(id,actor_person_id,profile_id,template_id,template_version,category,profile_type,source_type,source_id,profile_label,organization_name,patient_name,signer_name,signer_email,terms_url,terms_version,body_snapshot,body_format,accepted_terms,guardian_acknowledgement,signature_png,signature_sha256,snapshot_sha256)
        values(?,?,?,?,?,'PATIENT','PATIENT','PATIENT',?,'Patient','','Patient','Signer','signer@example.test','https://example.test/terms','v1','<p>Signed contract</p>','HTML',true,false,?,?,?)
        """,
        agreement,
        person,
        UUID.randomUUID(),
        template,
        db.queryForObject(
            "select version from profilecontract.template where id=?", Integer.class, template),
        UUID.randomUUID(),
        new byte[] {1, 2, 3},
        "a".repeat(64),
        "b".repeat(64));
    assertThat(
            db.queryForObject(
                "select body_format from profilecontract.agreement where id=?",
                String.class,
                agreement))
        .isEqualTo("HTML");
    assertThatThrownBy(
            () ->
                db.update(
                    "update profilecontract.agreement set body_format='TEXT' where id=?",
                    agreement))
        .isInstanceOf(DataIntegrityViolationException.class);
    assertThatThrownBy(
            () ->
                db.update(
                    "update profilecontract.agreement set body_snapshot='<p>Changed</p>' where"
                        + " id=?",
                    agreement))
        .isInstanceOf(DataIntegrityViolationException.class);
    assertThatThrownBy(
            () ->
                db.update(
                    "update profilecontract.agreement set header_snapshot='<p>Changed header</p>'"
                        + " where id=?",
                    agreement))
        .isInstanceOf(DataIntegrityViolationException.class);
    assertThatThrownBy(
            () ->
                db.update(
                    "update profilecontract.agreement set footer_snapshot='<p>Changed footer</p>'"
                        + " where id=?",
                    agreement))
        .isInstanceOf(DataIntegrityViolationException.class);
    assertThat(
            db.update(
                "update profilecontract.agreement set"
                    + " status='PDF_READY',pdf_key='private-contract-test' where id=?",
                agreement))
        .isEqualTo(1);
  }
}
