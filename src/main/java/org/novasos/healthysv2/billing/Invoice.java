package org.novasos.healthysv2.billing;

import jakarta.persistence.*;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.Instant;
import java.util.*;

@Entity
@Table(name = "invoice", schema = "billing")
class Invoice {
    @Id private UUID id;
    @Column(name = "invoice_number", nullable = false, unique = true, length = 50) private String number;
    @Column(name = "patient_id", nullable = false) private UUID patientId;
    @Column(name = "organization_id", nullable = false) private UUID organizationId;
    @Column(name = "encounter_id") private UUID encounterId;
    @Column(nullable = false, length = 3) private String currency;
    @Column(nullable = false, precision = 14, scale = 2) private BigDecimal subtotal;
    @Column(name = "tax_amount", nullable = false, precision = 14, scale = 2) private BigDecimal taxAmount;
    @Column(name = "total_amount", nullable = false, precision = 14, scale = 2) private BigDecimal totalAmount;
    @Column(name = "amount_paid", nullable = false, precision = 14, scale = 2) private BigDecimal amountPaid;
    @Column(name = "issued_at", nullable = false) private Instant issuedAt;
    @Column(name = "due_at") private Instant dueAt;
    @Column(nullable = false, length = 30) private String status;
    @Column(name = "created_at", nullable = false) private Instant createdAt;
    @Column(name = "updated_at", nullable = false) private Instant updatedAt;
    @Version private long version;
    @OneToMany(mappedBy = "invoice", cascade = CascadeType.ALL, orphanRemoval = true)
    private List<InvoiceItem> items = new ArrayList<>();

    protected Invoice() {}

    static Invoice create(UUID patient, UUID organization, UUID encounter, String currency, Instant dueAt) {
        var invoice = new Invoice();
        invoice.id = UUID.randomUUID();
        invoice.number = "INV-" + invoice.id.toString().replace("-", "").substring(0, 20).toUpperCase();
        invoice.patientId = Objects.requireNonNull(patient);
        invoice.organizationId = Objects.requireNonNull(organization);
        invoice.encounterId = encounter;
        invoice.currency = normalizeCurrency(currency);
        invoice.subtotal = money(BigDecimal.ZERO);
        invoice.taxAmount = money(BigDecimal.ZERO);
        invoice.totalAmount = money(BigDecimal.ZERO);
        invoice.amountPaid = money(BigDecimal.ZERO);
        invoice.issuedAt = Instant.now();
        if (dueAt != null && dueAt.isBefore(invoice.issuedAt)) throw new IllegalArgumentException("Invalid due date");
        invoice.dueAt = dueAt;
        invoice.status = "DRAFT";
        invoice.createdAt = invoice.issuedAt;
        invoice.updatedAt = invoice.issuedAt;
        return invoice;
    }

    InvoiceItem addItem(String type, UUID reference, String description, BigDecimal quantity,
                        BigDecimal unitPrice, BigDecimal taxAmount) {
        ensureDraft();
        var item = InvoiceItem.create(this, type, reference, description, quantity, unitPrice, taxAmount);
        items.add(item);
        recalculate();
        return item;
    }

    void issue() {
        ensureDraft();
        if (items.isEmpty() || totalAmount.signum() <= 0) throw new IllegalStateException("Invoice has no amount");
        status = "ISSUED";
        issuedAt = Instant.now();
        touch();
    }

    void cancel() {
        if (!Set.of("DRAFT", "ISSUED").contains(status) || amountPaid.signum() > 0) {
            throw new IllegalStateException("Invoice cannot be cancelled");
        }
        status = "CANCELLED";
        touch();
    }

    void recordPayment(BigDecimal amount, String paymentCurrency) {
        if (!Set.of("ISSUED", "PARTIALLY_PAID").contains(status)) throw new IllegalStateException("Invoice not payable");
        BigDecimal normalized = money(amount);
        if (normalized.signum() <= 0 || !currency.equals(normalizeCurrency(paymentCurrency))
                || normalized.compareTo(balance()) > 0) throw new IllegalArgumentException("Invalid payment");
        amountPaid = amountPaid.add(normalized);
        status = amountPaid.compareTo(totalAmount) == 0 ? "PAID" : "PARTIALLY_PAID";
        touch();
    }

