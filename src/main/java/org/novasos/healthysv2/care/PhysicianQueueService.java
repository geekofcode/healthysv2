package org.novasos.healthysv2.care;

import java.sql.Timestamp;
import java.time.*;
import java.util.*;
import org.novasos.healthysv2.audit.AuditTrail;
import org.novasos.healthysv2.licensing.api.LicensingAccess;
import org.novasos.healthysv2.patient.CurrentUserContext;
import org.novasos.healthysv2.shared.api.error.*;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/** A call changes only the queue, never the clinical visit or follow-up period. */
@Service
public class PhysicianQueueService {
  private org.novasos.healthysv2.organizationaccess.api.OrganizationPermissionAccess permissions;

  @org.springframework.beans.factory.annotation.Autowired
  public void configurePermissions(
      org.novasos.healthysv2.organizationaccess.api.OrganizationPermissionAccess permissions) {
    this.permissions = Objects.requireNonNull(permissions);
  }

  private void requirePermission(UUID org, String resource, String action) {
    if (permissions == null) throw denied("ORGANIZATION_PERMISSIONS_UNAVAILABLE");
    permissions.require(org, resource, action);
  }

  private final JdbcTemplate jdbc;
  private final CurrentUserContext users;
  private final LicensingAccess licensing;
  private final AuditTrail audit;

  PhysicianQueueService(
      JdbcTemplate jdbc, CurrentUserContext users, LicensingAccess licensing, AuditTrail audit) {
    this.jdbc = jdbc;
    this.users = users;
    this.licensing = licensing;
    this.audit = audit;
  }

  @Transactional
  public PhysicianQueueDtos.Calendar calendar(UUID professionalId, LocalDate date) {
    var user = user("scheduling", "READ");
    lockOrganization(user.organizationId());
    user = lockedUser(user, "scheduling", "READ");
    UUID professional = professionalId == null ? ownProfessional(user) : professionalId;
    authorizeDoctor(user, professional, false);
    var queue = queue(user.organizationId(), professional, date);
    return result(queue);
  }

  @Transactional
  public PhysicianQueueDtos.Calendar presence(PhysicianQueueDtos.Presence request) {
    var user = user("queue", "UPDATE");
    licensing.requireActive(user.organizationId());
    lockOrganization(user.organizationId());
    user = lockedUser(user, "queue", "UPDATE");
    var target =
        jdbc.query(
            """
            select v.professional_id,v.scheduled_at,v.status from care.care_visit v
            join care.care_episode e on e.id=v.episode_id where v.id=? and e.organization_id=?
            """,
            rs ->
                rs.next()
                    ? new Target(
                        rs.getObject(1, UUID.class),
                        rs.getTimestamp(2).toInstant(),
                        rs.getString(3))
                    : null,
            request.visitId(),
            user.organizationId());
    if (target == null) throw new ResourceNotFoundException("CareVisit", request.visitId());
    if (!target.status().equals("SCHEDULED"))
      throw conflict("Only scheduled visits have a reception presence");
    authorizeDoctor(user, target.professional(), false);
    LocalDate day = target.instant().atZone(zone(user.organizationId())).toLocalDate();
    var queue = queue(user.organizationId(), target.professional(), day);
    checkVersion(queue.version(), request.expectedVersion());
    requireOpen(queue);
    jdbc.update(
        "update care.queue_entry set presence=? where visit_id=? and queue_id=?",
        request.present() ? "PRESENT" : "WAITING",
        request.visitId(),
        queue.id());
    changed(
        queue,
        user,
        "PRESENCE",
        Map.of("visitId", request.visitId(), "present", request.present()));
    return result(refresh(queue));
  }

