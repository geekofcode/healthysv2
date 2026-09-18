package org.novasos.healthysv2.shared.api.error;

import java.time.Instant;
import java.util.List;

public record ErrorResponse(
        Instant timestamp,
        int status,
        String error,
        String code,
        String message,
        String path,
        String correlationId,
        List<FieldViolation> violations) {

    public ErrorResponse {
        violations = violations == null ? List.of() : List.copyOf(violations);
    }
}
