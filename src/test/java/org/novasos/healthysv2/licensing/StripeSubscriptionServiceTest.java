package org.novasos.healthysv2.licensing;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

import com.fasterxml.jackson.databind.ObjectMapper;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.*;
import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.RowMapper;

class StripeSubscriptionServiceTest {
  private final JdbcTemplate db = mock(JdbcTemplate.class);
  private final LicensingService licensing = mock(LicensingService.class);
  private final StripeGateway gateway = mock(StripeGateway.class);
  private final ObjectMapper mapper = new ObjectMapper();
  private final UUID org = UUID.randomUUID(), planId = UUID.randomUUID();
  private final Map<String, Object> plan = new LinkedHashMap<>();
  private StripeSubscriptionService service;
  private static final String ROWS = "select * from licensing.subscription where organization_id=?";

  @SuppressWarnings("unchecked")
  @BeforeEach
  void setup() throws Exception {
    service =
        new StripeSubscriptionService(
            db, licensing, gateway, mapper, "secret", "https://example.org");
    plan.put("id", planId);
    plan.put("amount_minor", 100L);
    plan.put("currency", "cad");
    plan.put("billing_interval", "MONTH");
    plan.put("stripe_price_id", "price_test");
    plan.put("doctor_quota", 3);
    plan.put("professional_quota", 5);
    plan.put("cashier_quota", 1);
    plan.put("accountant_quota", 1);
    plan.put("ai_assistant_enabled", true);
    when(db.query(
            eq("select id from organization.organization where id=? for update"),
            any(RowMapper.class),
            eq(org)))
        .thenReturn(List.of(org));
    when(db.queryForMap("select * from licensing.plan where id=? and active=true", planId))
        .thenReturn(plan);
    when(db.queryForList("select * from licensing.plan where stripe_price_id=?", "price_test"))
        .thenReturn(List.of(plan));
    when(db.queryForList(ROWS, org)).thenReturn(List.of());
    when(db.update(
            eq("insert into licensing.stripe_event(id,type) values(?,?) on conflict do nothing"),
            anyString(),
            anyString()))
        .thenReturn(1);
    when(gateway.get("prices/price_test"))
        .thenReturn(
            mapper.readTree(
                "{\"active\":true,\"unit_amount\":100,\"currency\":\"cad\",\"recurring\":{\"interval\":\"month\",\"interval_count\":1}}"));
    when(gateway.post(eq("checkout/sessions"), anyMap(), anyString()))
        .thenReturn(
            mapper.readTree(
                "{\"url\":\"https://checkout.stripe.com/test\",\"expires_at\":"
                    + (Instant.now().getEpochSecond() + 3600)
                    + "}"));
  }

  private String subscription(String status) throws Exception {
    return "{\"status\":\""
        + status
        + "\",\"customer\":\"cus_test\",\"metadata\":{\"organization_id\":\""
        + org
        + "\"},\"current_period_start\":1000,\"current_period_end\":2000,\"items\":{\"data\":[{\"price\":{\"id\":\"price_test\"}}]}}";
  }

  private void event(String id) throws Exception {
    String body =
        "{\"id\":\""
            + id
            + "\",\"type\":\"customer.subscription.updated\",\"data\":{\"object\":{\"id\":\"sub_test\"}}}";
    long t = Instant.now().getEpochSecond();
    Mac mac = Mac.getInstance("HmacSHA256");
    mac.init(new SecretKeySpec("secret".getBytes(StandardCharsets.UTF_8), "HmacSHA256"));
    service.webhook(
        body,
        "t="
            + t
            + ",v1="
            + HexFormat.of()
                .formatHex(mac.doFinal((t + "." + body).getBytes(StandardCharsets.UTF_8))));
  }

  private List<Object[]> persisted() {
    return mockingDetails(db).getInvocations().stream()
        .filter(
            i ->
                i.getMethod().getName().equals("update")
                    && i.getArgument(0, String.class)
                        .startsWith("insert into licensing.subscription(")
                    && i.getArgument(0, String.class).contains("period_start"))
        .map(i -> (Object[]) i.getRawArguments()[1])
        .toList();
  }

  @Test
  void purchasedSnapshotSurvivesCatalogChangeAndRenewal() throws Exception {
    String frozenJson = mapper.writeValueAsString(plan);
    Map<String, Object> pending =
        Map.of("pending_checkout_plan_id", planId, "pending_plan_snapshot", frozenJson);
    Map<String, Object> frozen = new LinkedHashMap<>(plan);
    frozen.put("stripe_subscription_id", "sub_test");
    frozen.put("plan_id", planId);
    frozen.put("status", "active");
    plan.put("doctor_quota", 99);
    plan.put("ai_assistant_enabled", false);
    when(db.queryForList(ROWS, org)).thenReturn(List.of(pending), List.of(frozen));
    when(gateway.get("subscriptions/sub_test")).thenReturn(mapper.readTree(subscription("active")));
    event("evt_first");
    event("evt_renewal");
    assertThat(persisted()).hasSize(2);
    for (Object[] args : persisted()) {
      assertThat(((Number) args[8]).intValue()).isEqualTo(3);
      assertThat(args[12]).isEqualTo(true);
    }
  }

  @Test
  void oldEventUsesCurrentCancelledStripeState() throws Exception {
    when(gateway.get("subscriptions/sub_test"))
        .thenReturn(mapper.readTree(subscription("canceled")));
    event("evt_cancel");
    event("evt_old_update");
    assertThat(persisted()).hasSize(2);
    assertThat(persisted()).allSatisfy(args -> assertThat(args[2]).isEqualTo("canceled"));
  }

  @Test
  void checkoutReusesPendingSession() {
    String pendingSQL =
        "select pending_checkout_url,pending_checkout_plan_id from licensing.subscription where"
            + " organization_id=? and pending_checkout_expires_at>now()";
    when(db.queryForList(pendingSQL, org))
        .thenReturn(
            List.of(),
            List.of(
                Map.of(
                    "pending_checkout_url",
                    "https://checkout.stripe.com/test",
                    "pending_checkout_plan_id",
                    planId)));
    assertThat(service.checkout(org, planId)).isEqualTo(service.checkout(org, planId));
    verify(gateway, times(1)).post(eq("checkout/sessions"), anyMap(), anyString());
  }

  @Test
  void invalidPriceFailsBeforeCheckout() throws Exception {
    when(gateway.get("prices/price_test"))
        .thenReturn(mapper.readTree("{\"active\":true,\"unit_amount\":1}"));
    assertThatThrownBy(() -> service.checkout(org, planId))
        .isInstanceOf(IllegalArgumentException.class)
        .hasMessage("STRIPE_PRICE_PLAN_MISMATCH");
    verify(gateway, never()).post(anyString(), anyMap(), anyString());
  }
}
