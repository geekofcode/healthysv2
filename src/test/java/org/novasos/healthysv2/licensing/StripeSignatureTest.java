package org.novasos.healthysv2.licensing;

import static org.junit.jupiter.api.Assertions.*;

import java.nio.charset.StandardCharsets;
import java.time.*;
import javax.crypto.*;
import javax.crypto.spec.*;
import org.junit.jupiter.api.Test;

class StripeSignatureTest {
  private final Clock clock = Clock.fixed(Instant.ofEpochSecond(1000), ZoneOffset.UTC);

  private String signature(long timestamp, String body) throws Exception {
    Mac mac = Mac.getInstance("HmacSHA256");
    mac.init(new SecretKeySpec("secret".getBytes(StandardCharsets.UTF_8), "HmacSHA256"));
    return "t="
        + timestamp
        + ",v1="
        + java.util.HexFormat.of()
            .formatHex(mac.doFinal((timestamp + "." + body).getBytes(StandardCharsets.UTF_8)));
  }

  @Test
  void authenticRawBodyAccepted() throws Exception {
    assertDoesNotThrow(
        () -> StripeSignature.verify("{}", signature(1000, "{}"), "secret", clock, 300));
  }

  @Test
  void tamperedBodyRejected() throws Exception {
    assertThrows(
        IllegalArgumentException.class,
        () -> StripeSignature.verify("{ }", signature(1000, "{}"), "secret", clock, 300));
  }

  @Test
  void staleReplayRejected() throws Exception {
    assertThrows(
        IllegalArgumentException.class,
        () -> StripeSignature.verify("{}", signature(600, "{}"), "secret", clock, 300));
  }

  @Test
  void missingConfigurationFailsClosed() {
    assertThrows(
        IllegalStateException.class, () -> StripeSignature.verify("{}", null, "", clock, 300));
  }

  @Test
  void secretRotationAcceptsMatchingSignature() throws Exception {
    assertDoesNotThrow(
        () ->
            StripeSignature.verify(
                "{}", signature(1000, "{}").replace(",v1=", ",v1=abcd,v1="), "secret", clock, 300));
  }

  @Test
  void categoriesMatchCommercialQuotas() {
    assertEquals("doctor", LicensingService.bucket("DOCTOR"));
    assertEquals("professional", LicensingService.bucket("NURSE"));
    assertEquals("professional", LicensingService.bucket("HOSPITAL_AGENT"));
    assertEquals("cashier", LicensingService.bucket("CASHIER"));
    assertThrows(IllegalArgumentException.class, () -> LicensingService.bucket("PLATFORM_ADMIN"));
  }
}
