package org.novasos.healthysv2.audit;

import java.util.UUID;
import org.novasos.healthysv2.shared.api.error.ResourceNotFoundException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.bind.annotation.*;

/**
 * Only aggregate counts cross the platform/establishment boundary. No clinical rows are returned.
 */
@RestController
@RequestMapping("/api/v1/admin")
@PreAuthorize("hasRole('PLATFORM_ADMIN')")
class PlatformStatisticsController {
  private final JdbcTemplate jdbc;

  PlatformStatisticsController(JdbcTemplate jdbc) {
    this.jdbc = jdbc;
  }

  @GetMapping("/statistics")
  @Transactional(readOnly = true)
  Statistics global() {
    return statistics(null);
  }

  @GetMapping("/organizations/{organizationId}/statistics")
  @Transactional(readOnly = true)
  Statistics organization(@PathVariable UUID organizationId) {
    if (count("select count(*) from organization.organization where id=?", organizationId) == 0)
      throw new ResourceNotFoundException("Organization", organizationId);
    return statistics(organizationId);
  }

  private Statistics statistics(UUID org) {
    return new Statistics(
        org,
        org == null ? count("select count(*) from organization.organization") : 1,
        org == null
            ? count("select count(*) from patient.patient")
            : count(
                "select count(distinct patient_id) from care.care_episode where organization_id=?",
                org),
        scoped("licensing.member", "organization_id", org, "status='ACTIVE'"),
        scoped("care.care_episode", "organization_id", org, "true"),
        org == null
            ? count("select count(*) from care.care_visit")
            : count(
                "select count(*) from care.care_visit v join care.care_episode e on"
                    + " e.id=v.episode_id where e.organization_id=?",
                org),
        org == null
            ? count("select count(*) from care.care_visit where status='COMPLETED'")
            : count(
                "select count(*) from care.care_visit v join care.care_episode e on"
                    + " e.id=v.episode_id where e.organization_id=? and v.status='COMPLETED'",
                org),
        org == null
            ? count("select count(*) from document.document")
            : count(
                "select count(distinct d.id) from document.document d join document.document_link l"
                    + " on l.document_id=d.id join care.care_visit v on v.id=l.resource_id join"
                    + " care.care_episode e on e.id=v.episode_id where l.resource_type='CARE_VISIT'"
                    + " and e.organization_id=?",
                org),
        scoped("billing.invoice", "organization_id", org, "true"),
        org == null
            ? count("select count(*) from billing.cash_operation")
            : count(
                "select count(*) from billing.cash_operation c join billing.invoice i on"
                    + " i.id=c.invoice_id where i.organization_id=?",
                org));
  }

  private long scoped(String table, String column, UUID org, String extra) {
    return org == null
        ? count("select count(*) from " + table + " where " + extra)
        : count("select count(*) from " + table + " where " + column + "=? and " + extra, org);
  }

  private long count(String sql, Object... params) {
    Long value = jdbc.queryForObject(sql, Long.class, params);
    return value == null ? 0 : value;
  }

  record Statistics(
      UUID organizationId,
      long organizations,
      long patients,
      long employees,
      long episodes,
      long visits,
      long completedVisits,
      long documents,
      long invoices,
      long cashOperations) {}
}
