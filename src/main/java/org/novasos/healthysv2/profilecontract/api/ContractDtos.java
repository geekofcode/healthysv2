package org.novasos.healthysv2.profilecontract.api;

import jakarta.validation.constraints.*;
import java.time.Instant;
import java.util.UUID;

public final class ContractDtos {
  private ContractDtos() {}

  public record TemplateRequest(
      @NotBlank @Pattern(regexp = "PATIENT|HOSPITAL|STAFF|ADMIN") String category,
      @NotBlank @Size(max = 2048) String termsUrl,
      @NotBlank @Size(max = 100) String termsVersion,
      @NotBlank @Size(max = 60000) String bodyText,
      @Pattern(regexp = "TEXT|HTML") String bodyFormat) {
    public TemplateRequest(String category, String termsUrl, String termsVersion, String bodyText) {
      this(category, termsUrl, termsVersion, bodyText, "TEXT");
    }

    public TemplateRequest {
      bodyFormat = bodyFormat == null ? "TEXT" : bodyFormat;
    }
  }

  public record Template(
      UUID id,
      String category,
      int version,
      String termsUrl,
      String termsVersion,
      String bodyText,
      Instant publishedAt,
      String renderedBodyText,
      String bodyFormat) {
    public Template(
        UUID id,
        String category,
        int version,
        String termsUrl,
        String termsVersion,
        String bodyText,
        Instant publishedAt,
        String renderedBodyText) {
      this(
          id,
          category,
          version,
          termsUrl,
          termsVersion,
          bodyText,
          publishedAt,
          renderedBodyText,
          "TEXT");
    }

    public Template {
      bodyFormat = bodyFormat == null ? "TEXT" : bodyFormat;
    }
  }

  public record SignRequest(
      @NotNull UUID templateId,
      boolean acceptTerms,
      @NotBlank @Size(max = 1500000) String signature,
      boolean guardianAcknowledgement) {}

  public record State(
      UUID profileId,
      boolean signed,
      UUID agreementId,
      String status,
      UUID templateId,
      Integer templateVersion,
      Instant signedAt,
      boolean pdfReady,
      String emailStatus) {}
}
