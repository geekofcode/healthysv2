package org.novasos.healthysv2.care;

import java.time.Instant;
import java.util.UUID;
import org.novasos.healthysv2.audit.AuditTrail;
import org.novasos.healthysv2.licensing.api.LicensingAccess;
import org.novasos.healthysv2.patient.CurrentUserContext;
import org.novasos.healthysv2.shared.api.error.*;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@Transactional
class VisitSchedulingService {
  private org.novasos.healthysv2.organizationaccess.api.OrganizationPermissionAccess permissions;

  @org.springframework.beans.factory.annotation.Autowired
  public void configurePermissions(
      org.novasos.healthysv2.organizationaccess.api.OrganizationPermissionAccess permissions) {
    this.permissions = java.util.Objects.requireNonNull(permissions);
  }

  private void permission(java.util.UUID org, String resource, String action) {
    if (permissions == null)
      throw new org.springframework.security.access.AccessDeniedException(
          "ORGANIZATION_PERMISSIONS_UNAVAILABLE");
    permissions.require(org, resource, action);
  }

  private final CareVisitRepository visits;
  private final CurrentUserContext users;
  private final LicensingAccess licensing;
  private final VisitSlotService slots;
  private final PhysicianQueueService queues;
  private final JdbcTemplate jdbc;
  private final AuditTrail audit;

  VisitSchedulingService(
      CareVisitRepository visits,
      CurrentUserContext users,
      LicensingAccess licensing,
      VisitSlotService slots,
      PhysicianQueueService queues,
      JdbcTemplate jdbc,
      AuditTrail audit) {
    this.visits = visits;
    this.users = users;
    this.licensing = licensing;
    this.slots = slots;
    this.queues = queues;
    this.jdbc = jdbc;
    this.audit = audit;
  }

  VisitSchedulingDtos.Visit program(UUID id, VisitSchedulingDtos.Program request) {
    var user = programmer();
    jdbc.queryForObject(
        "select id from organization.organization where id=? for update",
        UUID.class,
        user.organizationId());
    recheckProgrammer(user);
    var visit =
        visits
            .findLockedById(id)
            .filter(v -> v.episode().getOrganizationId().equals(user.organizationId()))
            .orElseThrow(() -> new ResourceNotFoundException("CareVisit", id));
    if (visit.getVersion() != request.version())
      throw new ConflictException("VISIT_VERSION_CONFLICT", "Reload the visit before modifying it");
    requireUnstartedPaid(visit);
    Instant now = Instant.now();
    if (request.scheduledAt().isBefore(now))
      throw new BusinessRuleException("INVALID_VISIT_DATE", "Visit date is in the past");
    if (visit.getStatus() == CareVisit.Status.SCHEDULED
        && request.scheduledAt().isBefore(visit.getScheduledAt())) {
      throw new BusinessRuleException(
          "POSTPONEMENT_REQUIRED", "A programmed visit can only move to a later date");
    }
    int duration =
        slots.requireSlot(
            request.professionalId(),
            user.organizationId(),
            visit.episode().getSpecialityId(),
            request.scheduledAt(),
            id);
    UUID previousDoctor = visit.getProfessionalId();
    Instant previousDate = visit.getScheduledAt();
    String previousStatus = visit.getStatus().name();
    // Invalidating even a same-day move resets attendance/call state and the old queue's version.
    queues.invalidateVisit(id);
    try {
      visit.program(request.professionalId(), request.scheduledAt(), duration, now);
    } catch (IllegalArgumentException | IllegalStateException e) {
      throw new BusinessRuleException("VISIT_CANNOT_PROGRAM", e.getMessage());
    }
    visits.flush();
    jdbc.update(
        """
        insert into care.visit_schedule_history(visit_id,organization_id,previous_professional_id,professional_id,
            previous_scheduled_at,scheduled_at,previous_status,status,actor_person_id,reason)
        values(?,?,?,?,?,?,?,?,?,?)
        """,
        id,
        user.organizationId(),
        previousDoctor,
        visit.getProfessionalId(),
        java.sql.Timestamp.from(previousDate),
        java.sql.Timestamp.from(visit.getScheduledAt()),
        previousStatus,
        visit.getStatus().name(),
        user.personId(),
        request.reason());
    audit.change(
        user.personId(),
        user.organizationId(),
        "CARE",
        "CareVisit",
        id,
        "PROGRAM",
        null,
        java.util.Map.of(
            "professionalId", visit.getProfessionalId(), "scheduledAt", visit.getScheduledAt()));
    return response(visit);
  }

