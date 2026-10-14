package org.novasos.healthysv2.licensing;

import com.fasterxml.jackson.databind.*;
import java.time.*;
import java.util.*;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
class StripeSubscriptionService {
  private final JdbcTemplate db;
  private final LicensingService licensing;
  private final StripeGateway stripe;
  private final ObjectMapper mapper;
  private final String webhookSecret, returnUrl;

  StripeSubscriptionService(
      JdbcTemplate db,
      LicensingService licensing,
      StripeGateway stripe,
      ObjectMapper mapper,
      @Value("${healthys.licensing.stripe.webhook-secret:}") String webhookSecret,
      @Value("${healthys.licensing.stripe.return-url:http://localhost:5173/licensing}")
          String returnUrl) {
    this.db = db;
    this.licensing = licensing;
    this.stripe = stripe;
    this.mapper = mapper;
    this.webhookSecret = webhookSecret;
    this.returnUrl = returnUrl;
  }

  @Transactional
  Map<String, String> checkout(UUID org, UUID plan) {
    licensing.requireSubscriptionAdministrator(org);
    lock(org);
    var p = db.queryForMap("select * from licensing.plan where id=? and active=true", plan);
    validatePrice(p);
    var existing =
        db.queryForList("select * from licensing.subscription where organization_id=?", org);
    if (!existing.isEmpty()
        && Set.of("active", "trialing", "past_due", "unpaid", "incomplete")
            .contains(existing.getFirst().get("status")))
      throw new IllegalArgumentException("USE_SUBSCRIPTION_PORTAL");
    var pending =
        db.queryForList(
            "select pending_checkout_url,pending_checkout_plan_id from licensing.subscription where"
                + " organization_id=? and pending_checkout_expires_at>now()",
            org);
    if (!pending.isEmpty()) {
      if (!plan.equals(pending.getFirst().get("pending_checkout_plan_id")))
        throw new IllegalArgumentException("CHECKOUT_ALREADY_PENDING");
      return Map.of("url", pending.getFirst().get("pending_checkout_url").toString());
    }
    Map<String, String> fields = checkoutFields(org, plan, p, existing);
    var session =
        stripe.post(
            "checkout/sessions",
            fields,
            "healthys-checkout-" + org + "-" + plan + "-" + UUID.randomUUID());
    String url = session.path("url").asText();
    db.update(
        "insert into"
            + " licensing.subscription(organization_id,pending_checkout_url,pending_checkout_plan_id,pending_checkout_expires_at,pending_plan_snapshot)"
            + " values(?,?,?,?,?::jsonb) on conflict(organization_id) do update set"
            + " pending_checkout_url=excluded.pending_checkout_url,pending_checkout_plan_id=excluded.pending_checkout_plan_id,pending_checkout_expires_at=excluded.pending_checkout_expires_at,pending_plan_snapshot=excluded.pending_plan_snapshot",
        org,
        url,
        plan,
        time(session.path("expires_at").asLong()),
        planJson(p));
    return Map.of("url", url);
  }

  private Map<String, String> checkoutFields(
      UUID org, UUID plan, Map<String, Object> p, List<Map<String, Object>> existing) {
    Map<String, String> fields = new LinkedHashMap<>();
    fields.put("mode", "subscription");
    fields.put("line_items[0][price]", p.get("stripe_price_id").toString());
    fields.put("line_items[0][quantity]", "1");
    fields.put("success_url", returnUrl + "?checkout=success");
    fields.put("cancel_url", returnUrl + "?checkout=cancelled");
    fields.put("client_reference_id", org.toString());
    fields.put("subscription_data[metadata][organization_id]", org.toString());
    fields.put("subscription_data[metadata][plan_id]", plan.toString());
    if (!existing.isEmpty() && existing.getFirst().get("stripe_customer_id") != null)
      fields.put("customer", existing.getFirst().get("stripe_customer_id").toString());
    return fields;
  }

  private void validatePrice(Map<String, Object> p) {
    JsonNode price = stripe.get("prices/" + p.get("stripe_price_id"));
    if (!price.path("active").asBoolean()
        || price.path("unit_amount").asLong(-1) != ((Number) p.get("amount_minor")).longValue()
        || !price.path("currency").asText().equals(p.get("currency"))
        || !price
            .path("recurring")
            .path("interval")
            .asText()
            .equals(p.get("billing_interval").toString().toLowerCase(Locale.ROOT))
        || price.path("recurring").path("interval_count").asInt() != 1)
      throw new IllegalArgumentException("STRIPE_PRICE_PLAN_MISMATCH");
  }

