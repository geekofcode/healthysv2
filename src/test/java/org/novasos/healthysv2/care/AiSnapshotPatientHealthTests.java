package org.novasos.healthysv2.care;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

import com.fasterxml.jackson.databind.ObjectMapper;
import java.util.*;
import org.junit.jupiter.api.Test;
import org.springframework.jdbc.core.JdbcTemplate;

class AiSnapshotPatientHealthTests {
  @Test
  void preservesDatedSourceAndUnvalidatedPatientProvenanceAndDigestDetectsNewHistory() {
    var db = mock(JdbcTemplate.class);
    var snapshot = new AiSnapshot(db, new ObjectMapper());
    UUID patient = UUID.randomUUID(),
        observation = UUID.randomUUID(),
        predecessor = UUID.randomUUID();
    var c =
        new AiAuthorization.Context(
            UUID.randomUUID(), UUID.randomUUID(), UUID.randomUUID(), patient, UUID.randomUUID());
    when(db.queryForList(contains("from patienthealth.observation o"), eq(patient)))
        .thenReturn(
            List.of(
                Map.of(
                    "id",
                    observation,
                    "self_reported",
                    true,
                    "measured_at",
                    "2026-10-15T10:00:00Z",
                    "supersedes_id",
                    predecessor,
                    "provenance",
                    "PATIENT_DECLARED_NOT_VALIDATED")));
    when(db.queryForList(contains("from patienthealth.declaration d"), eq(patient)))
        .thenReturn(
            List.of(
                Map.of(
                    "id",
                    UUID.randomUUID(),
                    "recorded_at",
                    "2026-10-15T11:00:00Z",
                    "kind",
                    "ALLERGY",
                    "provenance",
                    "PATIENT_DECLARED_NOT_VALIDATED")));
    when(db.queryForObject(
            contains("from patienthealth.observation t"), eq(String.class), eq(patient)))
        .thenReturn("history-before", "history-after");
    var before = snapshot.build(c);
    String encoded = snapshot.encode(before);
    assertTrue(encoded.contains(observation.toString()));
    assertTrue(encoded.contains(predecessor.toString()));
    assertTrue(encoded.contains("2026-10-15T10:00:00Z"));
    assertTrue(encoded.contains("PATIENT_DECLARED_NOT_VALIDATED"));
    assertTrue(encoded.contains("non validées"));
    var after = snapshot.build(c);
    assertNotEquals(before.get("sourceDigest"), after.get("sourceDigest"));
    assertNotEquals(snapshot.hash(before), snapshot.hash(after));
    verify(db, times(2))
        .queryForList(
            argThat(
                (String sql) ->
                    sql.contains("from patienthealth.observation o") && sql.contains("limit 100")),
            eq(patient));
    verify(db, times(2))
        .queryForList(
            argThat(
                (String sql) ->
                    sql.contains("from patienthealth.declaration d") && sql.contains("limit 50")),
            eq(patient));
  }
}
