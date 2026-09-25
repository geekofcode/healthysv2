package org.novasos.healthysv2.prescription;

import static org.assertj.core.api.Assertions.*;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.*;
import org.junit.jupiter.api.Test;

class PrescriptionTests {
    @Test void recordsPartialAndCompleteDispensing() {
        var prescription = Prescription.create(UUID.randomUUID(), null, UUID.randomUUID(),
                UUID.randomUUID(), Instant.now().plusSeconds(3600));
        var item = prescription.addItem(UUID.randomUUID(), "500 mg", "Twice daily", "ORAL",
                "5 days", new BigDecimal("10"), null);
        prescription.recordDispense(List.of(new PrescriptionDispensing.DispenseLine(item.getId(), new BigDecimal("4"))));
        assertThat(prescription.getStatus()).isEqualTo("PARTIALLY_DISPENSED");
        assertThat(item.getQuantityDispensed()).isEqualByComparingTo("4");
        prescription.recordDispense(List.of(new PrescriptionDispensing.DispenseLine(item.getId(), new BigDecimal("6"))));
        assertThat(prescription.getStatus()).isEqualTo("DISPENSED");
        assertThatThrownBy(prescription::cancel).isInstanceOf(IllegalStateException.class);
    }

    @Test void rejectsOverDispensingAndDuplicateLinesAtomically() {
        var prescription = Prescription.create(UUID.randomUUID(), null, UUID.randomUUID(), null, null);
        var item = prescription.addItem(UUID.randomUUID(), "1 tablet", "Daily", "ORAL",
                "3 days", new BigDecimal("3"), null);
        assertThatThrownBy(() -> prescription.recordDispense(List.of(
                new PrescriptionDispensing.DispenseLine(item.getId(), new BigDecimal("4")))))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> prescription.recordDispense(List.of(
                new PrescriptionDispensing.DispenseLine(item.getId(), BigDecimal.ONE),
                new PrescriptionDispensing.DispenseLine(item.getId(), BigDecimal.ONE))))
                .isInstanceOf(IllegalArgumentException.class);
        assertThat(item.getQuantityDispensed()).isEqualByComparingTo("0");
    }
}
