package org.novasos.healthysv2.prescription;

import java.math.BigDecimal;
import java.util.List;
import java.util.UUID;

/** Public contract used by the pharmacy module to dispense an ordonnance. */
public interface PrescriptionDispensing {
    void verifyReadAccess(UUID prescriptionId);

    PrescriptionForDispensing prepare(UUID prescriptionId);

    void record(UUID prescriptionId, List<DispenseLine> lines);

    record PrescriptionForDispensing(UUID id, UUID patientId, UUID organizationId, String status,
                                     List<ItemForDispensing> items) {
        public ItemForDispensing item(UUID itemId) {
            return items.stream().filter(item -> item.id().equals(itemId)).findFirst()
                    .orElseThrow(() -> new IllegalArgumentException("Unknown prescription item"));
        }
    }

    record ItemForDispensing(UUID id, UUID medicationCatalogId, BigDecimal quantityRemaining) {
    }

    record DispenseLine(UUID prescriptionItemId, BigDecimal quantity) {
    }
}