  Map<String, String> portal(UUID org) {
    licensing.requireSubscriptionAdministrator(org);
    String customer =
        db.queryForObject(
            "select stripe_customer_id from licensing.subscription where organization_id=?",
            String.class,
            org);
    if (customer == null) throw new IllegalArgumentException("STRIPE_CUSTOMER_NOT_FOUND");
    return Map.of(
        "url",
        stripe
            .post(
                "billing_portal/sessions",
                Map.of("customer", customer, "return_url", returnUrl),
                null)
            .path("url")
            .asText());
  }

  @Transactional
  void webhook(String body, String signature) {
    StripeSignature.verify(body, signature, webhookSecret, Clock.systemUTC(), 300);
    try {
      JsonNode event = mapper.readTree(body);
      String id = event.path("id").asText(), type = event.path("type").asText();
      if (id.isBlank()) throw new IllegalArgumentException("INVALID_EVENT");
      if (db.update(
              "insert into licensing.stripe_event(id,type) values(?,?) on conflict do nothing",
              id,
              type)
          == 0) return;
      JsonNode object = event.path("data").path("object");
      if (type.startsWith("customer.subscription.")) reconcile(object.path("id").asText());
      else if (type.equals("checkout.session.completed")
          && object.path("mode").asText().equals("subscription"))
        reconcile(object.path("subscription").asText());
      else if (type.startsWith("invoice.")) {
        String sub = object.path("subscription").asText();
        if (!sub.isBlank()) {
          reconcile(sub);
          recordInvoice(object, sub);
        }
      }
    } catch (java.io.IOException e) {
      throw new IllegalArgumentException("INVALID_EVENT_JSON", e);
    }
  }

  private void lock(UUID org) {
    if (db.query(
            "select id from organization.organization where id=? for update",
            (r, i) -> r.getObject(1),
            org)
        .isEmpty()) throw new IllegalArgumentException("ORGANIZATION_NOT_FOUND");
  }

  private void reconcile(String id) {
    if (!id.startsWith("sub_")) throw new IllegalArgumentException("INVALID_SUBSCRIPTION");
    JsonNode probe = stripe.get("subscriptions/" + id);
    UUID org = UUID.fromString(probe.path("metadata").path("organization_id").asText());
    lock(
        org); // Fetch again under the organization lock: reordered/concurrent events cannot restore
    // stale rights.
    JsonNode sub = stripe.get("subscriptions/" + id);
    String price = sub.path("items").path("data").path(0).path("price").path("id").asText();
    var plans = db.queryForList("select * from licensing.plan where stripe_price_id=?", price);
    if (plans.isEmpty()) throw new IllegalArgumentException("UNKNOWN_STRIPE_PRICE");
    var p = plans.getFirst();
    var rows = db.queryForList("select * from licensing.subscription where organization_id=?", org);
    if (!rows.isEmpty()
        && rows.getFirst().get("stripe_subscription_id") != null
        && !rows.getFirst().get("stripe_subscription_id").equals(id)
        && Set.of("active", "trialing", "past_due", "unpaid", "incomplete")
            .contains(rows.getFirst().get("status")))
      throw new IllegalArgumentException("SUBSCRIPTION_CONFLICT");
    if (!org.toString().equals(sub.path("metadata").path("organization_id").asText()))
      throw new IllegalArgumentException("STRIPE_ORGANIZATION_MISMATCH");
    Map<String, Object> snapshot = subscriptionSnapshot(id, p, rows);
    persistSubscription(org, id, sub, p, snapshot);
  }

  private Map<String, Object> subscriptionSnapshot(
      String id, Map<String, Object> p, List<Map<String, Object>> rows) {
    boolean same =
        !rows.isEmpty()
            && id.equals(rows.getFirst().get("stripe_subscription_id"))
            && p.get("id").equals(rows.getFirst().get("plan_id"));
    Map<String, Object> snapshot = same ? rows.getFirst() : p;
    if (!same
        && !rows.isEmpty()
        && p.get("id").equals(rows.getFirst().get("pending_checkout_plan_id"))
        && rows.getFirst().get("pending_plan_snapshot") != null)
      snapshot = readSnapshot(rows.getFirst().get("pending_plan_snapshot").toString());
    return snapshot;
  }

