package org.novasos.healthysv2.pharmacy;

import jakarta.persistence.*;
import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.util.*;

@Entity
@Table(name = "dispense", schema = "pharmacy")
class Dispense {
    @Id private UUID id;
    @Column(name = "prescription_id", nullable = false) private UUID prescriptionId;
    @Column(name = "dispense_number", nullable = false, unique = true, length = 50) private String number;
    @Column(name = "pharmacy_organization_id", nullable = false) private UUID pharmacyOrganizationId;
    @Column(name = "pharmacist_id") private UUID pharmacistId;
    @Column(name = "dispensed_at", nullable = false) private Instant dispensedAt;
    @Column(nullable = false, length = 30) private String status;
    @OneToMany(mappedBy = "dispense", cascade = CascadeType.ALL, orphanRemoval = true)
    private List<DispenseItem> items = new ArrayList<>();

    protected Dispense() {}

    static Dispense create(UUID prescriptionId, UUID pharmacy, UUID pharmacist, List<DispenseLine> lines) {
        var dispense = new Dispense();
        dispense.id = UUID.randomUUID();
        dispense.prescriptionId = Objects.requireNonNull(prescriptionId);
        dispense.number = "DSP-" + dispense.id.toString().replace("-", "").substring(0, 20).toUpperCase();
        dispense.pharmacyOrganizationId = Objects.requireNonNull(pharmacy);
        dispense.pharmacistId = Objects.requireNonNull(pharmacist);
        dispense.dispensedAt = Instant.now();
        dispense.status = "COMPLETED";
        lines.forEach(line -> dispense.items.add(DispenseItem.create(dispense, line.prescriptionItemId(),
                line.quantity(), line.batch())));
        return dispense;
    }

    UUID getId() { return id; }
    UUID getPrescriptionId() { return prescriptionId; }
    String getNumber() { return number; }
    UUID getPharmacyOrganizationId() { return pharmacyOrganizationId; }
    UUID getPharmacistId() { return pharmacistId; }
    Instant getDispensedAt() { return dispensedAt; }
    String getStatus() { return status; }
    List<DispenseItem> getItems() { return items; }

    record DispenseLine(UUID prescriptionItemId, BigDecimal quantity, String batch) {}
}

@Entity
@Table(name = "dispense_item", schema = "pharmacy")
class DispenseItem {
    @Id private UUID id;
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "dispense_id", nullable = false)
    private Dispense dispense;
    @Column(name = "prescription_item_id", nullable = false) private UUID prescriptionItemId;
    @Column(name = "quantity_dispensed", nullable = false, precision = 12, scale = 3) private BigDecimal quantity;
    @Column(name = "batch_number", length = 100) private String batch;

    protected DispenseItem() {}

    static DispenseItem create(Dispense dispense, UUID prescriptionItemId, BigDecimal quantity, String batch) {
        var item = new DispenseItem();
        item.id = UUID.randomUUID();
        item.dispense = dispense;
        item.prescriptionItemId = Objects.requireNonNull(prescriptionItemId);
        item.quantity = Objects.requireNonNull(quantity);
        item.batch = PharmacyValues.required(batch);
        return item;
    }

    UUID getId() { return id; }
    UUID getPrescriptionItemId() { return prescriptionItemId; }
    BigDecimal getQuantity() { return quantity; }
    String getBatch() { return batch; }
}

@Entity
@Table(name = "medication_stock", schema = "pharmacy",
        uniqueConstraints = @UniqueConstraint(columnNames = {"organization_id", "medication_catalog_id", "batch_number"}))
class MedicationStock {
    @Id private UUID id;
    @Column(name = "organization_id", nullable = false) private UUID organizationId;
    @Column(name = "medication_catalog_id", nullable = false) private UUID medicationId;
    @Column(name = "batch_number", length = 100) private String batch;
    @Column(nullable = false, precision = 14, scale = 3) private BigDecimal quantity;
    @Column(name = "expiration_date") private LocalDate expirationDate;

    protected MedicationStock() {}

    static MedicationStock create(UUID organization, UUID medication, String batch,
                                  BigDecimal quantity, LocalDate expiration) {
        if (quantity == null || quantity.signum() <= 0) throw new IllegalArgumentException("Positive quantity required");
        var stock = new MedicationStock();
        stock.id = UUID.randomUUID();
        stock.organizationId = Objects.requireNonNull(organization);
        stock.medicationId = Objects.requireNonNull(medication);
        stock.batch = PharmacyValues.required(batch);
        stock.quantity = quantity;
        stock.expirationDate = expiration;
        return stock;
    }

    void replenish(BigDecimal amount, LocalDate expiration) {
        if (amount == null || amount.signum() <= 0) throw new IllegalArgumentException("Positive quantity required");
        quantity = quantity.add(amount);
        if (expiration != null) expirationDate = expiration;
    }

    void consume(BigDecimal amount) {
        if (expired() || amount == null || amount.signum() <= 0 || quantity.compareTo(amount) < 0) {
            throw new IllegalStateException("Insufficient stock");
        }
        quantity = quantity.subtract(amount);
    }

    boolean expired() { return expirationDate != null && expirationDate.isBefore(LocalDate.now()); }
    UUID getId() { return id; }
    UUID getOrganizationId() { return organizationId; }
    UUID getMedicationId() { return medicationId; }
    String getBatch() { return batch; }
    BigDecimal getQuantity() { return quantity; }
    LocalDate getExpirationDate() { return expirationDate; }
}

final class PharmacyValues {
    private PharmacyValues() {}
    static String required(String value) {
        if (value == null || value.isBlank()) throw new IllegalArgumentException("Required value");
        return value.trim();
    }
}