  @Transactional
  public PhysicianQueueDtos.Calendar next(PhysicianQueueDtos.Next request) {
    var user = user("queue", "UPDATE");
    licensing.requireActive(user.organizationId());
    lockOrganization(user.organizationId());
    user = lockedUser(user, "queue", "UPDATE");
    authorizeDoctor(user, request.professionalId(), true);
    var queue = queue(user.organizationId(), request.professionalId(), request.date());
    checkVersion(queue.version(), request.expectedVersion());
    requireOpen(queue);
    UUID visit =
        jdbc.query(
            """
            select entry.visit_id from care.queue_entry entry join care.care_visit visit on visit.id=entry.visit_id
            where entry.queue_id=? and entry.presence='PRESENT' and visit.status='SCHEDULED'
            order by entry.position,entry.visit_id limit 1
            """,
            rs -> rs.next() ? rs.getObject(1, UUID.class) : null,
            queue.id());
    if (visit == null) throw conflict("No present patient is waiting");
    jdbc.update("update care.queue_entry set presence='CALLED' where visit_id=?", visit);
    changed(queue, user, "CALL_NEXT", Map.of("visitId", visit));
    return result(refresh(queue));
  }

  @Transactional
  public PhysicianQueueDtos.Calendar order(PhysicianQueueDtos.Order request) {
    var user = user("queue", "UPDATE");
    licensing.requireActive(user.organizationId());
    lockOrganization(user.organizationId());
    user = lockedUser(user, "queue", "UPDATE");
    authorizeDoctor(user, request.professionalId(), true);
    var queue = queue(user.organizationId(), request.professionalId(), request.date());
    checkVersion(queue.version(), request.expectedVersion());
    requireOpen(queue);
    var ids = result(queue).visits().stream().map(PhysicianQueueDtos.Visit::visitId).toList();
    validateOrder(ids, request.visitIds());
    for (int index = 0; index < request.visitIds().size(); index++)
      jdbc.update(
          "update care.queue_entry set position=? where queue_id=? and visit_id=?",
          index + 1L,
          queue.id(),
          request.visitIds().get(index));
    jdbc.update("update care.physician_queue set custom_order=true where id=?", queue.id());
    changed(queue, user, "REORDER", Map.of("visitIds", request.visitIds()));
    return result(refresh(queue));
  }

  /** Caller must lock organization before visit/episode locks; scheduling uses the same order. */
  @Transactional
  public void invalidateVisit(UUID visitId) {
    UUID queueId =
        jdbc.query(
            "select queue_id from care.queue_entry where visit_id=?",
            rs -> rs.next() ? rs.getObject(1, UUID.class) : null,
            visitId);
    if (queueId == null) return;
    jdbc.update("delete from care.queue_entry where visit_id=?", visitId);
    jdbc.update("update care.physician_queue set version=version+1 where id=?", queueId);
  }

  private CurrentUserContext.UserContext user(String resource, String action) {
    var user = users.current();
    validateProfile(user);
    licensing.requireMember(user.organizationId());
    requirePermission(user.organizationId(), resource, action);
    return user;
  }

  private void validateProfile(CurrentUserContext.UserContext user) {
    if (user.personId() == null
        || user.profileId() == null
        || user.organizationId() == null
        || user.roles().isEmpty()
        || user.hasAny("PLATFORM_ADMIN", "PATIENT"))
      throw denied("SELECTED_HOSPITAL_PROFILE_REQUIRED");
  }

  /** Re-resolve the same persisted source after waiting for the establishment lock. */
  private CurrentUserContext.UserContext lockedUser(
      CurrentUserContext.UserContext previous, String resource, String action) {
    var refreshed = users.current();
    validateProfile(refreshed);
    if (!Objects.equals(previous.personId(), refreshed.personId())
        || !Objects.equals(previous.organizationId(), refreshed.organizationId())
        || !Objects.equals(previous.profileId(), refreshed.profileId())
        || !previous.roles().equals(refreshed.roles())) throw denied("PROFILE_CONTEXT_CHANGED");
    licensing.requireMember(refreshed.organizationId());
    if (!action.equals("READ")) licensing.requireActive(refreshed.organizationId());
    requirePermission(refreshed.organizationId(), resource, action);
    return refreshed;
  }

