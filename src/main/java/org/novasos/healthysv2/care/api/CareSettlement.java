package org.novasos.healthysv2.care.api;

import java.util.UUID;

/** Internal transactional handoff from confirmed, scoped cash accounting. */
public interface CareSettlement {
    void markSettled(UUID episodeId, UUID organizationId);
    void reopenPayment(UUID episodeId, UUID organizationId);
}
