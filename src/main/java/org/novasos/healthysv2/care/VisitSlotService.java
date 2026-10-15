package org.novasos.healthysv2.care;

import java.time.*;
import java.util.UUID;
import org.novasos.healthysv2.shared.api.error.BusinessRuleException;
import org.novasos.healthysv2.shared.api.error.ConflictException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;

/** Caller holds the organization and physician locks until the visit has been persisted. */
@Component
class VisitSlotService {
  private final JdbcTemplate jdbc;
  private final VisitSchedulingPolicy policy;

  VisitSlotService(JdbcTemplate jdbc, VisitSchedulingPolicy policy) {
    this.jdbc = jdbc;
    this.policy = policy;
  }

  int requireSlot(
      UUID professional, UUID organization, UUID speciality, Instant start, UUID excludedVisit) {
    ZoneId zone =
        ZoneId.of(
            jdbc.queryForObject(
                "select timezone from organization.organization where id=?",
                String.class,
                organization));
    LocalDate date = start.atZone(zone).toLocalDate();
    jdbc.queryForObject(
        "select id from professional.professional where id=? for update", UUID.class, professional);
    requireDoctor(professional, organization, speciality, date);
    Integer closed =
        jdbc.queryForObject(
            "select count(*) from care.physician_queue where organization_id=? and"
                + " professional_id=? and local_date=? and closed",
            Integer.class,
            organization,
            professional,
            date);
    if (closed != null && closed > 0)
      throw invalid("QUEUE_CLOSED", "The clinician calendar is closed for this date");
    int duration = resolveDuration(professional, organization, start, zone);
    Instant end = start.plusSeconds(duration * 60L);
    Integer closedWindow =
        jdbc.queryForObject(
            "select count(*) from care.closed_calendar_window where organization_id=? and"
                + " professional_id=? and starts_at<? and ends_at>?",
            Integer.class,
            organization,
            professional,
            java.sql.Timestamp.from(end),
            java.sql.Timestamp.from(start));
    if (closedWindow != null && closedWindow > 0)
      throw invalid("CALENDAR_WINDOW_CLOSED", "Clinician calendar is closed for this interval");
    requireUnblocked(professional, organization, start, end);
    requireNoOverlap(professional, start, end, excludedVisit);
    return duration;
  }

  private void requireDoctor(
      UUID professional, UUID organization, UUID speciality, LocalDate date) {
    Integer count =
        jdbc.queryForObject(
            """
            select count(*) from professional.professional p
            join identity.person person on person.id=p.person_id and person.status='ACTIVE'
            where p.id=? and p.status='ACTIVE' and p.professional_type in ('DOCTOR','MEDECIN','NURSE')
            and exists(select 1 from licensing.member m where m.person_id=p.person_id
                and m.organization_id=? and m.status='ACTIVE' and ((m.role='DOCTOR' and p.professional_type in ('DOCTOR','MEDECIN')) or (m.role='NURSE' and p.professional_type='NURSE')))
            and exists(select 1 from professional.professional_assignment a where a.professional_id=p.id
                and a.organization_id=? and a.status='ACTIVE' and a.start_date<=?
                and (a.end_date is null or a.end_date>=?))
            and exists(select 1 from professional.professional_speciality s
                join catalog.speciality_catalog c on c.id=s.speciality_catalog_id and c.active
                where s.professional_id=p.id and s.speciality_catalog_id=?)
            """,
            Integer.class,
            professional,
            organization,
            organization,
            date,
            date,
            speciality);
    if (count == null || count == 0)
      throw invalid(
          "DOCTOR_NOT_AVAILABLE",
          "Doctor is not assigned to this establishment, date and specialty");
  }

