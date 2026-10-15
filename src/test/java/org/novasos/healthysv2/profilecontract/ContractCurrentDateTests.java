package org.novasos.healthysv2.profilecontract;

import static org.assertj.core.api.Assertions.*;

import java.time.*;
import java.util.*;
import org.junit.jupiter.api.Test;
import org.novasos.healthysv2.profilecontract.api.ContractDtos.Template;

class ContractCurrentDateTests {
  @Test
  void dateUsesConfiguredZoneAtTheExactSignatureInstant() {
    Instant signed = Instant.parse("2026-10-15T01:16:00Z");
    assertThat(ProfileContractService.currentDate(signed, ZoneId.of("America/Toronto")))
        .isEqualTo("2026-10-14");
    assertThat(ProfileContractService.currentDate(signed, ZoneOffset.UTC)).isEqualTo("2026-10-15");
  }

  @Test
  void currentDateWorksInTextAndEveryHtmlZone() {
    var values =
        Map.of(
            "signerName",
            "Signer",
            "profileLabel",
            "Patient",
            "organizationName",
            "",
            "patientName",
            "Patient",
            "signedAt",
            "2026-10-15T01:16:00Z",
            "currentDate",
            "2026-10-14");
    var template =
        new Template(
            UUID.randomUUID(),
            "PATIENT",
            1,
            "https://example.test/terms",
            "v1",
            "Fait le {{currentDate}}",
            null,
            null);
    assertThat(ProfileContractService.renderBody(template, values)).isEqualTo("Fait le 2026-10-14");
    for (String html : List.of("<p>{{currentDate}}</p>", "<strong>{{currentDate}}</strong>"))
      assertThat(ContractHtml.render(html, values))
          .contains("2026-10-14")
          .doesNotContain("{{currentDate}}");
  }
}
