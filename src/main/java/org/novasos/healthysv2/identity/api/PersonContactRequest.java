package org.novasos.healthysv2.identity.api;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record PersonContactRequest(
        @NotBlank @Size(max = 20) String type,
        @NotBlank @Size(max = 255) String value,
        boolean primary,
        boolean verified) {
}
