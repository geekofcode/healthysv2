package org.novasos.healthysv2.prescription;

import jakarta.persistence.CascadeType;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.OneToMany;
import jakarta.persistence.Table;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Objects;
import java.util.Set;
import java.util.UUID;

@Entity
@Table(name = "prescription", schema = "prescription")
class Prescription {
    @Id
    private UUID id;
    @Column(name = "prescription_number", nullable = false, unique = true, length = 50)
    private String number;
    @Column(name = "patient_id", nullable = false)
    private UUID patientId;
    @Column(name = "consultation_id")
    private UUID consultationId;
    @Column(name = "prescriber_id", nullable = false)
    private UUID prescriberId;
    @Column(name = "organization_id")
    private UUID organizationId;
    @Column(name = "prescribed_at", nullable = false)
    private Instant prescribedAt;
    @Column(name = "expires_at")
    private Instant expiresAt;
    @Column(nullable = false, length = 30)
    private String status;
    @OneToMany(mappedBy = "prescription", cascade = CascadeType.ALL, orphanRemoval = true)
    private List<PrescriptionItem> items = new ArrayList<>();

    protected Prescription() {
    }

    static Prescription create(UUID patient, UUID consultation, UUID prescriber, UUID organization, Instant expiresAt) {
        var prescription = new Prescription();
        prescription.id = UUID.randomUUID();
        prescription.number = "RX-" + prescription.id.toString().replace("-", "").substring(0, 20).toUpperCase();
        prescription.patientId = Objects.requireNonNull(patient);
        prescription.consultationId = consultation;
        prescription.prescriberId = Objects.requireNonNull(prescriber);
        prescription.organizationId = organization;
        prescription.prescribedAt = Instant.now();
        if (expiresAt != null && !expiresAt.isAfter(prescription.prescribedAt)) {
            throw new IllegalArgumentException("Invalid expiration");
        }
        prescription.expiresAt = expiresAt;
        prescription.status = "ACTIVE";
        return prescription;
    }

    PrescriptionItem addItem(UUID medication, String dosage, String frequency, String route,
                             String duration, BigDecimal quantity, String instructions) {
        ensureOpen();
        if (items.stream().anyMatch(item -> item.getMedicationId().equals(medication))) {
            throw new IllegalArgumentException("Duplicate medication");
        }
        var item = PrescriptionItem.create(this, medication, dosage, frequency, route, duration, quantity, instructions);
        items.add(item);
        return item;
    }

    void recordDispense(List<PrescriptionDispensing.DispenseLine> lines) {
        ensureOpen();
        if (expired()) {
            throw new IllegalStateException("Prescription expired");
        }
        if (lines == null || lines.isEmpty()) {
            throw new IllegalArgumentException("Empty dispense");
        }
        var seen = new HashSet<UUID>();
        lines.forEach(line -> {
            if (!seen.add(line.prescriptionItemId())) {
                throw new IllegalArgumentException("Duplicate dispense item");
            }
            item(line.prescriptionItemId()).validateDispense(line.quantity());
        });
        lines.forEach(line -> item(line.prescriptionItemId()).recordDispense(line.quantity()));
        status = items.stream().allMatch(PrescriptionItem::fullyDispensed)
                ? "DISPENSED"
                : "PARTIALLY_DISPENSED";
    }

    void cancel() {
        if (Set.of("DISPENSED", "CANCELLED").contains(status)) {
            throw new IllegalStateException("Prescription cannot be cancelled");
        }
        status = "CANCELLED";
    }

    boolean expired() {
        return expiresAt != null && !expiresAt.isAfter(Instant.now());
    }

    PrescriptionItem item(UUID itemId) {
        return items.stream().filter(item -> item.getId().equals(itemId)).findFirst()
                .orElseThrow(() -> new IllegalArgumentException("Unknown prescription item"));
    }

    private void ensureOpen() {
        if (Set.of("DISPENSED", "CANCELLED").contains(status)) {
            throw new IllegalStateException("Prescription closed");
        }
    }

    UUID getId() { return id; }
    String getNumber() { return number; }
    UUID getPatientId() { return patientId; }
    UUID getConsultationId() { return consultationId; }
    UUID getPrescriberId() { return prescriberId; }
    UUID getOrganizationId() { return organizationId; }
    Instant getPrescribedAt() { return prescribedAt; }
    Instant getExpiresAt() { return expiresAt; }
    String getStatus() { return status; }
    List<PrescriptionItem> getItems() { return items; }
}

@Entity
@Table(name = "prescription_item", schema = "prescription")
class PrescriptionItem {
    @Id
    private UUID id;
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "prescription_id", nullable = false)
    private Prescription prescription;
    @Column(name = "medication_catalog_id", nullable = false)
    private UUID medicationId;
    @Column(length = 100)
    private String dosage;
    @Column(length = 100)
    private String frequency;
    @Column(length = 100)
    private String route;
    @Column(length = 100)
    private String duration;
    @Column(nullable = false, precision = 12, scale = 3)
    private BigDecimal quantity;
    @Column(name = "quantity_dispensed", nullable = false, precision = 12, scale = 3)
    private BigDecimal quantityDispensed;
    @Column(columnDefinition = "text")
    private String instructions;

    protected PrescriptionItem() {
    }

    static PrescriptionItem create(Prescription prescription, UUID medication, String dosage, String frequency,
                                   String route, String duration, BigDecimal quantity, String instructions) {
        if (quantity == null || quantity.signum() <= 0) {
            throw new IllegalArgumentException("Quantity required");
        }
        var item = new PrescriptionItem();
        item.id = UUID.randomUUID();
        item.prescription = prescription;
        item.medicationId = Objects.requireNonNull(medication);
        item.dosage = required(dosage);
        item.frequency = required(frequency);
        item.route = required(route).toUpperCase();
        item.duration = required(duration);
        item.quantity = quantity;
        item.quantityDispensed = BigDecimal.ZERO;
        item.instructions = optional(instructions);
        return item;
    }

    void validateDispense(BigDecimal amount) {
        if (amount == null || amount.signum() <= 0 || amount.compareTo(remaining()) > 0) {
            throw new IllegalArgumentException("Invalid dispense quantity");
        }
    }

    void recordDispense(BigDecimal amount) {
        validateDispense(amount);
        quantityDispensed = quantityDispensed.add(amount);
    }

    boolean fullyDispensed() {
        return quantityDispensed.compareTo(quantity) >= 0;
    }

    BigDecimal remaining() {
        return quantity.subtract(quantityDispensed);
    }

    private static String required(String value) {
        if (value == null || value.isBlank()) {
            throw new IllegalArgumentException("Required value");
        }
        return value.trim();
    }

    private static String optional(String value) {
        return value == null || value.isBlank() ? null : value.trim();
    }

    UUID getId() { return id; }
    UUID getMedicationId() { return medicationId; }
    String getDosage() { return dosage; }
    String getFrequency() { return frequency; }
    String getRoute() { return route; }
    String getDuration() { return duration; }
    BigDecimal getQuantity() { return quantity; }
    BigDecimal getQuantityDispensed() { return quantityDispensed; }
    String getInstructions() { return instructions; }
}
