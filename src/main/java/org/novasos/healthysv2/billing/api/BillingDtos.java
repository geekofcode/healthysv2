package org.novasos.healthysv2.billing.api;

import jakarta.validation.Valid;
import jakarta.validation.constraints.*;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.*;

public final class BillingDtos {
    private BillingDtos() {}
    public record CreateInvoiceRequest(@NotNull UUID patientId, @NotNull UUID organizationId, UUID encounterId,
                                       @NotBlank @Pattern(regexp="[A-Za-z]{3}") String currency,
                                       @FutureOrPresent Instant dueAt,
                                       @NotEmpty List<@Valid InvoiceItemRequest> items) {}
    public record InvoiceItemRequest(@NotBlank @Size(max=50) String itemType, UUID referenceId,
                                     @NotBlank @Size(max=500) String description,
                                     @NotNull @DecimalMin("0.001") BigDecimal quantity,
                                     @NotNull @DecimalMin("0.00") BigDecimal unitPrice,
                                     @NotNull @DecimalMin("0.00") BigDecimal taxAmount) {}
    public record RecordPaymentRequest(@NotNull @DecimalMin("0.01") BigDecimal amount,
                                       @NotBlank @Pattern(regexp="[A-Za-z]{3}") String currency,
                                       @NotBlank String paymentMethod, @Size(max=100) String provider,
                                       @Size(max=255) String externalTransactionId) {}
    public record InvoiceSummary(UUID id,String invoiceNumber,UUID patientId,UUID organizationId,String currency,
                                 BigDecimal totalAmount,BigDecimal amountPaid,BigDecimal balance,
                                 Instant issuedAt,Instant dueAt,String status) {}
    public record InvoiceResponse(UUID id,String invoiceNumber,UUID patientId,UUID organizationId,UUID encounterId,
                                  String currency,BigDecimal subtotal,BigDecimal taxAmount,BigDecimal totalAmount,
                                  BigDecimal amountPaid,BigDecimal balance,Instant issuedAt,Instant dueAt,String status,
                                  List<InvoiceItemResponse> items,List<PaymentResponse> payments) {}
    public record InvoiceItemResponse(UUID id,String itemType,UUID referenceId,String description,BigDecimal quantity,
                                      BigDecimal unitPrice,BigDecimal taxAmount,BigDecimal totalAmount) {}
    public record PaymentResponse(UUID id,String paymentNumber,UUID invoiceId,BigDecimal amount,String currency,
                                  String paymentMethod,Instant paidAt,String status,
                                  List<PaymentTransactionResponse> transactions) {}
    public record PaymentTransactionResponse(UUID id,String provider,String externalTransactionId,String transactionType,
                                             BigDecimal amount,String status,Instant occurredAt) {}
}
