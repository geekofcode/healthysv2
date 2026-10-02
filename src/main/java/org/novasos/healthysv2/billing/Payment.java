package org.novasos.healthysv2.billing;

import jakarta.persistence.*;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.*;

@Entity
@Table(name = "payment", schema = "billing")
class Payment {
    @Id private UUID id;
    @Column(name = "payment_number", nullable = false, unique = true, length = 50) private String number;
    @Column(name = "invoice_id", nullable = false) private UUID invoiceId;
    @Column(nullable = false, precision = 14, scale = 2) private BigDecimal amount;
    @Column(nullable = false, length = 3) private String currency;
    @Column(name = "payment_method", nullable = false, length = 50) private String method;
    @Column(name = "paid_at") private Instant paidAt;
    @Column(nullable = false, length = 30) private String status;
    @Column(name = "created_at", nullable = false) private Instant createdAt;
    @Column(name = "updated_at", nullable = false) private Instant updatedAt;
    @Version private long version;
    @OneToMany(mappedBy = "payment", cascade = CascadeType.ALL, orphanRemoval = true)
    private List<PaymentTransaction> transactions = new ArrayList<>();

    protected Payment() {}
    static Payment complete(UUID invoice, BigDecimal amount, String currency, String method,
                            String provider, String externalReference) {
        var payment = new Payment(); payment.id = UUID.randomUUID();
        payment.number = "PAY-" + payment.id.toString().replace("-", "").substring(0, 20).toUpperCase();
        payment.invoiceId = Objects.requireNonNull(invoice); payment.amount = Invoice.money(amount);
        payment.currency = currency; payment.method = normalizeMethod(method); payment.status = "COMPLETED";
        payment.paidAt = Instant.now(); payment.createdAt = payment.paidAt; payment.updatedAt = payment.paidAt;
        payment.transactions.add(PaymentTransaction.capture(payment, payment.amount, provider, externalReference));
        return payment;
    }
    private static String normalizeMethod(String value) {
        String method = value == null ? "" : value.trim().toUpperCase(Locale.ROOT);
        if (!Set.of("CASH","CARD","MOBILE_MONEY","INSURANCE","MUTUAL","THIRD_PARTY").contains(method))
            throw new IllegalArgumentException("Unsupported payment method");
        return method;
    }
    UUID getId(){return id;} String getNumber(){return number;} UUID getInvoiceId(){return invoiceId;}
    BigDecimal getAmount(){return amount;} String getCurrency(){return currency;} String getMethod(){return method;}
    Instant getPaidAt(){return paidAt;} String getStatus(){return status;} List<PaymentTransaction> getTransactions(){return transactions;}
}

@Entity
@Table(name = "payment_transaction", schema = "billing")
class PaymentTransaction {
    @Id private UUID id;
    @ManyToOne(fetch = FetchType.LAZY, optional = false) @JoinColumn(name = "payment_id") private Payment payment;
    @Column(length = 100) private String provider;
    @Column(name = "external_transaction_id", length = 255) private String externalReference;
    @Column(name = "transaction_type", nullable = false, length = 50) private String type;
    @Column(nullable = false, precision = 14, scale = 2) private BigDecimal amount;
    @Column(nullable = false, length = 30) private String status;
    @Column(name = "occurred_at", nullable = false) private Instant occurredAt;

    protected PaymentTransaction() {}
    static PaymentTransaction capture(Payment payment, BigDecimal amount, String provider, String reference) {
        var transaction = new PaymentTransaction(); transaction.id = UUID.randomUUID(); transaction.payment = payment;
        transaction.provider = blank(provider); transaction.externalReference = blank(reference);
        transaction.type = "CAPTURE"; transaction.amount = amount; transaction.status = "COMPLETED";
        transaction.occurredAt = Instant.now(); return transaction;
    }
    private static String blank(String value){return value==null||value.isBlank()?null:value.trim();}
    UUID getId(){return id;} String getProvider(){return provider;} String getExternalReference(){return externalReference;}
    String getType(){return type;} BigDecimal getAmount(){return amount;} String getStatus(){return status;}
    Instant getOccurredAt(){return occurredAt;}
}
