package org.novasos.healthysv2.identity;

import java.util.UUID;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/v1/platform/accounts/{personId}/reset-access")
@PreAuthorize("hasRole('PLATFORM_ADMIN')")
class PlatformAccountResetController {
  private final AccountResetService service;

  PlatformAccountResetController(AccountResetService service) {
    this.service = service;
  }

  @PostMapping
  AccountResetService.Request reset(
      @PathVariable UUID personId, @RequestHeader("Idempotency-Key") String key) {
    return service.enqueue(personId, key);
  }

  @GetMapping("/requests/{requestId}")
  AccountResetService.Request state(@PathVariable UUID personId, @PathVariable UUID requestId) {
    return service.state(personId, requestId);
  }
}
