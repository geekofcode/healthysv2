package org.novasos.healthysv2.audit;

import java.time.Instant;
import java.util.List;
import java.util.UUID;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

/** Deliberately excludes hospital operations, clinical payloads and authentication details. */
@RestController
@RequestMapping("/api/v1/admin")
@PreAuthorize("hasRole('PLATFORM_ADMIN')")
class PlatformActivityController {
  private final JdbcTemplate db;

  PlatformActivityController(JdbcTemplate db) {
    this.db = db;
  }

  record Activity(
      UUID id,
      UUID actorPersonId,
      UUID organizationId,
      String source,
      String action,
      UUID targetId,
      Instant occurredAt) {}

  record AuthenticationEvent(
      UUID id, UUID personId, String eventType, boolean success, Instant occurredAt) {}

  record Subscription(
      UUID organizationId,
      String organizationName,
      UUID planId,
      String planName,
      String status,
      Instant periodStart,
      Instant periodEnd,
      boolean cancelAtPeriodEnd,
      int doctorQuota,
      int professionalQuota,
      int cashierQuota,
      int accountantQuota,
      boolean aiAssistantEnabled,
      Long amountMinor,
      String currency,
      String billingInterval) {}

  @GetMapping("/platform-audit")
  List<Activity> audit(@RequestParam(defaultValue = "100") int limit) {
    return db.query(
        """
        select id,actor_person_id,organization_id,source,action,target_id,occurred_at from (
          select id,actor_person_id,organization_id,'LICENSING' source,action,target_id,created_at occurred_at
          from licensing.audit_entry where action in ('PLAN_UPDATED','PLAN_CREATED','PLAN_DISABLED')
          union all
          select id,actor_person_id,organization_id,module source,action,entity_id target_id,occurred_at
          from audit.audit_log where (module='ORGANIZATION' and entity_type='Organization')
            or (module='PROFILE_CONTRACT' and entity_type='ContractTemplate')
            or (module='PLATFORM_ACCOUNT' and entity_type='AccountReset')
        ) events order by occurred_at desc,id limit ?
        """,
        (rs, n) ->
            new Activity(
                rs.getObject("id", UUID.class),
                rs.getObject("actor_person_id", UUID.class),
                rs.getObject("organization_id", UUID.class),
                rs.getString("source"),
                rs.getString("action"),
                rs.getObject("target_id", UUID.class),
                rs.getTimestamp("occurred_at").toInstant()),
        bounded(limit));
  }

  @GetMapping("/account-authentication")
  List<AuthenticationEvent> authentication(@RequestParam(defaultValue = "100") int limit) {
    return db.query(
        "select id,person_id,event_type,success,occurred_at from audit.authentication_log order by"
            + " occurred_at desc,id limit ?",
        (rs, n) ->
            new AuthenticationEvent(
                rs.getObject("id", UUID.class),
                rs.getObject("person_id", UUID.class),
                rs.getString("event_type"),
                rs.getBoolean("success"),
                rs.getTimestamp("occurred_at").toInstant()),
        bounded(limit));
  }

  @GetMapping("/subscriptions")
  List<Subscription> subscriptions() {
    return db.query(
        """
        select s.*,o.name organization_name,p.name plan_name from licensing.subscription s
        join organization.organization o on o.id=s.organization_id left join licensing.plan p on p.id=s.plan_id
        order by o.name,s.organization_id
        """,
        (rs, n) ->
            new Subscription(
                rs.getObject("organization_id", UUID.class),
                rs.getString("organization_name"),
                rs.getObject("plan_id", UUID.class),
                rs.getString("plan_name"),
                rs.getString("status"),
                instant(rs.getTimestamp("period_start")),
                instant(rs.getTimestamp("period_end")),
                rs.getBoolean("cancel_at_period_end"),
                rs.getInt("doctor_quota"),
                rs.getInt("professional_quota"),
                rs.getInt("cashier_quota"),
                rs.getInt("accountant_quota"),
                rs.getBoolean("ai_assistant_enabled"),
                rs.getObject("amount_minor", Long.class),
                rs.getString("currency"),
                rs.getString("billing_interval")));
  }

  private static Instant instant(java.sql.Timestamp value) {
    return value == null ? null : value.toInstant();
  }

  private static int bounded(int limit) {
    return Math.max(1, Math.min(limit, 500));
  }
}
