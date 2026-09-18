package org.novasos.healthysv2.identity.api;

import java.util.UUID;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

public record PersonAddressRequest(
        @NotNull UUID addressId,
        @NotBlank @Size(max = 30) String addressType,
        boolean primary) {
}
