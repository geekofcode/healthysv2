package org.novasos.healthysv2.licensing;

import com.fasterxml.jackson.databind.*;
import java.net.*;
import java.net.http.*;
import java.nio.charset.StandardCharsets;
import java.util.*;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

@Component
class StripeGateway {
  private final String secret;
  private final ObjectMapper mapper;
  private final HttpClient http =
      HttpClient.newBuilder().connectTimeout(java.time.Duration.ofSeconds(10)).build();

  StripeGateway(
      @Value("${healthys.licensing.stripe.secret-key:}") String secret, ObjectMapper mapper) {
    this.secret = secret;
    this.mapper = mapper;
  }

  JsonNode get(String path) {
    return call(path, null, null);
  }

  JsonNode post(String path, Map<String, String> data, String key) {
    return call(path, data, key);
  }

  private JsonNode call(String path, Map<String, String> data, String key) {
    if (secret.isBlank()) throw new IllegalStateException("STRIPE_NOT_CONFIGURED");
    try {
      var b =
          HttpRequest.newBuilder(URI.create("https://api.stripe.com/v1/" + path))
              .timeout(java.time.Duration.ofSeconds(20))
              .header("Authorization", "Bearer " + secret)
              .header("Stripe-Version", "2024-06-20");
      if (data == null) b.GET();
      else {
        String body =
            data.entrySet().stream()
                .map(e -> encode(e.getKey()) + "=" + encode(e.getValue()))
                .collect(java.util.stream.Collectors.joining("&"));
        b.header("Content-Type", "application/x-www-form-urlencoded")
            .POST(HttpRequest.BodyPublishers.ofString(body));
        if (key != null) b.header("Idempotency-Key", key);
      }
      var response = http.send(b.build(), HttpResponse.BodyHandlers.ofString());
      if (response.statusCode() < 200 || response.statusCode() >= 300)
        throw new IllegalStateException("STRIPE_REQUEST_FAILED");
      return mapper.readTree(response.body());
    } catch (InterruptedException e) {
      Thread.currentThread().interrupt();
      throw new IllegalStateException("STRIPE_INTERRUPTED", e);
    } catch (java.io.IOException e) {
      throw new IllegalStateException("STRIPE_UNAVAILABLE", e);
    }
  }

  private String encode(String s) {
    return URLEncoder.encode(s, StandardCharsets.UTF_8);
  }
}
