package org.novasos.healthysv2.identity.api;

import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

import org.novasos.healthysv2.identity.PersonStatus;

public record PersonResponse(
        UUID id,
        String personNumber,
        UUID keycloakUserId,
        String firstName,
        String middleName,
        String lastName,
        String gender,
        LocalDate birthDate,
        UUID preferredLanguageId,
        PersonStatus status,
        List<PersonAddressResponse> addresses,
        List<PersonContactResponse> contacts,
        List<EmergencyContactResponse> emergencyContacts,
        Instant createdAt,
        Instant updatedAt,
        UUID createdBy,
        UUID updatedBy,
        long version) {
}
