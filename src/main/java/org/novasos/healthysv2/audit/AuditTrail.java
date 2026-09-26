package org.novasos.healthysv2.audit;

import java.util.UUID;

public interface AuditTrail {
    void change(UUID actor, UUID organization, String module, String entityType, UUID entityId,
                String action, Object oldValue, Object newValue);
    void access(UUID actor, UUID patient, UUID organization, String resourceType, UUID resourceId,
                String action, String reason, Object context);
}