  private UUID ownProfessional(CurrentUserContext.UserContext user) {
    UUID id =
        jdbc.query(
            "select id from professional.professional where person_id=? and status='ACTIVE' and"
                + " professional_type in ('DOCTOR','MEDECIN','NURSE')",
            rs -> rs.next() ? rs.getObject(1, UUID.class) : null,
            user.personId());
    if (id == null || !user.hasAny("DOCTOR", "NURSE")) throw denied("DOCTOR_REQUIRED");
    return id;
  }

  private void authorizeDoctor(
      CurrentUserContext.UserContext user, UUID professional, boolean doctorOnly) {
    if (doctorOnly || user.has("DOCTOR")) {
      if (!professional.equals(ownProfessional(user))) throw denied("OWN_DOCTOR_QUEUE_REQUIRED");
    }
    Integer count =
        jdbc.queryForObject(
            """
            select count(*) from professional.professional p
            join identity.person person on person.id=p.person_id and person.status='ACTIVE'
            where p.id=? and p.status='ACTIVE' and p.professional_type in ('DOCTOR','MEDECIN','NURSE')
            and exists(select 1 from licensing.member m where m.person_id=p.person_id and m.organization_id=? and ((m.role='DOCTOR' and p.professional_type in ('DOCTOR','MEDECIN')) or (m.role='NURSE' and p.professional_type='NURSE')) and m.status='ACTIVE')
            and exists(select 1 from professional.professional_assignment a where a.professional_id=p.id and a.organization_id=?
                and a.status='ACTIVE' and a.start_date<=current_date and (a.end_date is null or a.end_date>=current_date))
            """,
            Integer.class,
            professional,
            user.organizationId(),
            user.organizationId());
    if (count == null || count == 0) throw denied("ACTIVE_ORGANIZATION_DOCTOR_REQUIRED");
  }

  private void lockOrganization(UUID org) {
    jdbc.queryForObject(
        "select id from organization.organization where id=? for update", UUID.class, org);
  }

  private ZoneId zone(UUID org) {
    return ZoneId.of(
        jdbc.queryForObject(
            "select timezone from organization.organization where id=?", String.class, org));
  }

  private Queue queue(UUID org, UUID professional, LocalDate day) {
    jdbc.update(
        "insert into care.physician_queue(id,organization_id,professional_id,local_date)"
            + " values(?,?,?,?) on conflict(organization_id,professional_id,local_date) do nothing",
        UUID.randomUUID(),
        org,
        professional,
        day);
    Queue queue =
        jdbc.queryForObject(
            "select id,version from care.physician_queue where organization_id=? and"
                + " professional_id=? and local_date=? for update",
            (rs, row) ->
                new Queue(rs.getObject(1, UUID.class), org, professional, day, rs.getLong(2)),
            org,
            professional,
            day);
    sync(queue);
    return refresh(queue);
  }

