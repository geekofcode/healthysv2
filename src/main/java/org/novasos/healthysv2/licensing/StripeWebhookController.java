package org.novasos.healthysv2.licensing;

import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

@RestController
class StripeWebhookController {
  private final StripeSubscriptionService service;

  StripeWebhookController(StripeSubscriptionService service) {
    this.service = service;
  }

  @PostMapping("/api/v1/licensing/stripe/webhook")
  ResponseEntity<Void> webhook(
      @RequestBody String body,
      @RequestHeader(value = "Stripe-Signature", required = false) String signature) {
    service.webhook(body, signature);
    return ResponseEntity.ok().build();
  }
}