  private void persistSubscription(
      UUID org, String id, JsonNode sub, Map<String, Object> p, Map<String, Object> snapshot) {
    db.update(
        "insert into"
            + " licensing.subscription(organization_id,plan_id,status,stripe_customer_id,stripe_subscription_id,period_start,period_end,cancel_at_period_end,doctor_quota,professional_quota,cashier_quota,accountant_quota,ai_assistant_enabled,amount_minor,currency,billing_interval)"
            + " values(?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?) on conflict(organization_id) do update set"
            + " plan_id=excluded.plan_id,status=excluded.status,stripe_customer_id=excluded.stripe_customer_id,stripe_subscription_id=excluded.stripe_subscription_id,period_start=excluded.period_start,period_end=excluded.period_end,cancel_at_period_end=excluded.cancel_at_period_end,doctor_quota=excluded.doctor_quota,professional_quota=excluded.professional_quota,cashier_quota=excluded.cashier_quota,accountant_quota=excluded.accountant_quota,ai_assistant_enabled=excluded.ai_assistant_enabled,amount_minor=excluded.amount_minor,currency=excluded.currency,billing_interval=excluded.billing_interval,updated_at=now()",
        org,
        p.get("id"),
        sub.path("status").asText(),
        sub.path("customer").asText(),
        id,
        time(sub.path("current_period_start").asLong()),
        time(sub.path("current_period_end").asLong()),
        sub.path("cancel_at_period_end").asBoolean(),
        snapshot.get("doctor_quota"),
        snapshot.get("professional_quota"),
        snapshot.get("cashier_quota"),
        snapshot.get("accountant_quota"),
        snapshot.get("ai_assistant_enabled"),
        snapshot.get("amount_minor"),
        snapshot.get("currency"),
        snapshot.get("billing_interval"));
    db.update(
        "update licensing.subscription set"
            + " pending_checkout_url=null,pending_checkout_plan_id=null,pending_checkout_expires_at=null,pending_plan_snapshot=null"
            + " where organization_id=?",
        org);
    db.update(
        "insert into licensing.audit_entry(organization_id,action,target_id)"
            + " values(?,'SUBSCRIPTION_RECONCILED',?)",
        org,
        p.get("id"));
  }

  private String planJson(Map<String, Object> plan) {
    Map<String, Object> snapshot = new LinkedHashMap<>();
    for (String key :
        List.of(
            "doctor_quota",
            "professional_quota",
            "cashier_quota",
            "accountant_quota",
            "ai_assistant_enabled",
            "amount_minor",
            "currency",
            "billing_interval")) snapshot.put(key, plan.get(key));
    try {
      return mapper.writeValueAsString(snapshot);
    } catch (java.io.IOException e) {
      throw new IllegalStateException("SNAPSHOT_SERIALIZATION_FAILED", e);
    }
  }

  private Map<String, Object> readSnapshot(String json) {
    try {
      return mapper.readValue(
          json, new com.fasterxml.jackson.core.type.TypeReference<Map<String, Object>>() {});
    } catch (java.io.IOException e) {
      throw new IllegalStateException("SNAPSHOT_INVALID", e);
    }
  }

  private java.sql.Timestamp time(long seconds) {
    return java.sql.Timestamp.from(Instant.ofEpochSecond(seconds));
  }

  private void recordInvoice(JsonNode invoice, String subscription) {
    UUID org =
        db.queryForObject(
            "select organization_id from licensing.subscription where stripe_subscription_id=?",
            UUID.class,
            subscription); // Reconcile invoice too to avoid applying an older invoice event.
    JsonNode current = stripe.get("invoices/" + invoice.path("id").asText());
    if (!subscription.equals(current.path("subscription").asText()))
      throw new IllegalArgumentException("INVOICE_SUBSCRIPTION_MISMATCH");
    db.update(
        "insert into"
            + " licensing.subscription_invoice(id,organization_id,status,amount_paid,currency,hosted_invoice_url,invoice_pdf,created_at)"
            + " values(?,?,?,?,?,?,?,?) on conflict(id) do update set"
            + " status=excluded.status,amount_paid=excluded.amount_paid,hosted_invoice_url=excluded.hosted_invoice_url,invoice_pdf=excluded.invoice_pdf",
        current.path("id").asText(),
        org,
        current.path("status").asText(),
        current.path("amount_paid").asLong(),
        current.path("currency").asText(),
        current.path("hosted_invoice_url").asText(null),
        current.path("invoice_pdf").asText(null),
        time(current.path("created").asLong()));
  }
}
