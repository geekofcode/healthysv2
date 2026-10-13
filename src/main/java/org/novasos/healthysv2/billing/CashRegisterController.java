package org.novasos.healthysv2.billing;

import static org.novasos.healthysv2.billing.api.CashRegisterDtos.*;
import jakarta.validation.Valid;
import java.time.Instant;
import java.util.List;
import java.util.UUID;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/v1/billing/care")
class CashRegisterController {
    private final CashRegisterService service;
    CashRegisterController(CashRegisterService service) { this.service=service; }
    @GetMapping("/cash-report") CashReport report(@RequestParam Instant from,@RequestParam Instant to,
        @RequestParam(required=false) UUID cashierPersonId) { return service.report(from,to,cashierPersonId); }
    @PostMapping("/cash-closures") CashClosure close(@RequestHeader("Idempotency-Key") String key,
        @Valid @RequestBody CloseCashRequest request) { return service.close(key,request); }
    @GetMapping("/cash-closures") List<CashClosure> closures(@RequestParam(required=false) UUID cashierPersonId) {
        return service.closures(cashierPersonId);
    }
    @GetMapping("/cash-closures/{id}") CashClosure closure(@PathVariable UUID id) { return service.closure(id); }
}
