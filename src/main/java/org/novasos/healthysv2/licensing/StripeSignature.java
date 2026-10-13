package org.novasos.healthysv2.licensing;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.time.*;
import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;

final class StripeSignature {
  private StripeSignature() {}

  static void verify(String body, String header, String secret, Clock clock, long tolerance) {
    if (secret == null || secret.isBlank())
      throw new IllegalStateException("STRIPE_WEBHOOK_NOT_CONFIGURED");
    try {
      if (header == null) throw new IllegalArgumentException();
      Long timestamp = null;
      java.util.List<String> signatures = new java.util.ArrayList<>();
      for (String entry : header.split(",")) {
        String[] p = entry.split("=", 2);
        if (p.length == 2 && p[0].equals("t")) timestamp = Long.valueOf(p[1]);
        if (p.length == 2 && p[0].equals("v1")) signatures.add(p[1]);
      }
      if (timestamp == null || Math.abs(clock.instant().getEpochSecond() - timestamp) > tolerance)
        throw new IllegalArgumentException();
      Mac mac = Mac.getInstance("HmacSHA256");
      mac.init(new SecretKeySpec(secret.getBytes(StandardCharsets.UTF_8), "HmacSHA256"));
      byte[] expected = mac.doFinal((timestamp + "." + body).getBytes(StandardCharsets.UTF_8));
      for (String signature : signatures) {
        try {
          if (MessageDigest.isEqual(expected, java.util.HexFormat.of().parseHex(signature))) return;
        } catch (IllegalArgumentException ignored) {
        }
      }
      throw new IllegalArgumentException();
    } catch (Exception e) {
      if (e instanceof IllegalStateException state) throw state;
      throw new IllegalArgumentException("INVALID_STRIPE_SIGNATURE");
    }
  }
}
