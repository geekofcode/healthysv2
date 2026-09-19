package org.novasos.healthysv2.identity.api;

import java.util.UUID;

public record EmergencyContactResponse(
        UUID id,
        String firstName,
        String lastName,
        String relationship,
        String phone,
        String email) {
}
