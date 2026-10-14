package org.novasos.healthysv2.care;

import java.sql.Timestamp;
import java.time.Instant;
import java.util.*;
import org.novasos.healthysv2.audit.AuditTrail;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationToken;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/v1/care/visits/me")
class PatientVisitsController {
  private final JdbcTemplate db;
  private final AuditTrail audit;
  private final org.novasos.healthysv2.patient.PatientSelfAccess selves;

  PatientVisitsController(
      JdbcTemplate db, AuditTrail audit, org.novasos.healthysv2.patient.PatientSelfAccess selves) {
    this.db = db;
    this.audit = audit;
    this.selves = selves;
  }

  record Visit(
      UUID id,
      UUID episodeId,
      UUID organizationId,
      String organizationName,
      UUID professionalId,
      String doctorName,
      Instant scheduledAt,
      String status,
      String reason,
      boolean followUp) {}

  @GetMapping
  @Transactional(readOnly = true)
  List<Visit> mine(
      @RequestParam(required = false) Instant from, @RequestParam(required = false) Instant to) {
    var authentication = SecurityContextHolder.getContext().getAuthentication();
    if (!(authentication instanceof JwtAuthenticationToken token))
      throw new AccessDeniedException("AUTHENTICATED_PATIENT_REQUIRED");
    UUID subject;
    try {
      subject = UUID.fromString(token.getToken().getSubject());
    } catch (IllegalArgumentException e) {
      throw new AccessDeniedException("INVALID_SUBJECT");
    }
    var selected = selves.resolve(subject);
    UUID person = selected.person(), patient = selected.patient();
    Instant begin = from == null ? Instant.now().minusSeconds(30L * 86400) : from;
    Instant end = to == null ? Instant.now().plusSeconds(366L * 86400) : to;
    if (!end.isAfter(begin)) throw new IllegalArgumentException("INVALID_VISIT_PERIOD");
    var result =
        db.query(
            """
            select v.id,v.episode_id,e.organization_id,o.name as organization_name,v.professional_id,
            concat(author.first_name,' ',author.last_name) as doctor_name,v.scheduled_at,v.status,v.reason,v.follow_up
            from care.care_visit v join care.care_episode e on e.id=v.episode_id
            join organization.organization o on o.id=e.organization_id
            join professional.professional p on p.id=v.professional_id join identity.person author on author.id=p.person_id
            where e.patient_id=? and v.scheduled_at>=? and v.scheduled_at<? order by v.scheduled_at,v.id limit 200
            """,
            (rs, n) ->
                new Visit(
                    rs.getObject("id", UUID.class),
                    rs.getObject("episode_id", UUID.class),
                    rs.getObject("organization_id", UUID.class),
                    rs.getString("organization_name"),
                    rs.getObject("professional_id", UUID.class),
                    rs.getString("doctor_name"),
                    rs.getTimestamp("scheduled_at").toInstant(),
                    rs.getString("status"),
                    rs.getString("reason"),
                    rs.getBoolean("follow_up")),
            patient,
            Timestamp.from(begin),
            Timestamp.from(end));
    audit.access(
        person,
        patient,
        null,
        "CARE_VISITS",
        patient,
        "READ",
        "PATIENT_SELF",
        Map.of("count", result.size()));
    return result;
  }
}
