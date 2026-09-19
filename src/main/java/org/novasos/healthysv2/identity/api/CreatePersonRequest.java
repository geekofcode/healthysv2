package org.novasos.healthysv2.identity.api;

import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record CreatePersonRequest(
        @NotBlank @Size(max = 50) String personNumber,
        UUID keycloakUserId,
        @NotBlank @Size(max = 120) String firstName,
        @Size(max = 120) String middleName,
        @NotBlank @Size(max = 120) String lastName,
        @Size(max = 30) String gender,
        LocalDate birthDate,
        UUID preferredLanguageId,
        List<@Valid PersonAddressRequest> addresses,
        List<@Valid PersonContactRequest> contacts,
        List<@Valid EmergencyContactRequest> emergencyContacts) {

    public CreatePersonRequest {
        addresses = addresses == null ? List.of() : List.copyOf(addresses);
        contacts = contacts == null ? List.of() : List.copyOf(contacts);
        emergencyContacts = emergencyContacts == null
                ? List.of()
                : List.copyOf(emergencyContacts);
    }
}
