package org.novasos.healthysv2.identity.api;

import jakarta.validation.constraints.*;
import java.time.LocalDate;

/** Full demographic update by an authorized reception; does not change authentication linkage. */
public record ReceptionIdentityUpdate(@NotBlank @Size(max = 120) String firstName,
        @Size(max = 120) String middleName, @NotBlank @Size(max = 120) String lastName,
        @Size(max = 30) String gender, @PastOrPresent LocalDate birthDate,
        @Email @Size(max = 255) String email, @Size(max = 100) String phone) {}
