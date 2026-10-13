package org.novasos.healthysv2.care;

import java.net.*;
import java.net.http.*;
import java.nio.charset.StandardCharsets;
import java.util.*;
import org.springframework.stereotype.Component;

@Component
class AiPythonClient {
  private final AiPolicy policy;
  private final AiSnapshot json;
  private final HttpClient client;

  AiPythonClient(AiPolicy policy, AiSnapshot json) {
    this.policy = policy;
    this.json = json;
    this.client =
        HttpClient.newBuilder()
            .connectTimeout(policy.timeout())
            .followRedirects(HttpClient.Redirect.NEVER)
            .build();
  }

  Result generate(
      UUID id,
      AiDtos.Kind kind,
      Map<String, Object> snapshot,
      String notes,
      byte[] audio,
      String mime) {
    Map<String, Object> body = new LinkedHashMap<>();
    body.put("jobId", id);
    body.put("kind", kind.name());
    body.put("snapshot", snapshot);
    if (notes != null && !notes.isBlank()) body.put("notes", notes);
    if (audio != null) {
      body.put("audioBase64", Base64.getEncoder().encodeToString(audio));
      body.put("audioMimeType", mime);
    }
    var request =
        HttpRequest.newBuilder(URI.create(policy.serviceUrl() + "/v1/generate"))
            .timeout(policy.timeout())
            .header("Authorization", "Bearer " + policy.serviceToken())
            .header("Content-Type", "application/json")
            .POST(HttpRequest.BodyPublishers.ofString(json.encode(body)))
            .build();
    try {
      var future = client.sendAsync(request, info -> new LimitedBody());
      try {
        var response =
            future.get(policy.timeout().toMillis(), java.util.concurrent.TimeUnit.MILLISECONDS);
        if (response.statusCode() != 200)
          throw new Failure(
              response.statusCode() == 429 || response.statusCode() >= 500,
              "AI_PROVIDER_UNAVAILABLE");
        var value = json.decode(new String(response.body(), StandardCharsets.UTF_8));
        if (!(value.get("model") instanceof String model)
            || model.isBlank()
            || model.length() > 100
            || !(value.get("output") instanceof Map<?, ?>))
          throw new Failure(false, "AI_INVALID_OUTPUT");
        @SuppressWarnings("unchecked")
        var output = (Map<String, Object>) value.get("output");
        validate(kind, output, snapshot);
        return new Result(model, output);
      } finally {
        if (!future.isDone()) future.cancel(true);
      }
    } catch (Failure failure) {
      throw failure;
    } catch (InterruptedException e) {
      Thread.currentThread().interrupt();
      throw new Failure(true, "AI_PROVIDER_UNAVAILABLE");
    } catch (java.util.concurrent.TimeoutException e) {
      throw new Failure(true, "AI_PROVIDER_UNAVAILABLE");
    } catch (java.util.concurrent.ExecutionException e) {
      if (e.getCause() instanceof Failure f) throw f;
      throw new Failure(true, "AI_PROVIDER_UNAVAILABLE");
    } catch (RuntimeException e) {
      throw new Failure(false, "AI_INVALID_OUTPUT");
    }
  }

  static void validate(AiDtos.Kind kind, Map<String, Object> output, Map<String, Object> snapshot) {
    switch (kind) {
      case TRANSCRIPTION -> {
        exact(output, Set.of("text"));
        text(output, "text");
      }
      case REPORT -> {
        exact(output, Set.of("reason", "observations", "diagnosis", "treatment", "followUp"));
        for (String key : output.keySet())
          text(
              output,
              key,
              key.equals("reason") ? 10000 : key.equals("observations") ? 50000 : 20000);
      }
      case SUMMARY -> {
        exact(output, Set.of("summary", "sources", "missingData"));
        text(output, "summary");
        if (!(output.get("sources") instanceof List<?> refs)
            || refs.size() > 100
            || !(output.get("missingData") instanceof List<?> missing)
            || missing.size() > 100) throw new Failure(false, "AI_INVALID_OUTPUT");
        Map<String, String> known = new HashMap<>();
        if (snapshot.get("sources") instanceof List<?> list)
          for (Object source : list)
            if (source instanceof Map<?, ?> m)
              known.put(String.valueOf(m.get("id")), String.valueOf(m.get("label")));
        for (Object ref : refs) {
          if (!(ref instanceof Map<?, ?> m)
              || !m.keySet().equals(Set.of("id", "label"))
              || !(m.get("id") instanceof String id)
              || !Objects.equals(known.get(id), m.get("label")))
            throw new Failure(false, "AI_INVALID_OUTPUT");
        }
        for (Object item : missing)
          if (!(item instanceof String s) || s.length() > 1000)
            throw new Failure(false, "AI_INVALID_OUTPUT");
      }
    }
  }

  private static void exact(Map<String, Object> output, Set<String> keys) {
    if (!output.keySet().equals(keys)) throw new Failure(false, "AI_INVALID_OUTPUT");
  }

  private static void text(Map<String, Object> output, String key) {
    text(output, key, 30000);
  }

  private static void text(Map<String, Object> output, String key, int max) {
    if (!(output.get(key) instanceof String value) || value.length() > max)
      throw new Failure(false, "AI_INVALID_OUTPUT");
  }

  static final class LimitedBody implements HttpResponse.BodySubscriber<byte[]> {
    private final java.util.concurrent.CompletableFuture<byte[]> result =
        new java.util.concurrent.CompletableFuture<>();
    private final java.io.ByteArrayOutputStream bytes = new java.io.ByteArrayOutputStream();
    private java.util.concurrent.Flow.Subscription subscription;

    @Override
    public java.util.concurrent.CompletionStage<byte[]> getBody() {
      return result;
    }

    @Override
    public void onSubscribe(java.util.concurrent.Flow.Subscription value) {
      subscription = value;
      value.request(1);
    }

    @Override
    public void onNext(List<java.nio.ByteBuffer> buffers) {
      for (var buffer : buffers) {
        if (buffer.remaining() > 128000 - bytes.size()) {
          subscription.cancel();
          result.completeExceptionally(new Failure(false, "AI_INVALID_OUTPUT"));
          return;
        }
        byte[] chunk = new byte[buffer.remaining()];
        buffer.get(chunk);
        bytes.writeBytes(chunk);
      }
      subscription.request(1);
    }

    @Override
    public void onError(Throwable error) {
      result.completeExceptionally(error);
    }

    @Override
    public void onComplete() {
      result.complete(bytes.toByteArray());
    }
  }

  record Result(String model, Map<String, Object> output) {}

  static class Failure extends RuntimeException {
    final boolean retryable;
    final String code;

    Failure(boolean retryable, String code) {
      super(code);
      this.retryable = retryable;
      this.code = code;
    }
  }
}
