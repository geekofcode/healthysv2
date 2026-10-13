package org.novasos.healthysv2.billing;

import static org.novasos.healthysv2.billing.api.CareBillingDtos.*;
import org.novasos.healthysv2.billing.api.BillingDtos.InvoiceResponse;
import jakarta.validation.Valid;
import java.util.List;
import java.util.UUID;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/v1/billing/care")
class CareBillingController {
    private final CareBillingService service;
    CareBillingController(CareBillingService service) { this.service = service; }
    @GetMapping("/tariffs") List<TariffResponse> tariffs(@RequestParam UUID organizationId) { return service.tariffs(organizationId); }
    @PostMapping("/tariffs") TariffResponse createTariff(@Valid @RequestBody TariffRequest request) { return service.createTariff(request); }
    @PutMapping("/tariffs/{id}") TariffResponse updateTariff(@PathVariable UUID id, @Valid @RequestBody TariffRequest request) { return service.updateTariff(id,request); }
    @PostMapping("/episodes/{id}/invoice") InvoiceResponse invoice(@PathVariable UUID id,
        @RequestHeader("Idempotency-Key") String key, @Valid @RequestBody CreateCareInvoiceRequest request) { return service.createInvoice(id,key,request); }
    @GetMapping("/episodes/{id}/invoice") InvoiceResponse invoice(@PathVariable UUID id) { return service.episodeInvoice(id); }
    @PostMapping("/invoices/{id}/cash") Receipt cash(@PathVariable UUID id,
        @RequestHeader("Idempotency-Key") String key, @Valid @RequestBody CashRequest request) { return service.cash(id,key,request); }
    @PostMapping("/invoices/{id}/refunds") Receipt refund(@PathVariable UUID id,
        @RequestHeader("Idempotency-Key") String key, @Valid @RequestBody RefundRequest request) { return service.refund(id,key,request); }
    @GetMapping("/invoices/{id}/receipts") List<Receipt> receipts(@PathVariable UUID id) { return service.receipts(id); }
}
