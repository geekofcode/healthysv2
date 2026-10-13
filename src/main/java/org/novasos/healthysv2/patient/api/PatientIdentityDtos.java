package org.novasos.healthysv2.patient.api;

import jakarta.validation.Valid;
import org.novasos.healthysv2.patient.api.PatientDtos.IdentifierRequest;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

/** Administrative identity only: never contains medical history, blood group or notes. */
public final class PatientIdentityDtos {
    private PatientIdentityDtos() {}

    public record CreateIdentityRequest(@NotNull UUID personId,
            @Size(max = 20) List<@NotNull @Valid IdentifierRequest> identifiers) {
        public CreateIdentityRequest {
            identifiers = identifiers == null ? List.of() : List.copyOf(identifiers);
        }
    }

    public record PatientIdentityResponse(UUID id, UUID personId, String patientNumber,
            String firstName, String lastName, LocalDate birthDate, String gender,
            String email, String phone, String status, List<PatientDtos.IdentifierResponse> identifiers) {}

    public record PersonIdentityResponse(UUID personId, String personNumber,
            String firstName, String lastName, LocalDate birthDate, String gender,
            String email, String phone, UUID patientId, String patientNumber) {}
}
