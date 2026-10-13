package org.novasos.healthysv2.licensing;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.*;
import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.novasos.healthysv2.TestcontainersConfiguration;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationToken;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.transaction.annotation.Transactional;

@SpringBootTest(properties = "healthys.licensing.stripe.webhook-secret=test-webhook-secret")
@ActiveProfiles("test")
@Import(TestcontainersConfiguration.class)
@Transactional
class StripeSubscriptionIntegrationTests {
  @Autowired JdbcTemplate db;
  @Autowired StripeSubscriptionService service;
  @Autowired LicensingService licensing;
  @MockitoBean StripeGateway gateway;
  private final ObjectMapper mapper = new ObjectMapper();
  private UUID org, plan;
  private String price;

  @BeforeEach
  void setup() throws Exception {
    org = UUID.randomUUID();
    plan = UUID.randomUUID();
    price = "price_" + plan;
    db.update(
        "insert into organization.organization(id,organization_number,name) values(?,?,?)",
        org,
        "STRIPE-" + org,
        "Stripe test");
    db.update(
        "insert into"
            + " licensing.plan(id,name,amount_minor,currency,billing_interval,doctor_quota,professional_quota,cashier_quota,accountant_quota,ai_assistant_enabled,stripe_price_id)"
            + " values(?,'Test',10000,'cad','MONTH',3,5,1,1,true,?)",
        plan,
        price);
    var jwt =
        Jwt.withTokenValue("test")
            .header("alg", "none")
            .subject(UUID.randomUUID().toString())
            .build();
    SecurityContextHolder.getContext()
        .setAuthentication(
            new JwtAuthenticationToken(
                jwt, List.of(new SimpleGrantedAuthority("ROLE_PLATFORM_ADMIN"))));
    when(gateway.get("prices/" + price))
        .thenReturn(
            mapper.readTree(
                "{\"active\":true,\"unit_amount\":10000,\"currency\":\"cad\",\"recurring\":{\"interval\":\"month\",\"interval_count\":1}}"));
    when(gateway.post(eq("checkout/sessions"), anyMap(), anyString()))
        .thenReturn(
            mapper.readTree(
                "{\"url\":\"https://checkout.stripe.com/test\",\"expires_at\":"
                    + (Instant.now().getEpochSecond() + 3600)
                    + "}"));
  }

  @org.junit.jupiter.api.AfterEach
  void clearAuthentication() {
    SecurityContextHolder.clearContext();
  }

  private JsonNode subscription(String status, long start, long end) throws Exception {
    return mapper.readTree(
        "{\"id\":\"sub_test\",\"customer\":\"cus_test\",\"status\":\""
            + status
            + "\",\"metadata\":{\"organization_id\":\""
            + org
            + "\",\"plan_id\":\""
            + plan
            + "\"},\"current_period_start\":"
            + start
            + ",\"current_period_end\":"
            + end
            + ",\"items\":{\"data\":[{\"price\":{\"id\":\""
            + price
            + "\"}}]}}");
  }

  private void event(String id, String type) throws Exception {
    String body =
        "{\"id\":\""
            + id
            + "\",\"type\":\""
            + type
            + "\",\"data\":{\"object\":{\"id\":\"sub_test\"}}}";
    long timestamp = Instant.now().getEpochSecond();
    Mac mac = Mac.getInstance("HmacSHA256");
    mac.init(
        new SecretKeySpec("test-webhook-secret".getBytes(StandardCharsets.UTF_8), "HmacSHA256"));
    String signature =
        "t="
            + timestamp
            + ",v1="
            + HexFormat.of()
                .formatHex(mac.doFinal((timestamp + "." + body).getBytes(StandardCharsets.UTF_8)));
    service.webhook(body, signature);
  }

  @Test
  void freezesPurchasedRightsThroughActivationAndRenewal() throws Exception {
    service.checkout(org, plan);
    db.update(
        "update licensing.plan set doctor_quota=99,ai_assistant_enabled=false where id=?", plan);
    long now = Instant.now().getEpochSecond();
    when(gateway.get("subscriptions/sub_test"))
        .thenReturn(subscription("active", now - 60, now + 3600));
    event("evt_activation", "customer.subscription.created");
    assertThat(
            db.queryForObject(
                "select doctor_quota from licensing.subscription where organization_id=?",
                Integer.class,
                org))
        .isEqualTo(3);
    assertThat(
            db.queryForObject(
                "select ai_assistant_enabled from licensing.subscription where organization_id=?",
                Boolean.class,
                org))
        .isTrue();
    when(gateway.get("subscriptions/sub_test"))
        .thenReturn(subscription("active", now - 30, now + 7200));
    event("evt_renewal", "customer.subscription.updated");
    assertThat(
            db.queryForObject(
                "select doctor_quota from licensing.subscription where organization_id=?",
                Integer.class,
                org))
        .isEqualTo(3);
    licensing.requireActive(org);
  }

  @Test
  void reorderedOldEventCannotReactivateCancelledSubscription() throws Exception {
    service.checkout(org, plan);
    long now = Instant.now().getEpochSecond();
    when(gateway.get("subscriptions/sub_test"))
        .thenReturn(subscription("canceled", now - 60, now + 3600));
    event("evt_cancel", "customer.subscription.deleted");
    event("evt_old_update", "customer.subscription.updated");
    assertThat(
            db.queryForObject(
                "select status from licensing.subscription where organization_id=?",
                String.class,
                org))
        .isEqualTo("canceled");
    assertThatThrownBy(() -> licensing.requireActive(org))
        .isInstanceOf(org.springframework.security.access.AccessDeniedException.class);
  }

  @Test
  void checkoutReusesPendingSession() {
    assertThat(service.checkout(org, plan).get("url"))
        .isEqualTo(service.checkout(org, plan).get("url"));
    verify(gateway, times(1)).post(eq("checkout/sessions"), anyMap(), anyString());
  }

  @Test
  void invalidStripePriceNeverCreatesCheckout() throws Exception {
    when(gateway.get("prices/" + price))
        .thenReturn(
            mapper.readTree(
                "{\"active\":true,\"unit_amount\":1,\"currency\":\"cad\",\"recurring\":{\"interval\":\"month\",\"interval_count\":1}}"));
    assertThatThrownBy(() -> service.checkout(org, plan))
        .isInstanceOf(IllegalArgumentException.class)
        .hasMessage("STRIPE_PRICE_PLAN_MISMATCH");
    verify(gateway, never()).post(anyString(), anyMap(), anyString());
  }
}
