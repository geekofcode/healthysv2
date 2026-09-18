package org.novasos.healthysv2.shared.api.error;

public record FieldViolation(
        String field,
        String message) {
}