  private void sync(Queue queue) {
    ZoneId timezone = zone(queue.org());
    var window = dateWindow(queue.day(), timezone);
    Instant start = window.get(0);
    Instant end = window.get(1);
    int removed =
        jdbc.update(
            """
            delete from care.queue_entry q where q.queue_id=? and not exists(
              select 1 from care.care_visit v join care.care_episode e on e.id=v.episode_id where v.id=q.visit_id
              and e.organization_id=? and v.professional_id=? and v.scheduled_at>=? and v.scheduled_at<? and v.status in ('SCHEDULED','IN_PROGRESS','COMPLETED'))
            """,
            queue.id(),
            queue.org(),
            queue.professional(),
            Timestamp.from(start),
            Timestamp.from(end));
    int added =
        jdbc.update(
            """
            insert into care.queue_entry(visit_id,queue_id,position)
            select v.id,?,coalesce((select max(position) from care.queue_entry where queue_id=?),0)
                + row_number() over(order by v.created_at,v.id)
            from care.care_visit v join care.care_episode e on e.id=v.episode_id
            where e.organization_id=? and v.professional_id=? and v.scheduled_at>=? and v.scheduled_at<?
                and v.status in ('SCHEDULED','IN_PROGRESS','COMPLETED') and not exists(select 1 from care.queue_entry q where q.visit_id=v.id)
            """,
            queue.id(),
            queue.id(),
            queue.org(),
            queue.professional(),
            Timestamp.from(start),
            Timestamp.from(end));
    if (added + removed > 0) {
      // Until the physician changes the order, registration order remains the default.
      jdbc.update(
          """
          update care.queue_entry entry set position=ranked.position from (
            select q.visit_id,row_number() over(order by v.created_at,v.id) as position
            from care.queue_entry q join care.care_visit v on v.id=q.visit_id
            join care.physician_queue calendar on calendar.id=q.queue_id
            where q.queue_id=? and not calendar.custom_order
          ) ranked where entry.visit_id=ranked.visit_id
          """,
          queue.id());
      jdbc.update("update care.physician_queue set version=version+1 where id=?", queue.id());
    }
  }

  private Queue refresh(Queue queue) {
    Long version =
        jdbc.queryForObject(
            "select version from care.physician_queue where id=?", Long.class, queue.id());
    return new Queue(queue.id(), queue.org(), queue.professional(), queue.day(), version);
  }

  private PhysicianQueueDtos.Calendar result(Queue queue) {
    var visits =
        jdbc.query(
            """
            select v.id,v.episode_id,e.patient_id,p.first_name,p.last_name,v.scheduled_at,v.status,q.presence,q.position,v.version
            from care.queue_entry q join care.care_visit v on v.id=q.visit_id
            join care.care_episode e on e.id=v.episode_id join patient.patient patient on patient.id=e.patient_id
            join identity.person p on p.id=patient.person_id where q.queue_id=? order by q.position,v.id
            """,
            (rs, row) ->
                new PhysicianQueueDtos.Visit(
                    rs.getObject(1, UUID.class),
                    rs.getObject(2, UUID.class),
                    rs.getObject(3, UUID.class),
                    rs.getString(4),
                    rs.getString(5),
                    rs.getTimestamp(6).toInstant(),
                    rs.getString(7),
                    rs.getString(8),
                    rs.getLong(9),
                    rs.getLong(10)),
            queue.id());
    var window = dateWindow(queue.day(), zone(queue.org()));
    var closedWindows =
        jdbc.query(
            "select starts_at,ends_at,reason from care.closed_calendar_window where"
                + " organization_id=? and professional_id=? and starts_at<? and ends_at>? order by"
                + " starts_at,id",
            (r, n) ->
                new PhysicianQueueDtos.ClosedWindow(
                    r.getTimestamp(1).toInstant(), r.getTimestamp(2).toInstant(), r.getString(3)),
            queue.org(),
            queue.professional(),
            Timestamp.from(window.get(1)),
            Timestamp.from(window.get(0)));
    return new PhysicianQueueDtos.Calendar(
        queue.professional(),
        queue.day(),
        zone(queue.org()).getId(),
        queue.version(),
        visits,
        Boolean.TRUE.equals(
            jdbc.queryForObject(
                "select closed from care.physician_queue where id=?", Boolean.class, queue.id())),
        closedWindows);
  }

  private void requireOpen(Queue q) {
    if (Boolean.TRUE.equals(
        jdbc.queryForObject(
            "select closed from care.physician_queue where id=?", Boolean.class, q.id())))
      throw conflict("Calendar is closed");
  }

