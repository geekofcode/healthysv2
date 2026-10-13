package org.novasos.healthysv2.billing.api;

import jakarta.validation.Valid;
import jakarta.validation.constraints.*;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import java.util.UUID;

public final class CareBillingDtos {
    private CareBillingDtos() {}
    public record TariffRequest(@NotNull UUID organizationId, @NotBlank @Size(max=80) String code,
        @NotBlank @Size(max=500) String description, @NotBlank @Pattern(regexp="[A-Za-z]{3}") String currency,
        @NotNull @DecimalMin("0.01") @Digits(integer=12,fraction=2) BigDecimal unitPrice,
        @NotNull @DecimalMin("0") @DecimalMax("100") @Digits(integer=3,fraction=4) BigDecimal taxRate,
        boolean active, Long version) {}
    public record TariffResponse(UUID id, UUID organizationId, String code, String description,
        String currency, BigDecimal unitPrice, BigDecimal taxRate, boolean active, long version) {}
    public record CreateCareInvoiceRequest(@NotEmpty @Size(max=100) List<@Valid CareInvoiceLine> items) {}
    public record CareInvoiceLine(@NotNull UUID tariffId,
        @NotNull @DecimalMin("0.001") @Digits(integer=9,fraction=3) BigDecimal quantity) {}
    public record CashRequest(@NotNull @DecimalMin("0.01") @Digits(integer=12,fraction=2) BigDecimal amount,
        @NotBlank @Pattern(regexp="[A-Za-z]{3}") String currency) {}
    public record RefundRequest(@NotNull @DecimalMin("0.01") @Digits(integer=12,fraction=2) BigDecimal amount,
        @NotBlank @Pattern(regexp="[A-Za-z]{3}") String currency,
        @NotBlank @Size(max=500) String reason) {}
    public record Receipt(UUID id, String receiptNumber, UUID invoiceId, String operationType,
        BigDecimal amount, String currency, String reason, Instant occurredAt, UUID cashierPersonId) {}
}
