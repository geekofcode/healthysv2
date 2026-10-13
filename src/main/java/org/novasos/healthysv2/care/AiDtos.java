package org.novasos.healthysv2.care;

import jakarta.validation.constraints.*;
import java.time.Instant;
import java.util.*;

public final class AiDtos {
  private AiDtos() {}

  public enum Kind {
    SUMMARY,
    REPORT,
    TRANSCRIPTION
  }

  public record Request(@NotNull Kind kind, @Size(max = 30000) String notes) {}

  public record Job(
      UUID id,
      Kind kind,
      String status,
      Map<String, Object> output,
      boolean obsolete,
      String errorCode,
      Instant createdAt,
      String model) {}
}
