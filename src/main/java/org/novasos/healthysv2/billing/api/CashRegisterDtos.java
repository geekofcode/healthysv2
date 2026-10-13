package org.novasos.healthysv2.billing.api;

import jakarta.validation.Valid;
import jakarta.validation.constraints.*;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import java.util.UUID;

public final class CashRegisterDtos {
    private CashRegisterDtos() {}
    public record CurrencyTotals(String currency,BigDecimal payments,BigDecimal refunds,BigDecimal net,long count) {}
    public record CashierTotals(UUID cashierPersonId,List<CurrencyTotals> totals) {}
    public record CashReport(Instant from,Instant to,UUID cashierPersonId,List<CurrencyTotals> totals,List<CashierTotals> cashiers) {}
    public record DeclaredAmount(@NotBlank @Pattern(regexp="[A-Za-z]{3}") String currency,
        @NotNull @DecimalMin("0.00") @Digits(integer=12,fraction=2) BigDecimal amount) {}
    public record CloseCashRequest(@NotNull Instant from,@NotNull Instant to,UUID cashierPersonId,
        @NotNull @Size(max=100) List<@Valid DeclaredAmount> declaredAmounts,@Size(max=1000) String note) {}
    public record ClosureAmount(String currency,BigDecimal payments,BigDecimal refunds,BigDecimal expectedNet,
        BigDecimal declaredAmount,BigDecimal difference) {}
    public record CashClosure(UUID id,String number,Instant from,Instant to,UUID cashierPersonId,
        Instant createdAt,UUID createdByPersonId,String note,List<ClosureAmount> amounts,
        List<CareBillingDtos.Receipt> receipts) {}
}
