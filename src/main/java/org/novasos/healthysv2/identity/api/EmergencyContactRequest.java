package org.novasos.healthysv2.identity.api;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record EmergencyContactRequest(
        @NotBlank @Size(max = 120) String firstName,
        @Size(max = 120) String lastName,
        @Size(max = 80) String relationship,
        @NotBlank @Size(max = 50) String phone,
        @Email @Size(max = 255) String email) {
}
