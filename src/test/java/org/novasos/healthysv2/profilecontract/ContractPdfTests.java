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

  @Test
  void richHtmlPreservesStylesListsAndTablesWithoutPrintingMarkup() throws Exception {
    var agreement =
        htmlAgreement(
            "<h1>Contrat enrichi</h1><p>Résumé — Ελληνικά — Кириллица &amp; droits"
                + " <strong>importants</strong>, <em>explication</em> et"
                + " <u>mention</u>.</p><ol><li>Premier engagement</li><li>Second"
                + " engagement</li></ol><ul><li>Information"
                + " accessible</li></ul><table><tr><th>Acteur</th><th>Obligation</th></tr><tr><td>Établissement</td><td>Confidentialité"
                + " du dossier</td></tr></table><p>Fin du contenu validé.</p>");
    try (var document = Loader.loadPDF(new ContractPdf().render(agreement))) {
      String text = new PDFTextStripper().getText(document);
      assertThat(text)
          .contains(
              "Contrat enrichi",
              "Ελληνικά",
              "Кириллица",
              "1. Premier engagement",
              "2. Second engagement",
              "Information accessible",
              "Confidentialité du dossier",
              "Fin du contenu validé.");
      assertThat(text).doesNotContain("<h1>", "<strong>", "<table>", "&amp;");
      var headingSizes = new ArrayList<Float>();
      var inspector =
          new PDFTextStripper() {
            @Override
            protected void writeString(
                String value, List<org.apache.pdfbox.text.TextPosition> positions)
                throws java.io.IOException {
              if (value.contains("Contrat enrichi"))
                positions.forEach(p -> headingSizes.add(p.getFontSizeInPt()));
              super.writeString(value, positions);
            }
          };
      inspector.getText(document);
      assertThat(headingSizes).anyMatch(size -> size >= 17);
      try (var body = document.getPage(0).getContents()) {
        assertThat(new String(body.readAllBytes(), java.nio.charset.StandardCharsets.ISO_8859_1))
            .contains("2 Tr");
      }
      assertThat(document.getPage(document.getNumberOfPages() - 1).getResources().getXObjectNames())
          .isNotEmpty();
    }
  }

  @Test
  void longHtmlTableWrapsAcrossPagesAndKeepsSeparateSignatureAndProof() throws Exception {
    String body =
        "<h1>Contrat avec tableau</h1><p>Introduction en"
            + " français.</p><table><tr><th>Élément</th><th>Description</th></tr><tr><td>Texte"
            + " long</td><td>"
            + "Texte contractuel détaillé pour pagination. ".repeat(500)
            + "</td></tr><tr><td>Dernière"
            + " ligne</td><td>MARQUEUR_FINAL_TABLEAU</td></tr></table><p>MARQUEUR_FIN_CONTRAT</p>";
    byte[] bytes = new ContractPdf().render(htmlAgreement(body));
    try (var document = Loader.loadPDF(bytes)) {
      assertThat(document.getNumberOfPages()).isGreaterThan(4);
      var strip = new PDFTextStripper();
      String text = strip.getText(document);
      assertThat(text)
          .contains(
              "MARQUEUR_FINAL_TABLEAU",
              "MARQUEUR_FIN_CONTRAT",
              "Parent de test",
              "Enfant de test",
              "https://example.test/conditions")
          .doesNotContain("<td>", "<tr>");
      strip.setStartPage(document.getNumberOfPages());
      String signature = strip.getText(document);
      assertThat(signature)
          .contains("Signature manuscrite", "Représentant légal", "a".repeat(64), "b".repeat(64))
          .doesNotContain("Texte contractuel détaillé");
      assertThat(document.getPage(document.getNumberOfPages() - 1).getResources().getXObjectNames())
          .isNotEmpty();
    }
    String preview = System.getProperty("healthys.contract.html.preview");
    if (preview != null) java.nio.file.Files.write(java.nio.file.Path.of(preview), bytes);
  }

  @Test
  void htmlRendererDoesNotLoadExternalImagesOrPrintExecutableContent() throws Exception {
    String html =
        "<h2>Texte sûr</h2><p>Clause visible</p><img"
            + " src='https://unreachable.example/image.png'><script>SECRET_EXECUTABLE</script><style>SECRET_CSS</style>";
    assertThatThrownBy(() -> new ContractPdf().render(htmlAgreement(html)))
        .isInstanceOf(IllegalArgumentException.class)
        .hasMessage("UNSAFE_CONTRACT_HTML");
  }

  private Map<String, Object> htmlAgreement(String body) throws java.io.IOException {
    Map<String, Object> agreement = new HashMap<>();
    agreement.put("id", UUID.randomUUID());
    agreement.put("profile_label", "Patient - enfant");
    agreement.put("template_version", 2);
    agreement.put("category", "PATIENT");
    agreement.put("organization_name", "");
    agreement.put("body_format", "HTML");
    agreement.put("body_snapshot", body);
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
    return agreement;
  }
}
