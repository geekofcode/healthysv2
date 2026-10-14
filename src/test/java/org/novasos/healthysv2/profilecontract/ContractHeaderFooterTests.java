package org.novasos.healthysv2.profilecontract;

import static org.assertj.core.api.Assertions.*;

import java.util.*;
import org.junit.jupiter.api.Test;
import org.novasos.healthysv2.profilecontract.api.ContractDtos.*;

class ContractHeaderFooterTests {
  @Test
  void legacyRequestsAndTemplatesKeepBlankZones() {
    var request = new TemplateRequest("PATIENT", "https://example.test/terms", "1", "Contrat");
    assertThat(request.headerHtml()).isEmpty();
    assertThat(request.footerHtml()).isEmpty();
    var template =
        new Template(
            UUID.randomUUID(), "PATIENT", 1, request.termsUrl(), "1", "Contrat", null, null);
    assertThat(template.headerHtml()).isEmpty();
    assertThat(template.footerHtml()).isEmpty();
    assertThat(template.renderedHeaderHtml()).isEmpty();
    assertThat(template.renderedFooterHtml()).isEmpty();
  }

  @Test
  void zoneVariablesAreEscapedWhilePageNumbersWaitForFinalPdf() {
    String output =
        ContractHtml.render(
            "<p>{{signerName}} - {{patientName}} - Page {{pageNumber}}/{{pageCount}}</p>",
            Map.of("signerName", "<b>Parent</b>", "patientName", "Enfant & frère"));
    assertThat(output)
        .isEqualTo(
            "<p>&lt;b&gt;Parent&lt;/b&gt; - Enfant &amp; frère - Page"
                + " {{pageNumber}}/{{pageCount}}</p>");
  }

  @Test
  void activeHeaderCannotEnterTemplateStorage() {
    assertThatThrownBy(
            () ->
                ProfileContractService.validateTemplate(
                    new TemplateRequest(
                        "PATIENT",
                        "https://example.test/terms",
                        "1",
                        "Contrat",
                        "TEXT",
                        "<script>bad()</script>",
                        "")))
        .isInstanceOf(IllegalArgumentException.class)
        .hasMessage("UNSAFE_CONTRACT_HTML");
  }

  @Test
  void zoneLimitIsEnforcedOutsideControllerValidation() {
    assertThatThrownBy(
            () ->
                ProfileContractService.validateTemplate(
                    new TemplateRequest(
                        "PATIENT",
                        "https://example.test/terms",
                        "1",
                        "Contrat",
                        "TEXT",
                        "x".repeat(5001),
                        "")))
        .isInstanceOf(IllegalArgumentException.class)
        .hasMessage("CONTRACT_ZONE_TOO_LARGE");
  }
}
