package org.novasos.healthysv2.identity.api;

import java.util.UUID;

public record PersonContactResponse(
        UUID id,
        String type,
        String value,
        boolean primary,
        boolean verified) {
}
