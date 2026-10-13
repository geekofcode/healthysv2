package org.novasos.healthysv2.identity.api;

import java.util.UUID;

/** Caller must authorize reception access before invoking this internal module facade. */
public interface ReceptionIdentityEditor {
    void updateReceptionIdentity(UUID personId, ReceptionIdentityUpdate request);
}
