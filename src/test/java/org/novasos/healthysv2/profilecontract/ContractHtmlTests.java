package org.novasos.healthysv2.profilecontract;

import static org.assertj.core.api.Assertions.*;

import java.time.Instant;
import java.util.*;
import org.junit.jupiter.api.Test;
import org.novasos.healthysv2.profilecontract.api.ContractDtos.*;

class ContractHtmlTests {
  @Test
  void normalizedAndRenderedContentRemainWithinThePdfInputLimit() {
    assertThatThrownBy(() -> ContractHtml.sanitize("<p>" + "'".repeat(13000) + "</p>"))
        .isInstanceOf(IllegalArgumentException.class)
        .hasMessage("CONTRACT_HTML_TOO_LARGE");
    assertThatThrownBy(
            () ->
                ContractHtml.render(
                    "<p>{{signerName}}</p>", Map.of("signerName", "'".repeat(13000))))
        .isInstanceOf(IllegalArgumentException.class)
        .hasMessage("CONTRACT_HTML_TOO_LARGE");
  }

  @Test
  void richStructureAndHttpsLinksRemainWithoutActiveAttributes() {
    String html =
        ContractHtml.sanitize(
            "<h2>Accord</h2><p style='color:red' onclick='bad()'><strong>Important</strong> et"
                + " <em>lisible</em></p><ul><li>Premier</li></ul><table><tr><th>Nom</th><td>Valeur</td></tr></table><a"
                + " href='https://example.test/terms' target='_blank'>Conditions</a>");
    assertThat(html)
        .contains(
            "<h2>Accord</h2>",
            "<strong>Important</strong>",
            "<em>lisible</em>",
            "<ul>",
            "<li>",
            "<table>",
            "<tr>",
            "<th>",
            "<td>",
            "href=\"https://example.test/terms\"")
        .doesNotContain("style=", "onclick", "target=");
  }

  @Test
  void scriptsImagesAndExternalContentAreRejected() {
    for (String html :
        List.of(
            "<script>alert(1)</script>",
            "<img src='https://tracker.test/a'>",
            "<iframe src='https://x.test'></iframe>",
            "<style>body{display:none}</style>",
            "<!DOCTYPE html>")) {
      assertThatThrownBy(() -> ContractHtml.sanitize(html))
          .isInstanceOf(IllegalArgumentException.class)
          .hasMessage("UNSAFE_CONTRACT_HTML");
    }
  }

  @Test
  void unsafeSchemesCannotBecomeExecutableLinks() {
    String html =
        ContractHtml.sanitize(
            "<a href='jav&#97;script:alert(1)'>Lien</a><a href='data:text/html,evil'>Autre</a>");
    assertThat(html).doesNotContain("href=", "javascript:", "data:").contains("Lien", "Autre");
  }

  @Test
  void serverNamesCannotInjectMarkupAndSignatureMarkerStaysExact() {
    var template =
        new Template(
            UUID.randomUUID(),
            "PATIENT",
            1,
            "https://example.test/terms",
            "1",
            "<p>{{signerName}} représente {{patientName}}.</p><p>{{signature}}</p>",
            Instant.now(),
            null,
            "HTML");
    String rendered =
        ProfileContractService.renderBody(
            template,
            Map.of(
                "signerName",
                "Parent <img src=x onerror=bad()>",
                "profileLabel",
                "Patient",
                "organizationName",
                "",
                "patientName",
                "Enfant & <strong>nom</strong>",
                "signedAt",
                "date"));
    assertThat(rendered)
        .contains(
            "&lt;img src=x onerror=bad()&gt;",
            "Enfant &amp; &lt;strong&gt;nom&lt;/strong&gt;",
            "{{signature}}")
        .doesNotContain("<img", "<strong>nom");
  }

  @Test
  void replacementValuesAreNotEvaluatedAsMoreTemplateVariables() {
    assertThat(
            ContractHtml.render(
                "<p>{{signerName}} {{organizationName}}</p>",
                Map.of("signerName", "{{organizationName}}", "organizationName", "Hôpital")))
        .isEqualTo("<p>{{organizationName}} Hôpital</p>");
  }

  @Test
  void emptyFormattingCannotBePublishedAsAContract() {
    assertThatThrownBy(
            () ->
                ProfileContractService.validateTemplate(
                    new TemplateRequest(
                        "PATIENT", "https://example.test/terms", "1", "<p><br></p>", "HTML")))
        .isInstanceOf(IllegalArgumentException.class)
        .hasMessage("CONTRACT_TEXT_REQUIRED");
  }

  @Test
  void legacyTextRemainsLiteralAndDefaultsToText() {
    var request =
        new TemplateRequest("PATIENT", "https://example.test/terms", "1", "Texte <inchangé>");
    assertThat(request.bodyFormat()).isEqualTo("TEXT");
    var template =
        new Template(
            UUID.randomUUID(),
            "PATIENT",
            1,
            request.termsUrl(),
            "1",
            request.bodyText(),
            null,
            null);
    assertThat(
            ProfileContractService.renderBody(
                template,
                Map.of(
                    "signerName",
                    "Nom",
                    "profileLabel",
                    "Patient",
                    "organizationName",
                    "",
                    "patientName",
                    "",
                    "signedAt",
                    "date")))
        .isEqualTo("Texte <inchangé>");
  }
}