  @Transactional
  public PhysicianQueueDtos.Calendar close(PhysicianQueueDtos.Close request) {
    var user = user("queue", "UPDATE");
    licensing.requireActive(user.organizationId());
    lockOrganization(user.organizationId());
    user = lockedUser(user, "queue", "UPDATE");
    authorizeDoctor(user, request.professionalId(), true);
    var q = queue(user.organizationId(), request.professionalId(), request.date());
    checkVersion(q.version(), request.expectedVersion());
    requireOpen(q);
    boolean window = request.windowStart() != null || request.windowEnd() != null;
    var dayWindow = dateWindow(request.date(), zone(user.organizationId()));
    Instant from = window ? request.windowStart() : dayWindow.get(0),
        to = window ? request.windowEnd() : dayWindow.get(1);
    if (from == null
        || to == null
        || !to.isAfter(from)
        || from.isBefore(dayWindow.get(0))
        || to.isAfter(dayWindow.get(1)))
      throw new BusinessRuleException(
          "INVALID_CLOSE_WINDOW", "Closed window must be within the selected calendar day");
    Integer ongoing =
        jdbc.queryForObject(
            "select count(*) from care.queue_entry qe join care.care_visit v on v.id=qe.visit_id"
                + " where qe.queue_id=? and v.status='IN_PROGRESS' and v.scheduled_at<? and"
                + " v.scheduled_at+v.slot_minutes*interval '1 minute'>?",
            Integer.class,
            q.id(),
            Timestamp.from(to),
            Timestamp.from(from));
    if (ongoing != null && ongoing > 0)
      throw conflict("Complete in-progress visits before closing the calendar");
    var ids =
        deferReservations(
            q.org(), q.professional(), q.day(), from, to, user.personId(), request.reason());
    jdbc.update(
        "insert into"
            + " care.closed_calendar_window(organization_id,professional_id,starts_at,ends_at,reason,closed_by)"
            + " values(?,?,?,?,?,?)",
        q.org(),
        q.professional(),
        Timestamp.from(from),
        Timestamp.from(to),
        request.reason(),
        user.personId());
    if (!window)
      jdbc.update(
          "update care.physician_queue set closed=true,closed_at=now(),closed_by=? where id=?",
          user.personId(),
          q.id());
    changed(q, user, "CLOSE", Map.of("reason", request.reason(), "deferredVisitIds", ids));
    return result(refresh(q));
  }

  @Transactional
  public PhysicianQueueDtos.Calendar defer(PhysicianQueueDtos.Defer request) {
    var user = user("queue", "UPDATE");
    licensing.requireActive(user.organizationId());
    lockOrganization(user.organizationId());
    user = lockedUser(user, "queue", "UPDATE");
    var target =
        jdbc.query(
            "select v.professional_id,v.scheduled_at from care.care_visit v join care.care_episode"
                + " e on e.id=v.episode_id where v.id=? and e.organization_id=? and"
                + " v.status='SCHEDULED'",
            rs ->
                rs.next()
                    ? new Target(
                        rs.getObject(1, UUID.class), rs.getTimestamp(2).toInstant(), "SCHEDULED")
                    : null,
            request.visitId(),
            user.organizationId());
    if (target == null) throw new ResourceNotFoundException("CareVisit", request.visitId());
    authorizeDoctor(user, target.professional(), true);
    var q =
        queue(
            user.organizationId(),
            target.professional(),
            target.instant().atZone(zone(user.organizationId())).toLocalDate());
    checkVersion(q.version(), request.expectedVersion());
    requireOpen(q);
    defer(q, user, request.visitId(), request.reason());
    changed(q, user, "DEFER", Map.of("visitId", request.visitId(), "reason", request.reason()));
    return result(refresh(q));
  }

