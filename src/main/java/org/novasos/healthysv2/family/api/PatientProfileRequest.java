package org.novasos.healthysv2.family.api;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import java.time.LocalDate;

/** Never accepts another existing person/patient identifier. */
public record PatientProfileRequest(
    @NotNull Kind kind,
    @NotBlank @Size(max = 120) String firstName,
    @NotBlank @Size(max = 120) String lastName,
    @NotNull LocalDate birthDate,
    @Size(max = 30) String gender,
    Relationship guardianRelationship,
    LocalDate guardianBirthDate) {
  public enum Kind {
    SELF,
    MINOR
  }

  public enum Relationship {
    PARENT,
    GUARDIAN
  }
}
