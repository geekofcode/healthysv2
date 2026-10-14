package org.novasos.healthysv2.identity.api;

import java.util.Optional;
import java.util.UUID;

/** Internal identity-only operations; callers must authorize establishment/team management. */
public interface EmployeeAccountProvisioning {
  AccountIdentity ensureAccount(String firstName, String lastName, String email);

  Optional<AccountIdentity> lookupExactEmail(String email);

  record AccountIdentity(
      UUID personId,
      String firstName,
      String lastName,
      String email,
      String status,
      String accountStatus) {}
}