  private int resolveDuration(UUID professional, UUID organization, Instant start, ZoneId zone) {
    ZonedDateTime local = start.atZone(zone);
    var schedules =
        jdbc.query(
            """
            select s.start_time,s.end_time,s.slot_duration_minutes
            from professional.professional_schedule s
            join professional.professional_assignment a on a.id=s.assignment_id
            where a.professional_id=? and a.organization_id=? and a.status='ACTIVE'
                and a.start_date<=? and (a.end_date is null or a.end_date>=?) and s.day_of_week=?
            order by s.start_time,s.id
            """,
            (r, n) ->
                new Window(
                    r.getObject(1, LocalTime.class), r.getObject(2, LocalTime.class), r.getInt(3)),
            professional,
            organization,
            local.toLocalDate(),
            local.toLocalDate(),
            local.getDayOfWeek().getValue());
    for (var window : schedules) {
      if (window.minutes() < 1 || window.minutes() > 240) continue;
      long offset = Duration.between(window.start(), local.toLocalTime()).getSeconds();
      Instant finish = start.plusSeconds(window.minutes() * 60L);
      if (offset >= 0
          && offset % (window.minutes() * 60L) == 0
          && finish.atZone(zone).toLocalDate().equals(local.toLocalDate())
          && !finish.atZone(zone).toLocalTime().isAfter(window.end())) return window.minutes();
    }
    Instant end = start.plusSeconds(policy.defaultSlotMinutes() * 60L);
    Integer available =
        jdbc.queryForObject(
            """
            select count(*) from professional.professional_availability pa
            join professional.professional_assignment a on a.id=pa.assignment_id
            where a.professional_id=? and a.organization_id=? and a.status='ACTIVE'
                and a.start_date<=? and (a.end_date is null or a.end_date>=?)
                and pa.status='AVAILABLE' and pa.availability_type not in ('LEAVE','ABSENCE','UNAVAILABLE','BLOCKED')
                and pa.start_at<=? and pa.end_at>=?
            """,
            Integer.class,
            professional,
            organization,
            local.toLocalDate(),
            local.toLocalDate(),
            java.sql.Timestamp.from(start),
            java.sql.Timestamp.from(end));
    if (available == null || available == 0)
      throw invalid("OUTSIDE_AVAILABILITY", "No matching recurring slot or explicit availability");
    return policy.defaultSlotMinutes();
  }

  private void requireUnblocked(UUID professional, UUID organization, Instant start, Instant end) {
    Integer blocked =
        jdbc.queryForObject(
            """
            select count(*) from professional.professional_availability pa
            join professional.professional_assignment a on a.id=pa.assignment_id
            where a.professional_id=? and a.organization_id=? and a.status='ACTIVE'
                and (pa.status='UNAVAILABLE' or pa.availability_type in ('LEAVE','ABSENCE','UNAVAILABLE','BLOCKED'))
                and pa.start_at<? and pa.end_at>?
            """,
            Integer.class,
            professional,
            organization,
            java.sql.Timestamp.from(end),
            java.sql.Timestamp.from(start));
    if (blocked != null && blocked > 0)
      throw invalid("DOCTOR_UNAVAILABLE", "Doctor has an absence or blocked interval");
  }

  void requireNoOverlap(UUID professional, Instant start, Instant end, UUID excludedVisit) {
    Integer care =
        jdbc.queryForObject(
            """
            select count(*) from care.care_visit v where professional_id=?
                and not exists(select 1 from care.reassignment_request reassignment where reassignment.visit_id=v.id and reassignment.status='PENDING')
                and status in ('PENDING_PAYMENT','AWAITING_SCHEDULING','SCHEDULED','IN_PROGRESS') and id<>?
                and scheduled_at<? and scheduled_at + slot_minutes * interval '1 minute'>?
            """,
            Integer.class,
            professional,
            excludedVisit,
            java.sql.Timestamp.from(end),
            java.sql.Timestamp.from(start));
    Integer legacy =
        jdbc.queryForObject(
            """
            select count(*) from appointment.appointment where professional_id=?
                and status in ('SCHEDULED','CONFIRMED','RESCHEDULED','CHECKED_IN','IN_PROGRESS')
                and scheduled_start<? and scheduled_end>?
            """,
            Integer.class,
            professional,
            java.sql.Timestamp.from(end),
            java.sql.Timestamp.from(start));
    if ((care != null && care > 0) || (legacy != null && legacy > 0)) {
      throw new ConflictException(
          "VISIT_SLOT_ALREADY_BOOKED", "The physician already has an overlapping visit");
    }
  }

  private BusinessRuleException invalid(String code, String message) {
    return new BusinessRuleException(code, message);
  }

  private record Window(LocalTime start, LocalTime end, int minutes) {}
}