  /** Internal closure operation; caller holds the establishment lock and transaction. */
  List<UUID> deferReservations(
      UUID organization,
      UUID professional,
      LocalDate day,
      Instant from,
      Instant to,
      UUID actor,
      String reason) {
    var ids =
        jdbc.queryForList(
            """
            select v.id from care.care_visit v join care.care_episode e on e.id=v.episode_id
            where e.organization_id=? and v.professional_id=? and v.started_at is null
              and v.status in ('PENDING_PAYMENT','AWAITING_SCHEDULING','SCHEDULED')
              and v.scheduled_at<? and v.scheduled_at+v.slot_minutes*interval '1 minute'>?
              and not exists(select 1 from care.reassignment_request r where r.visit_id=v.id and r.status='PENDING')
            order by v.created_at,v.id for update of v
            """,
            UUID.class,
            organization,
            professional,
            Timestamp.from(to),
            Timestamp.from(from));
    for (UUID id : ids) {
      jdbc.update(
          "insert into"
              + " care.reassignment_request(visit_id,organization_id,source_professional_id,source_date,reason,created_by)"
              + " values(?,?,?,?,?,?)",
          id,
          organization,
          professional,
          day,
          reason,
          actor);
      // Unpaid reservations stay unpaid; only previously scheduled paid visits lose their slot.
      jdbc.update(
          "update care.care_visit set status='AWAITING_SCHEDULING',version=version+1 where id=? and"
              + " status='SCHEDULED'",
          id);
      jdbc.update("delete from care.queue_entry where visit_id=?", id);
    }
    return ids;
  }

  private void defer(Queue q, CurrentUserContext.UserContext user, UUID id, String reason) {
    jdbc.update(
        "insert into"
            + " care.reassignment_request(visit_id,organization_id,source_professional_id,source_date,reason,created_by)"
            + " values(?,?,?,?,?,?)",
        id,
        q.org(),
        q.professional(),
        q.day(),
        reason,
        user.personId());
    jdbc.update(
        "update care.care_visit set status='AWAITING_SCHEDULING',version=version+1 where id=? and"
            + " status='SCHEDULED'",
        id);
    jdbc.update("delete from care.queue_entry where visit_id=?", id);
  }

  @Transactional(readOnly = true)
  public List<PhysicianQueueDtos.Reassignment> reassignments() {
    var u = user("scheduling", "READ");
    if (!u.hasAny("HOSPITAL_ADMIN", "ORG_ADMIN")) throw denied("HOSPITAL_ADMIN_REQUIRED");
    return jdbc.query(
        "select * from care.reassignment_request where organization_id=? and status='PENDING' order"
            + " by created_at,id",
        (r, n) ->
            new PhysicianQueueDtos.Reassignment(
                r.getObject("id", UUID.class),
                r.getObject("visit_id", UUID.class),
                r.getObject("source_professional_id", UUID.class),
                r.getObject("source_date", LocalDate.class),
                r.getString("reason")),
        u.organizationId());
  }

  private void changed(
      Queue queue, CurrentUserContext.UserContext user, String action, Object value) {
    jdbc.update("update care.physician_queue set version=version+1 where id=?", queue.id());
    audit.change(
        user.personId(), queue.org(), "CARE", "PhysicianQueue", queue.id(), action, null, value);
  }

  static List<Instant> dateWindow(LocalDate day, ZoneId timezone) {
    return List.of(
        day.atStartOfDay(timezone).toInstant(), day.plusDays(1).atStartOfDay(timezone).toInstant());
  }

  static void checkVersion(long actual, long expected) {
    if (actual != expected) throw conflict("The queue changed; refresh before modifying it");
  }

  static void validateOrder(List<UUID> actual, List<UUID> requested) {
    if (requested.size() != new HashSet<>(requested).size()
        || !new HashSet<>(actual).equals(new HashSet<>(requested)))
      throw new BusinessRuleException(
          "INVALID_QUEUE_ORDER", "Include each calendar visit exactly once");
  }

  private static ConflictException conflict(String message) {
    return new ConflictException("QUEUE_CONFLICT", message);
  }

  private static AccessDeniedException denied(String code) {
    return new AccessDeniedException(code);
  }

  private record Queue(UUID id, UUID org, UUID professional, LocalDate day, long version) {}

  private record Target(UUID professional, Instant instant, String status) {}
}