  @Transactional(readOnly = true)
  VisitSchedulingDtos.Visit find(UUID id) {
    var user = users.current();
    if (user.organizationId() == null)
      throw new AccessDeniedException("ORGANIZATION_CONTEXT_REQUIRED");
    permission(user.organizationId(), "visits", "READ");
    licensing.requireMember(user.organizationId());
    var visit =
        visits
            .findById(id)
            .filter(v -> v.episode().getOrganizationId().equals(user.organizationId()))
            .orElseThrow(() -> new ResourceNotFoundException("CareVisit", id));
    return response(visit);
  }

  private CurrentUserContext.UserContext programmer() {
    var user = users.current();
    if (user.personId() == null || user.organizationId() == null)
      throw new AccessDeniedException("VISIT_PROGRAMMER_REQUIRED");
    permission(user.organizationId(), "scheduling", "UPDATE");
    licensing.requireMember(user.organizationId());
    licensing.requireActive(user.organizationId());
    return user;
  }

  private void recheckProgrammer(CurrentUserContext.UserContext user) {
    licensing.requireMember(user.organizationId());
    licensing.requireActive(user.organizationId());
    if (user.has("PLATFORM_ADMIN"))
      throw new org.springframework.security.access.AccessDeniedException(
          "PLATFORM_OPERATION_DENIED");
    permission(user.organizationId(), "scheduling", "UPDATE");
  }

  private void requireUnstartedPaid(CareVisit visit) {
    if (visit.getStartedAt() != null
        || (visit.getStatus() != CareVisit.Status.SCHEDULED
            && visit.getStatus() != CareVisit.Status.AWAITING_SCHEDULING)) {
      throw new BusinessRuleException(
          "VISIT_NOT_PROGRAMMABLE", "Only paid, unstarted visits can be programmed");
    }
    var episode = visit.episode();
    if ((!visit.isFollowUp() && episode.getStatus() != CareEpisode.Status.READY)
        || (visit.isFollowUp() && !episode.isActiveAt(Instant.now()))) {
      throw new BusinessRuleException(
          "EPISODE_NOT_ELIGIBLE", "Episode has an outstanding balance or expired follow-up");
    }
  }

  private VisitSchedulingDtos.Visit response(CareVisit visit) {
    var history =
        jdbc.query(
            "select * from care.visit_schedule_history where visit_id=? order by changed_at,id",
            (r, n) ->
                new VisitSchedulingDtos.History(
                    r.getObject("id", UUID.class),
                    r.getObject("previous_professional_id", UUID.class),
                    r.getObject("professional_id", UUID.class),
                    r.getTimestamp("previous_scheduled_at").toInstant(),
                    r.getTimestamp("scheduled_at").toInstant(),
                    r.getString("previous_status"),
                    r.getString("status"),
                    r.getObject("actor_person_id", UUID.class),
                    r.getString("reason"),
                    r.getTimestamp("changed_at").toInstant()),
            visit.getId());
    var episode = visit.episode();
    return new VisitSchedulingDtos.Visit(
        visit.getId(),
        episode.getId(),
        episode.getPatientId(),
        episode.getOrganizationId(),
        visit.getProfessionalId(),
        visit.getScheduledAt(),
        visit.getStatus().name(),
        visit.isFollowUp(),
        visit.getVersion(),
        visit.getSlotMinutes(),
        visit.getCreatedAt(),
        history);
  }
}
