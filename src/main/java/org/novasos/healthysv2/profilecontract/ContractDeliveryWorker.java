package org.novasos.healthysv2.profilecontract;

import java.util.*;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;

@Component
class ContractDeliveryWorker {
  private final JdbcTemplate db;
  private final TransactionTemplate tx;
  private final ContractPdf pdf;
  private final ContractObjectStorage storage;
  private final ContractMailer mail;
  private final boolean enabled;

  ContractDeliveryWorker(
      JdbcTemplate db,
      PlatformTransactionManager manager,
      ContractPdf pdf,
      ContractObjectStorage storage,
      ContractMailer mail,
      @Value("${PROFILE_CONTRACT_WORKER_ENABLED:true}") boolean enabled) {
    this.db = db;
    this.tx = new TransactionTemplate(manager);
    this.pdf = pdf;
    this.storage = storage;
    this.mail = mail;
    this.enabled = enabled;
  }

  @Scheduled(fixedDelayString = "${PROFILE_CONTRACT_POLL_MILLIS:30000}")
  void poll() {
    if (!enabled) return;
    for (int i = 0; i < 10; i++) {
      Map<String, Object> job = tx.execute(s -> claim());
      if (job == null) return;
      process(job);
    }
  }

  private Map<String, Object> claim() {
    var rows =
        db.queryForList(
            "select agreement_id,phase from profilecontract.delivery_job where attempts<5 and"
                + " next_attempt_at<=clock_timestamp() and (lease_until is null or"
                + " lease_until<clock_timestamp()) and (phase='PDF' or (? and phase='EMAIL')) order"
                + " by next_attempt_at for update skip locked limit 1",
            mail.enabled());
    if (rows.isEmpty()) return null;
    UUID lease = UUID.randomUUID();
    var r = new HashMap<>(rows.getFirst());
    r.put("lease_token", lease);
    db.update(
        "update profilecontract.delivery_job set"
            + " lease_token=?,lease_until=clock_timestamp()+interval '5"
            + " minutes',attempts=attempts+1 where agreement_id=?",
        lease,
        r.get("agreement_id"));
    return r;
  }

  private void lease(Map<String, Object> job) {
    Integer n =
        db.queryForObject(
            "select count(*) from profilecontract.delivery_job where agreement_id=? and"
                + " lease_token=? and lease_until>clock_timestamp()",
            Integer.class,
            job.get("agreement_id"),
            job.get("lease_token"));
    if (n == null || n != 1) throw new IllegalStateException("CONTRACT_JOB_LEASE_EXPIRED");
  }

  private void process(Map<String, Object> job) {
    try {
      lease(job);
      var rows =
          db.queryForList(
              "select * from profilecontract.agreement where id=?", job.get("agreement_id"));
      if (rows.isEmpty()) throw new IllegalStateException("CONTRACT_AGREEMENT_MISSING");
      var agreement = rows.getFirst();
      if ("PDF".equals(job.get("phase"))) {
        byte[] content = pdf.render(agreement);
        String prefix =
            "profile-contracts/"
                + agreement.get("actor_person_id")
                + "/"
                + agreement.get("profile_id")
                + "/"
                + agreement.get("id");
        String signatureKey = prefix + "/signature.png", pdfKey = prefix + "/contract.pdf";
        lease(job);
        storage.put(signatureKey, (byte[]) agreement.get("signature_png"), "image/png");
        lease(job);
        storage.put(pdfKey, content, "application/pdf");
        tx.executeWithoutResult(
            s -> {
              lease(job);
              db.update(
                  "update profilecontract.agreement set"
                      + " pdf_key=?,signature_key=?,pdf_sha256=?,status='PDF_READY' where id=? and"
                      + " pdf_key is null",
                  pdfKey,
                  signatureKey,
                  ProfileContractService.hash(content),
                  agreement.get("id"));
              db.update(
                  "update profilecontract.delivery_job set"
                      + " phase='EMAIL',attempts=0,error_code=null,next_attempt_at=clock_timestamp(),lease_token=null,lease_until=null"
                      + " where agreement_id=? and lease_token=?",
                  agreement.get("id"),
                  job.get("lease_token"));
            });
      } else {
        lease(job);
        byte[] content = storage.get((String) agreement.get("pdf_key"));
        if (!ProfileContractService.hash(content).equals(agreement.get("pdf_sha256")))
          throw new IllegalStateException("CONTRACT_PDF_CHECKSUM_MISMATCH");
        lease(job);
        mail.send((UUID) agreement.get("id"), (String) agreement.get("signer_email"), content);
        tx.executeWithoutResult(
            s -> {
              lease(job);
              db.update(
                  "update profilecontract.delivery_job set"
                      + " phase='DONE',email_sent_at=clock_timestamp(),error_code=null,lease_token=null,lease_until=null"
                      + " where agreement_id=? and lease_token=?",
                  agreement.get("id"),
                  job.get("lease_token"));
            });
      }
    } catch (Exception e) {
      String code =
          "PDF".equals(job.get("phase"))
              ? "CONTRACT_PDF_DELIVERY_FAILED"
              : "CONTRACT_EMAIL_DELIVERY_FAILED";
      tx.executeWithoutResult(
          s ->
              db.update(
                  "update profilecontract.delivery_job set"
                      + " error_code=?,next_attempt_at=clock_timestamp()+interval '5"
                      + " minutes',lease_token=null,lease_until=null where agreement_id=? and"
                      + " lease_token=?",
                  code,
                  job.get("agreement_id"),
                  job.get("lease_token")));
    }
  }
}
