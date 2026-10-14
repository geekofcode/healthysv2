package org.novasos.healthysv2.profilecontract.api;

import java.util.UUID;

public interface ContractAccess {
  void requireSigned(UUID personId, UUID profileId);
}
