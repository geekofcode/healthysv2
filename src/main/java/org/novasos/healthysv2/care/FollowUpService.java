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
class FollowUpService {
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

  private final CareEpisodeRepository episodes;
  private final CareVisitRepository visits;
  private final CurrentUserContext users;
  private final LicensingAccess licensing;
  private final VisitSlotService slots;
  private final VisitSchedulingService scheduling;
  private final JdbcTemplate jdbc;
  private final CarePolicy policy;
  private final AuditTrail audit;

  FollowUpService(
      CareEpisodeRepository episodes,
      CareVisitRepository visits,
      CurrentUserContext users,
      LicensingAccess licensing,
      VisitSlotService slots,
      VisitSchedulingService scheduling,
      JdbcTemplate jdbc,
      CarePolicy policy,
      AuditTrail audit) {
    this.episodes = episodes;
    this.visits = visits;
    this.users = users;
    this.licensing = licensing;
    this.slots = slots;
    this.scheduling = scheduling;
    this.jdbc = jdbc;
    this.policy = policy;
    this.audit = audit;
  }

  VisitSchedulingDtos.Visit create(UUID episodeId, String key, FollowUpController.Request request) {
    var user = users.current();
    if (user.personId() == null || user.organizationId() == null)
      throw new AccessDeniedException("FOLLOW_UP_RECEPTION_REQUIRED");
    permission(user.organizationId(), "visits", "CREATE");
    if (key == null || !key.matches("[A-Za-z0-9._:-]{8,128}"))
      throw new BusinessRuleException("INVALID_IDEMPOTENCY_KEY", "An idempotency key is required");
    jdbc.queryForObject(
        "select id from organization.organization where id=? for update",
        UUID.class,
        user.organizationId());
    licensing.requireMember(user.organizationId());
    licensing.requireActive(user.organizationId());
    if (user.has("PLATFORM_ADMIN"))
      throw new org.springframework.security.access.AccessDeniedException(
          "PLATFORM_OPERATION_DENIED");
    permission(user.organizationId(), "visits", "CREATE");
    var episode =
        episodes
            .findLockedById(episodeId)
            .filter(e -> e.getOrganizationId().equals(user.organizationId()))
            .orElseThrow(() -> new ResourceNotFoundException("CareEpisode", episodeId));
    String reason = request.reason() == null ? "" : request.reason().trim();
    String fingerprint = request.professionalId() + "|" + request.scheduledAt() + "|" + reason;
    var prior =
        jdbc.queryForList(
            "select visit_id,fingerprint from care.follow_up_request where episode_id=? and"
                + " idempotency_key=?",
            episodeId,
            key);
    if (!prior.isEmpty()) {
      if (!fingerprint.equals(prior.getFirst().get("fingerprint")))
        throw new ConflictException("IDEMPOTENCY_CONFLICT", "This key has different visit details");
      return scheduling.find((UUID) prior.getFirst().get("visit_id"));
    }
    Instant now = Instant.now();
    if (request.scheduledAt().isBefore(now))
      throw new BusinessRuleException("INVALID_VISIT_DATE", "Follow-up date is in the past");
    if (!episode.isActiveAt(now) || !episode.isActiveAt(request.scheduledAt())) {
      throw new BusinessRuleException(
          "FOLLOW_UP_EXPIRED", "Follow-up must be within the active episode coverage");
    }
    var visit =
        CareVisit.schedule(
            episode, request.professionalId(), request.scheduledAt(), true, policy, now);
    int duration =
        slots.requireSlot(
            request.professionalId(),
            user.organizationId(),
            episode.getSpecialityId(),
            request.scheduledAt(),
            visit.getId());
    visit.program(request.professionalId(), request.scheduledAt(), duration, now);
    visit.specifyReason(request.reason());
    visits.saveAndFlush(visit);
    jdbc.update(
        "insert into care.follow_up_request values(?,?,?,?)",
        episodeId,
        key,
        fingerprint,
        visit.getId());
    audit.change(
        user.personId(),
        user.organizationId(),
        "CARE",
        "CareVisit",
        visit.getId(),
        "FREE_FOLLOW_UP",
        null,
        java.util.Map.of("episodeId", episodeId, "scheduledAt", request.scheduledAt()));
    return scheduling.find(visit.getId());
  }
}
