package org.novasos.healthysv2.identity.api;

import java.util.UUID;

public record PersonAddressResponse(
        UUID id,
        UUID addressId,
        String addressType,
        boolean primary) {
}
