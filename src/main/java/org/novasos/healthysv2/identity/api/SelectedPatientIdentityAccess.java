package org.novasos.healthysv2.identity.api;

import java.util.UUID;

/**
 * Caller must validate ownership of the selected patient/guardian source before editing its person.
 */
public interface SelectedPatientIdentityAccess {
  PersonProfileResponse profilePerson(UUID personId);

  PersonProfileResponse updatePerson(UUID personId, UpdatePersonProfileRequest request);
}
