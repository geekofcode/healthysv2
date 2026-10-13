package org.novasos.healthysv2.care;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.util.Map;
import java.util.UUID;
import org.novasos.healthysv2.audit.AuditTrail;
import org.novasos.healthysv2.patient.CurrentUserContext;
import org.novasos.healthysv2.shared.api.error.BusinessRuleException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@Transactional(readOnly = true)
public class CutoverService {
  private final JdbcTemplate db;
  private final ObjectMapper json;
  private final AuditTrail audit;
  private final CurrentUserContext users;

  CutoverService(JdbcTemplate db, ObjectMapper json, AuditTrail audit, CurrentUserContext users) {
    this.db = db;
    this.json = json;
    this.audit = audit;
    this.users = users;
  }

  public record State(
      String mode,
      boolean migrationValidated,
      boolean journeyValidated,
      boolean restorationValidated) {}

  public State read() {
    var rows =
        db.queryForList(
            """
            select s.mode,r.status,r.evidence from care.cutover_state s
            left join care.migration_run r on r.id=s.migration_run_id where s.singleton
            """);
    if (rows.isEmpty()) return new State("LEGACY", false, false, false);
    var row = rows.getFirst();
    var evidence = evidence(row.get("evidence"));
    return new State(
        row.get("mode").toString(),
        "VERIFIED".equals(row.get("status")),
        validated(evidence, "journey"),
        validated(evidence, "restoration"));
  }

  @Transactional
  public State activate(UUID runId) {
    var actor = users.current();
    if (!actor.has("PLATFORM_ADMIN") || actor.personId() == null)
      throw new org.springframework.security.access.AccessDeniedException(
          "PLATFORM_ADMIN_REQUIRED");
    db.queryForObject(
        "select singleton from care.cutover_state where singleton for update", Boolean.class);
    var rows =
        db.queryForList(
            "select status,evidence,report from care.migration_run where id=? for update", runId);
    if (rows.isEmpty() || !"VERIFIED".equals(rows.getFirst().get("status")))
      throw blocked("A verified migration run is required");
    var row = rows.getFirst();
    var proof = evidence(row.get("evidence"));
    if (!validated(proof, "journey") || !validated(proof, "restoration"))
      throw blocked("The cash journey and a restore drill must be verified before cutover");
    for (String key :
        new String[] {
          "backup_manifest_sha256",
          "restoration_test_sha256",
          "cash_e2e_sha256",
          "migration_review_sha256"
        })
      if (!proof.path(key).asText().matches("[a-fA-F0-9]{64}"))
        throw blocked("Missing verification artifact fingerprints");
    var report = evidence(row.get("report"));
    if (!report.path("anomalies").isArray() || !report.path("anomalies").isEmpty())
      throw blocked("Resolve migration anomalies before cutover");
    db.update(
        """
        update care.cutover_state set mode='CARE_ONLY',migration_run_id=?,evidence=?::jsonb,
        approved_by=?,approved_at=clock_timestamp(),updated_at=clock_timestamp() where singleton
        """,
        runId,
        proof.toString(),
        actor.personId().toString());
    audit.change(
        actor.personId(),
        null,
        "CARE",
        "MigrationRun",
        runId,
        "ACTIVATE_CARE_ONLY",
        null,
        Map.of("mode", "CARE_ONLY"));
    return read();
  }

  public void requireLegacyWrite() {
    if ("CARE_ONLY".equals(read().mode()))
      throw new BusinessRuleException(
          "LEGACY_PATH_RETIRED",
          "The historical care pathway is read-only; use episodes and visits");
  }

  private JsonNode evidence(Object value) {
    try {
      return value == null ? json.createObjectNode() : json.readTree(value.toString());
    } catch (Exception invalid) {
      throw blocked("Invalid cutover evidence");
    }
  }

  private static boolean validated(JsonNode node, String field) {
    return node.path(field).path("validated").isBoolean()
        && node.path(field).path("validated").asBoolean();
  }

  private static BusinessRuleException blocked(String message) {
    return new BusinessRuleException("CUTOVER_NOT_VERIFIED", message);
  }
}
