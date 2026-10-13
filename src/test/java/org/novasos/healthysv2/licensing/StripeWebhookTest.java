package org.novasos.healthysv2.licensing;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

import com.fasterxml.jackson.databind.ObjectMapper;
import java.nio.charset.StandardCharsets;
import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import org.junit.jupiter.api.Test;
import org.springframework.jdbc.core.JdbcTemplate;

class StripeWebhookTest {
  private String sign(String body) throws Exception {
    long t = java.time.Instant.now().getEpochSecond();
    Mac mac = Mac.getInstance("HmacSHA256");
    mac.init(new SecretKeySpec("secret".getBytes(StandardCharsets.UTF_8), "HmacSHA256"));
    return "t="
        + t
        + ",v1="
        + java.util.HexFormat.of()
            .formatHex(mac.doFinal((t + "." + body).getBytes(StandardCharsets.UTF_8)));
  }

  @Test
  void duplicateSignedEventDoesNotCallStripe() throws Exception {
    JdbcTemplate db = mock(JdbcTemplate.class);
    StripeGateway gateway = mock(StripeGateway.class);
    var service =
        new StripeSubscriptionService(
            db,
            mock(LicensingService.class),
            gateway,
            new ObjectMapper(),
            "secret",
            "https://example.org");
    String body =
        "{\"id\":\"evt_test\",\"type\":\"customer.subscription.updated\",\"data\":{\"object\":{\"id\":\"sub_test\"}}}";
    service.webhook(body, sign(body));
    verify(db)
        .update(
            "insert into licensing.stripe_event(id,type) values(?,?) on conflict do nothing",
            "evt_test",
            "customer.subscription.updated");
    verifyNoInteractions(gateway);
  }

  @Test
  void forgedEventRejectedBeforeDatabaseMutation() {
    JdbcTemplate db = mock(JdbcTemplate.class);
    StripeGateway gateway = mock(StripeGateway.class);
    var service =
        new StripeSubscriptionService(
            db,
            mock(LicensingService.class),
            gateway,
            new ObjectMapper(),
            "secret",
            "https://example.org");
    assertThrows(IllegalArgumentException.class, () -> service.webhook("{}", "t=1,v1=abcd"));
    verifyNoInteractions(db, gateway);
  }
}
