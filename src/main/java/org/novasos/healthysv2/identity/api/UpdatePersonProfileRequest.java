package org.novasos.healthysv2.identity.api;

import java.time.LocalDate;
import java.util.List;
import java.util.UUID;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.PastOrPresent;
import jakarta.validation.constraints.Size;

public record UpdatePersonProfileRequest(
        @NotBlank @Size(max = 120) String firstName,
        @Size(max = 120) String middleName,
        @NotBlank @Size(max = 120) String lastName,
        @Size(max = 30) String gender,
        @PastOrPresent LocalDate birthDate,
        UUID preferredLanguageId,
        @Valid HomeAddress homeAddress,
        @Size(max = 20) List<@Valid PersonContactRequest> contacts,
        @Size(max = 20) List<@Valid EmergencyContactRequest> emergencyContacts) {
    public UpdatePersonProfileRequest {
        contacts = contacts == null ? List.of() : List.copyOf(contacts);
        emergencyContacts = emergencyContacts == null ? List.of() : List.copyOf(emergencyContacts);
    }
    public record HomeAddress(
            @NotBlank @Size(max = 255) String line1,
            @Size(max = 255) String line2,
            @NotBlank @Size(max = 150) String city,
            @Size(max = 150) String province,
            @Size(max = 30) String postalCode,
            UUID countryId) {}
}
