package org.novasos.healthysv2.profilecontract;

import static org.assertj.core.api.Assertions.*;

import java.sql.Timestamp;
import java.time.Instant;
import java.util.*;
import org.apache.pdfbox.Loader;
import org.apache.pdfbox.text.PDFTextStripper;
import org.junit.jupiter.api.Test;

class ContractPdfTests {
  @Test
  void longContractHasReservedSignaturePageAndImmutableSignerProof() throws Exception {
    Map<String, Object> agreement = new HashMap<>();
    agreement.put("id", UUID.randomUUID());
    agreement.put("profile_label", "Patient - enfant");
    agreement.put("template_version", 2);
    agreement.put("category", "PATIENT");
    agreement.put("organization_name", "");
    agreement.put(
        "body_snapshot",
        "Contenu de modèle de test. Aucune clause juridique fictive.\n".repeat(100));
    agreement.put("signer_name", "Parent de test");
    agreement.put("signer_email", "parent@example.test");
    agreement.put("guardian_acknowledgement", true);
    agreement.put("patient_name", "Enfant de test");
    agreement.put("signed_at", Timestamp.from(Instant.parse("2026-10-14T01:00:00Z")));
    agreement.put("terms_version", "test-2");
    agreement.put("terms_url", "https://example.test/conditions");
    agreement.put("signature_png", SignatureImageTests.png(true));
    agreement.put("snapshot_sha256", "a".repeat(64));
    agreement.put("signature_sha256", "b".repeat(64));
    byte[] bytes = new ContractPdf().render(agreement);
    try (var document = Loader.loadPDF(bytes)) {
      assertThat(document.getNumberOfPages()).isGreaterThan(2);
      var strip = new PDFTextStripper();
      String all = strip.getText(document);
      assertThat(all)
          .contains("Parent de test", "Enfant de test", "https://example.test/conditions");
      strip.setStartPage(document.getNumberOfPages());
      assertThat(strip.getText(document)).contains("Signature manuscrite", "Représentant légal");
      assertThat(document.getPage(document.getNumberOfPages() - 1).getResources().getXObjectNames())
          .isNotEmpty();
    }
    String preview = System.getProperty("healthys.contract.preview");
    if (preview != null) java.nio.file.Files.write(java.nio.file.Path.of(preview), bytes);
  }
}
