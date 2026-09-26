package org.novasos.healthysv2.billing;

import static org.novasos.healthysv2.billing.api.BillingDtos.*;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import java.net.URI;
import java.util.*;
import org.novasos.healthysv2.shared.api.ApiPaths;
import org.novasos.healthysv2.shared.api.dto.PageResponse;
import org.springframework.data.domain.Pageable;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping(ApiPaths.V1+"/invoices")
@Tag(name="Billing",description="Invoice lifecycle, balances and payments")
@PreAuthorize("hasAnyRole('PLATFORM_ADMIN','HOSPITAL_ADMIN','HOSPITAL_AGENT','CASHIER','ACCOUNTANT','PATIENT')")
class BillingController {
    private static final String WRITE="hasAnyRole('PLATFORM_ADMIN','HOSPITAL_ADMIN','HOSPITAL_AGENT','CASHIER','ACCOUNTANT')";
    private final BillingService service;
    BillingController(BillingService service){this.service=service;}

    @PostMapping @PreAuthorize(WRITE) @Operation(summary="Create a draft invoice")
    ResponseEntity<InvoiceResponse> create(@Valid @RequestBody CreateInvoiceRequest request){var response=service.create(request);return ResponseEntity.created(URI.create(ApiPaths.V1+"/invoices/"+response.id())).body(response);}
    @GetMapping @Operation(summary="Search invoices in the authorized billing scope")
    PageResponse<InvoiceSummary> search(@RequestParam(required=false)UUID patientId,@RequestParam(required=false)UUID organizationId,@RequestParam(required=false)String status,Pageable pageable){return service.search(patientId,organizationId,status,pageable);}
    @GetMapping("/{id}") InvoiceResponse find(@PathVariable UUID id){return service.find(id);}
    @PostMapping("/{id}/issue") @PreAuthorize(WRITE) InvoiceResponse issue(@PathVariable UUID id){return service.issue(id);}
    @PostMapping("/{id}/cancel") @PreAuthorize(WRITE) InvoiceResponse cancel(@PathVariable UUID id){return service.cancel(id);}
    @PostMapping("/{id}/payments") @PreAuthorize(WRITE) @Operation(summary="Record a successful payment")
    ResponseEntity<PaymentResponse> payment(@PathVariable UUID id,@Valid @RequestBody RecordPaymentRequest request){return ResponseEntity.status(201).body(service.recordPayment(id,request));}
    @GetMapping("/{id}/payments") List<PaymentResponse> payments(@PathVariable UUID id){return service.listPayments(id);}
}
