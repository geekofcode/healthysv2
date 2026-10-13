package org.novasos.healthysv2.care;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

import com.fasterxml.jackson.databind.ObjectMapper;
import java.nio.ByteBuffer;
import java.time.Duration;
import java.util.*;
import java.util.concurrent.*;
import org.junit.jupiter.api.Test;
import org.springframework.jdbc.core.JdbcTemplate;

class AiContractTests {
  private final Map<String, Object> snapshot =
      Map.of("sources", List.of(Map.of("id", "intake", "label", "Constantes")));

  @Test
  void fabricatedSourceCannotBePresentedAsEvidence() {
    assertThrows(
        AiPythonClient.Failure.class,
        () ->
            AiPythonClient.validate(
                AiDtos.Kind.SUMMARY,
                Map.of(
                    "summary",
                    "Aucun problème",
                    "sources",
                    List.of(Map.of("id", "invented", "label", "Constantes")),
                    "missingData",
                    List.of()),
                snapshot));
  }

  @Test
  void modifiedSourceLabelCannotMisrepresentEvidence() {
    assertThrows(
        AiPythonClient.Failure.class,
        () ->
            AiPythonClient.validate(
                AiDtos.Kind.SUMMARY,
                Map.of(
                    "summary",
                    "Texte",
                    "sources",
                    List.of(Map.of("id", "intake", "label", "Examen validé")),
                    "missingData",
                    List.of()),
                snapshot));
  }

  @Test
  void reportCannotContainHiddenFinalizationInstructions() {
    Map<String, Object> result =
        new HashMap<>(
            Map.of(
                "reason",
                "Motif",
                "observations",
                "Examen",
                "diagnosis",
                "",
                "treatment",
                "",
                "followUp",
                ""));
    result.put("finalize", true);
    assertThrows(
        AiPythonClient.Failure.class,
        () -> AiPythonClient.validate(AiDtos.Kind.REPORT, result, snapshot));
  }

  @Test
  void incompleteStructuredReportIsRejected() {
    assertThrows(
        AiPythonClient.Failure.class,
        () -> AiPythonClient.validate(AiDtos.Kind.REPORT, Map.of("reason", "Motif"), snapshot));
  }

  @Test
  void transcriptionIsOnlyText() {
    AiPythonClient.validate(
        AiDtos.Kind.TRANSCRIPTION,
        Map.of("text", "Texte dicté corrigible"),
        Map.of("sources", List.of()));
    assertThrows(
        AiPythonClient.Failure.class,
        () ->
            AiPythonClient.validate(
                AiDtos.Kind.TRANSCRIPTION, Map.of("text", List.of("nottext")), snapshot));
  }

  @Test
  void oversizedResponseCancelsNetworkSubscription() {
    var body = new AiPythonClient.LimitedBody();
    var subscription = mock(Flow.Subscription.class);
    body.onSubscribe(subscription);
    body.onNext(List.of(ByteBuffer.wrap(new byte[128001])));
    verify(subscription).cancel();
    assertTrue(body.getBody().toCompletableFuture().isCompletedExceptionally());
  }

  @Test
  void responseBufferAcceptsChunksWithinLimit() {
    var body = new AiPythonClient.LimitedBody();
    var subscription = mock(Flow.Subscription.class);
    body.onSubscribe(subscription);
    body.onNext(List.of(ByteBuffer.wrap("hé".getBytes(java.nio.charset.StandardCharsets.UTF_8))));
    body.onComplete();
    assertEquals(
        "hé",
        new String(
            body.getBody().toCompletableFuture().join(), java.nio.charset.StandardCharsets.UTF_8));
  }

  @Test
  void snapshotHashSurvivesJsonMapOrderingAndRestart() {
    var codec = new AiSnapshot(mock(JdbcTemplate.class), new ObjectMapper());
    Map<String, Object> a = new LinkedHashMap<>();
    a.put("z", List.of(Map.of("b", 2, "a", 1)));
    a.put("a", "value");
    Map<String, Object> b = new LinkedHashMap<>();
    b.put("a", "value");
    b.put("z", List.of(Map.of("a", 1, "b", 2)));
    assertEquals(codec.hash(a), codec.hash(b));
    assertEquals(codec.hash(a), codec.hash(codec.decode(codec.encode(a))));
  }

  @Test
  void policyRejectsWeakSecretAndUnboundedDeadline() {
    assertThrows(
        IllegalArgumentException.class,
        () ->
            new AiPolicy(
                true,
                "http://ai",
                "weak",
                Duration.ofSeconds(60),
                Duration.ofMinutes(15),
                Duration.ofHours(1),
                3,
                1000));
    assertThrows(
        IllegalArgumentException.class,
        () ->
            new AiPolicy(
                false,
                "http://ai",
                "",
                Duration.ofSeconds(60),
                Duration.ofSeconds(10),
                Duration.ofHours(1),
                3,
                1000));
  }

  @Test
  void browserCodecMimeParametersAreAcceptedButNotExecutableTypes() {
    assertEquals("audio/webm", AiJobService.normalizeMime("audio/webm;codecs=opus"));
    assertThrows(
        IllegalArgumentException.class, () -> AiJobService.normalizeMime("application/javascript"));
  }
}
