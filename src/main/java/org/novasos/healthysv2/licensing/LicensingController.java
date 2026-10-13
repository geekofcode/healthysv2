package org.novasos.healthysv2.licensing;

import jakarta.validation.Valid;
import java.util.*;
import org.novasos.healthysv2.licensing.api.LicensingDtos.*;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/v1/licensing")
@PreAuthorize("isAuthenticated()")
class LicensingController {
  private final LicensingService service;
  private final StripeSubscriptionService stripe;

  LicensingController(LicensingService service, StripeSubscriptionService stripe) {
    this.service = service;
    this.stripe = stripe;
  }

  @GetMapping("/my-memberships")
  List<Map<String, Object>> myMemberships() {
    return service.myMemberships();
  }

  @GetMapping("/my-invitations")
  List<Map<String, Object>> myInvitations() {
    return service.myInvitations();
  }

  @GetMapping("/plans")
  List<Map<String, Object>> plans() {
    return service.plans();
  }

  @PostMapping("/plans")
  @PreAuthorize("hasRole('PLATFORM_ADMIN')")
  Map<String, Object> create(@Valid @RequestBody PlanRequest r) {
    return service.savePlan(null, r);
  }

  @PutMapping("/plans/{id}")
  @PreAuthorize("hasRole('PLATFORM_ADMIN')")
  Map<String, Object> update(@PathVariable UUID id, @Valid @RequestBody PlanRequest r) {
    return service.savePlan(id, r);
  }

  @GetMapping("/organizations/{org}/entitlements")
  Map<String, Object> entitlements(@PathVariable UUID org) {
    return service.entitlements(org);
  }

  @GetMapping("/organizations/{org}/team")
  List<Map<String, Object>> team(@PathVariable UUID org) {
    return service.team(org);
  }

  @PostMapping("/organizations/{org}/team")
  Map<String, Object> invite(@PathVariable UUID org, @Valid @RequestBody TeamRequest r) {
    return service.invite(org, r);
  }

  @PutMapping("/organizations/{org}/team/{id}")
  Map<String, Object> member(
      @PathVariable UUID org, @PathVariable UUID id, @Valid @RequestBody TeamUpdate r) {
    return service.update(org, id, r);
  }

  @PostMapping("/organizations/{org}/team/{id}/accept")
  Map<String, Object> accept(@PathVariable UUID org, @PathVariable UUID id) {
    return service.accept(org, id);
  }

  @GetMapping("/organizations/{org}/invoices")
  List<Map<String, Object>> invoices(@PathVariable UUID org) {
    return service.invoices(org);
  }

  @PostMapping("/organizations/{org}/checkout")
  Map<String, String> checkout(@PathVariable UUID org, @Valid @RequestBody CheckoutRequest r) {
    return stripe.checkout(org, r.planId());
  }

  @PostMapping("/organizations/{org}/portal")
  Map<String, String> portal(@PathVariable UUID org) {
    return stripe.portal(org);
  }
}