    BigDecimal balance() { return totalAmount.subtract(amountPaid); }
    private void recalculate() {
        subtotal = money(items.stream().map(InvoiceItem::subtotal).reduce(BigDecimal.ZERO, BigDecimal::add));
        taxAmount = money(items.stream().map(InvoiceItem::getTaxAmount).reduce(BigDecimal.ZERO, BigDecimal::add));
        totalAmount = subtotal.add(taxAmount);
        touch();
    }
    private void ensureDraft() { if (!"DRAFT".equals(status)) throw new IllegalStateException("Invoice is not draft"); }
    private void touch() { updatedAt = Instant.now(); }
    static BigDecimal money(BigDecimal value) { return Objects.requireNonNull(value).setScale(2, RoundingMode.HALF_UP); }
    private static String normalizeCurrency(String value) {
        String normalized = value == null ? "" : value.trim().toUpperCase(Locale.ROOT);
        if (!normalized.matches("[A-Z]{3}")) throw new IllegalArgumentException("Invalid currency");
        return normalized;
    }

    UUID getId(){return id;} String getNumber(){return number;} UUID getPatientId(){return patientId;}
    UUID getOrganizationId(){return organizationId;} UUID getEncounterId(){return encounterId;}
    String getCurrency(){return currency;} BigDecimal getSubtotal(){return subtotal;} BigDecimal getTaxAmount(){return taxAmount;}
    BigDecimal getTotalAmount(){return totalAmount;} BigDecimal getAmountPaid(){return amountPaid;}
    Instant getIssuedAt(){return issuedAt;} Instant getDueAt(){return dueAt;} String getStatus(){return status;}
    Instant getCreatedAt(){return createdAt;} Instant getUpdatedAt(){return updatedAt;} long getVersion(){return version;}
    List<InvoiceItem> getItems(){return items;}
}

@Entity
@Table(name = "invoice_item", schema = "billing")
class InvoiceItem {
    @Id private UUID id;
    @ManyToOne(fetch = FetchType.LAZY, optional = false) @JoinColumn(name = "invoice_id") private Invoice invoice;
    @Column(name = "item_type", nullable = false, length = 50) private String type;
    @Column(name = "reference_id") private UUID referenceId;
    @Column(nullable = false, length = 500) private String description;
    @Column(nullable = false, precision = 12, scale = 3) private BigDecimal quantity;
    @Column(name = "unit_price", nullable = false, precision = 14, scale = 2) private BigDecimal unitPrice;
    @Column(name = "tax_amount", nullable = false, precision = 14, scale = 2) private BigDecimal taxAmount;
    @Column(name = "total_amount", nullable = false, precision = 14, scale = 2) private BigDecimal totalAmount;

    protected InvoiceItem() {}
    static InvoiceItem create(Invoice invoice, String type, UUID reference, String description,
                              BigDecimal quantity, BigDecimal unitPrice, BigDecimal taxAmount) {
        if (quantity == null || quantity.signum() <= 0 || unitPrice == null || unitPrice.signum() < 0
                || taxAmount == null || taxAmount.signum() < 0) throw new IllegalArgumentException("Invalid amounts");
        var item = new InvoiceItem(); item.id = UUID.randomUUID(); item.invoice = invoice;
        item.type = required(type).toUpperCase(Locale.ROOT); item.referenceId = reference;
        item.description = required(description); item.quantity = quantity.setScale(3, RoundingMode.HALF_UP);
        item.unitPrice = Invoice.money(unitPrice); item.taxAmount = Invoice.money(taxAmount);
        item.totalAmount = item.subtotal().add(item.taxAmount); return item;
    }
    BigDecimal subtotal(){return Invoice.money(unitPrice.multiply(quantity));}
    private static String required(String value){if(value==null||value.isBlank())throw new IllegalArgumentException("Required");return value.trim();}
    UUID getId(){return id;} String getType(){return type;} UUID getReferenceId(){return referenceId;}
    String getDescription(){return description;} BigDecimal getQuantity(){return quantity;}
    BigDecimal getUnitPrice(){return unitPrice;} BigDecimal getTaxAmount(){return taxAmount;} BigDecimal getTotalAmount(){return totalAmount;}
}
