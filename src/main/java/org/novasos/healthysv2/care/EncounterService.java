package org.novasos.healthysv2.care;

import java.sql.Timestamp;
import java.time.*;
import java.util.*;
import org.novasos.healthysv2.audit.AuditTrail;
import org.novasos.healthysv2.care.api.VisitReportReader;
import org.novasos.healthysv2.licensing.api.LicensingAccess;
import org.novasos.healthysv2.patient.CurrentUserContext;
import org.novasos.healthysv2.patient.PatientAccessService;
import org.novasos.healthysv2.shared.api.error.*;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@Transactional
public class EncounterService implements VisitReportReader {
  private final CareVisitRepository visits;
  private final CurrentUserContext users;
  private final LicensingAccess licensing;
  private final PatientAccessService access;
  private final JdbcTemplate jdbc;
  private final AuditTrail audit;
  private final CarePolicy policy;
  private final Duration inProgressDuration;
  private AiJobService aiJobs;
  private org.novasos.healthysv2.organizationaccess.api.OrganizationPermissionAccess permissions;

  @org.springframework.beans.factory.annotation.Autowired
  void configurePermissions(
      org.novasos.healthysv2.organizationaccess.api.OrganizationPermissionAccess permissions) {
    this.permissions = permissions;
  }

  @org.springframework.beans.factory.annotation.Autowired
  void setAiJobs(AiJobService aiJobs) {
    this.aiJobs = aiJobs;
  }

  EncounterService(
      CareVisitRepository visits,
      CurrentUserContext users,
      LicensingAccess licensing,
      PatientAccessService access,
      JdbcTemplate jdbc,
      AuditTrail audit,
      CarePolicy policy,
      @org.springframework.beans.factory.annotation.Value(
              "${healthys.access.in-progress-duration:PT4H}")
          Duration inProgressDuration) {
    this.visits = visits;
    this.users = users;
    this.licensing = licensing;
    this.access = access;
    this.jdbc = jdbc;
    this.audit = audit;
    this.policy = policy;
    if (inProgressDuration == null
        || inProgressDuration.isNegative()
        || inProgressDuration.isZero())
      throw new IllegalArgumentException("In-progress access duration must be positive");
    this.inProgressDuration = inProgressDuration;
  }

  public EncounterDtos.Started start(UUID id, long version) {
    var visit = lockedDoctorVisit(id);
    permissions.require(visit.episode().getOrganizationId(), "visits", "UPDATE");
    checkVersion(visit.getVersion(), version);
    Instant now = Instant.now();
    String timezone =
        jdbc.queryForObject(
            "select timezone from organization.organization where id=?",
            String.class,
            visit.episode().getOrganizationId());
    ensureStartDay(visit.getScheduledAt(), now, ZoneId.of(timezone));
    access.requireAccess(visit.episode().getPatientId(), "MEDICAL_RECORD", "WRITE");
    try {
      visit.start(visit.episode().getOrganizationId(), now);
    } catch (IllegalStateException | IllegalArgumentException e) {
      throw rule("VISIT_NOT_STARTABLE", e.getMessage());
    }
    visits.flush();
    audit.change(
        users.current().personId(),
        visit.episode().getOrganizationId(),
        "CARE",
        "CareVisit",
        id,
        "START",
        null,
        Map.of("episodeId", visit.episode().getId(), "startedAt", now));
    var episode = visit.episode();
    return new EncounterDtos.Started(
        id,
        episode.getId(),
        visit.getStatus().name(),
        visit.getVersion(),
        visit.getStartedAt(),
        episode.getStartedAt(),
        episode.getExpiresAt());
  }

  @Transactional(readOnly = true)
  public EncounterDtos.Report read(UUID id) {
    requireClinicalReader();
    var visit = readableVisit(id);
    return response(visit, state(id));
  }

  public EncounterDtos.Report save(UUID id, EncounterDtos.Draft request) {
    var visit = lockedDoctorVisit(id);
    requireClinicalWrite(visit);
    var draft = state(id);
    checkVersion(draft.version(), request.version());
    if (draft.status().equals("VALIDATED"))
      throw rule("REPORT_VALIDATED", "Open a traced correction before editing a validated report");
    UUID provenance = draft.aiJobId();
    if (request.aiJobId() != null && !request.aiJobId().equals(provenance)) {
      provenance = aiJobs.requireReportProvenance(id, request.aiJobId());
    }
    Instant now = Instant.now();
    if (draft.persisted()) {
      jdbc.update(
          """
          update care.visit_report set version=version+1,reason=?,observations=?,diagnosis=?,treatment=?,follow_up=?,updated_at=?,updated_by=?
          where visit_id=?
          """,
          request.reason().trim(),
          text(request.observations()),
          text(request.diagnosis()),
          text(request.treatment()),
          text(request.followUp()),
          Timestamp.from(now),
          users.current().personId(),
          id);
    } else {
      jdbc.update(
          """
          insert into care.visit_report(visit_id,version,reason,observations,diagnosis,treatment,follow_up,updated_at,updated_by,retention_expires_at)
          values(?,1,?,?,?,?,?,?,?,?)
          """,
          id,
          request.reason().trim(),
          text(request.observations()),
          text(request.diagnosis()),
          text(request.treatment()),
          text(request.followUp()),
          Timestamp.from(now),
          users.current().personId(),
          retention(now));
    }
    if (provenance != null)
      jdbc.update("update care.visit_report set ai_job_id=? where visit_id=?", provenance, id);
    auditReport(visit, "SAVE_DRAFT", draft.revision());
    return response(visit, state(id));
  }

  public EncounterDtos.Report finalizeReport(UUID id, long version) {
    var visit = lockedDoctorVisit(id);
    requireClinicalWrite(visit);
    var draft = state(id);
    checkVersion(draft.version(), version);
    requireFinalizable(draft);
    Instant now = Instant.now();
    UUID previous =
        jdbc.query(
            "select id from care.visit_report_revision where visit_id=? order by revision desc"
                + " limit 1",
            rs -> rs.next() ? rs.getObject(1, UUID.class) : null,
            id);
    jdbc.update(
        """
        insert into care.visit_report_revision(id,visit_id,revision,reason,observations,diagnosis,treatment,follow_up,
            validated_at,validated_by,correction_reason,previous_revision_id,retention_expires_at,ai_job_id,patient_visible)
        values(?,?,?,?,?,?,?,?,?,?,?,?,?,?,?)
        """,
        UUID.randomUUID(),
        id,
        draft.revision(),
        draft.reason(),
        draft.observations(),
        draft.diagnosis(),
        draft.treatment(),
        draft.followUp(),
        Timestamp.from(now),
        users.current().personId(),
        draft.correctionReason(),
        previous,
        retention(now),
        draft.aiJobId(),
        draft.patientVisible());
    jdbc.update(
        "update care.visit_report set"
            + " status='VALIDATED',version=version+1,updated_at=?,updated_by=? where visit_id=?",
        Timestamp.from(now),
        users.current().personId(),
        id);
    if (visit.getStatus() == CareVisit.Status.IN_PROGRESS) {
      visit.complete(now);
      visits.flush();
    }
    auditReport(visit, "VALIDATE_REPORT", draft.revision());
    return response(visit, state(id));
  }

  public EncounterDtos.Report correct(UUID id, EncounterDtos.Correction request) {
    var visit = lockedDoctorVisit(id);
    requireClinicalWrite(visit);
    var draft = state(id);
    checkVersion(draft.version(), request.version());
    if (!draft.status().equals("VALIDATED"))
      throw rule("REPORT_NOT_VALIDATED", "Only a validated report can be corrected");
    jdbc.update(
        """
        update care.visit_report set status='DRAFT',current_revision=current_revision+1,version=version+1,
            correction_reason=?,updated_at=now(),updated_by=? where visit_id=?
        """,
        request.correctionReason().trim(),
        users.current().personId(),
        id);
    auditReport(visit, "OPEN_CORRECTION", draft.revision() + 1);
    return response(visit, state(id));
  }

  @Override
  @Transactional(readOnly = true)
  public ValidatedVisitReport validatedReport(UUID id) {
    var visit = readableVisit(id);
    var report =
        revisions(visit).stream()
            .reduce((a, b) -> b)
            .orElseThrow(() -> rule("REPORT_NOT_VALIDATED", "A validated report is required"));
    var user = users.current();
    if (user.isPatient(visit.episode().getPatientId()) && !report.patientVisible())
      throw new AccessDeniedException("REPORT_NOT_PATIENT_VISIBLE");
    return report;
  }

  public record HistoryReport(
      UUID visitId,
      UUID episodeId,
      UUID organizationId,
      String organizationName,
      String doctorName,
      int revision,
      Instant validatedAt,
      String reason,
      String observations,
      String diagnosis,
      String treatment,
      String followUp,
      boolean historical) {}

  @Transactional(readOnly = true)
  public List<HistoryReport> history(UUID patientId) {
    var user = users.current();
    boolean self = user.isPatient(patientId);
    if (!self) requireClinicalReader();
    access.requireAccess(patientId, "MEDICAL_RECORD", "READ");
    return jdbc.query(
        """
        select r.*, v.episode_id, e.organization_id, o.name organization_name,
            concat(p.first_name,' ',p.last_name) doctor_name,
            exists(select 1 from care.migration_mapping m
                where m.target_table='care.visit_report_revision' and m.target_id=r.id) historical
        from care.visit_report_revision r
        join care.care_visit v on v.id=r.visit_id
        join care.care_episode e on e.id=v.episode_id
        join organization.organization o on o.id=e.organization_id
        join identity.person p on p.id=r.validated_by
        where e.patient_id=? and (?=false or r.patient_visible=true)
            and not exists(select 1 from care.visit_report_revision newer
                where newer.visit_id=r.visit_id and newer.revision>r.revision)
        order by r.validated_at desc, r.id limit 100
        """,
        (rs, n) ->
            new HistoryReport(
                rs.getObject("visit_id", UUID.class),
                rs.getObject("episode_id", UUID.class),
                rs.getObject("organization_id", UUID.class),
                rs.getString("organization_name"),
                rs.getString("doctor_name"),
                rs.getInt("revision"),
                rs.getTimestamp("validated_at").toInstant(),
                rs.getString("reason"),
                rs.getString("observations"),
                rs.getString("diagnosis"),
                rs.getString("treatment"),
                rs.getString("follow_up"),
                rs.getBoolean("historical")),
        patientId,
        self);
  }

  private CareVisit lockedDoctorVisit(UUID id) {
    var user = users.current();
    if (user.personId() == null || user.organizationId() == null || !user.has("DOCTOR"))
      throw new AccessDeniedException("ASSIGNED_DOCTOR_REQUIRED");
    jdbc.queryForObject(
        "select id from organization.organization where id=? for update",
        UUID.class,
        user.organizationId());
    licensing.requireMember(user.organizationId());
    licensing.requireActive(user.organizationId());
    var visit =
        visits
            .findLockedById(id)
            .filter(v -> v.episode().getOrganizationId().equals(user.organizationId()))
            .orElseThrow(() -> new ResourceNotFoundException("CareVisit", id));
    jdbc.queryForObject(
        "select id from care.care_episode where id=? for update",
        UUID.class,
        visit.episode().getId());
    Integer active =
        jdbc.queryForObject(
            """
            select count(*) from professional.professional p join identity.person person on person.id=p.person_id
            where p.id=? and p.person_id=? and p.status='ACTIVE' and person.status='ACTIVE' and p.professional_type in ('DOCTOR','MEDECIN')
              and exists(select 1 from professional.professional_assignment a where a.professional_id=p.id and a.organization_id=?
                and a.status='ACTIVE' and a.start_date<=current_date and (a.end_date is null or a.end_date>=current_date))
              and exists(select 1 from licensing.member m where m.organization_id=? and m.person_id=p.person_id and m.role='DOCTOR' and m.status='ACTIVE')
            """,
            Integer.class,
            visit.getProfessionalId(),
            user.personId(),
            user.organizationId(),
            user.organizationId());
    if (active == null || active == 0) throw new AccessDeniedException("ASSIGNED_DOCTOR_REQUIRED");
    return visit;
  }

  /**
   * Draft reports belong to a licensed clinical workspace, never the patient self-access shortcut.
   */
  private void requireClinicalReader() {
    var user = users.current();
    if (user.personId() == null || user.organizationId() == null || !user.hasAny("DOCTOR", "NURSE"))
      throw new AccessDeniedException("CLINICAL_WORKSPACE_REQUIRED");
    licensing.requireMember(user.organizationId());
    licensing.requireActive(user.organizationId());
    Integer clinicians =
        jdbc.queryForObject(
            """
            select count(*) from professional.professional p
            join identity.person person on person.id=p.person_id and person.status='ACTIVE'
            join licensing.member m on m.person_id=p.person_id and m.organization_id=? and m.status='ACTIVE'
            where p.person_id=? and p.status='ACTIVE'
              and ((m.role='DOCTOR' and p.professional_type in ('DOCTOR','MEDECIN'))
                or (m.role='NURSE' and p.professional_type='NURSE'))
              and exists(select 1 from professional.professional_assignment a where a.professional_id=p.id
                and a.organization_id=? and a.status='ACTIVE' and a.start_date<=current_date
                and (a.end_date is null or a.end_date>=current_date))
            """,
            Integer.class,
            user.organizationId(),
            user.personId(),
            user.organizationId());
    if (clinicians == null || clinicians == 0)
      throw new AccessDeniedException("ACTIVE_CLINICAL_MEMBERSHIP_REQUIRED");
  }

  private CareVisit readableVisit(UUID id) {
    var user = users.current();
    var visit =
        visits.findById(id).orElseThrow(() -> new ResourceNotFoundException("CareVisit", id));
    if (user.organizationId() != null
        && !visit.episode().getOrganizationId().equals(user.organizationId()))
      throw new ResourceNotFoundException("CareVisit", id);
    access.requireAccess(visit.episode().getPatientId(), "MEDICAL_RECORD", "READ");
    return visit;
  }

  private void requireClinicalWrite(CareVisit visit) {
    permissions.require(visit.episode().getOrganizationId(), "reports", "UPDATE");
    if (visit.getStatus() != CareVisit.Status.IN_PROGRESS
        && visit.getStatus() != CareVisit.Status.COMPLETED)
      throw rule("VISIT_NOT_STARTED", "Start the encounter before writing a report");
    Instant now = Instant.now();
    if (visit.getStatus() == CareVisit.Status.IN_PROGRESS
        && !visit.episode().isActiveAt(now)
        && (visit.getStartedAt() == null
            || !now.isBefore(visit.getStartedAt().plus(inProgressDuration))))
      throw rule("EPISODE_COVERAGE_EXPIRED", "The encounter access period expired");
    // Completed report corrections may use a fresh patient consent; they never reopen coverage.
    access.requireAccess(visit.episode().getPatientId(), "MEDICAL_RECORD", "WRITE");
  }

  static void ensureStartDay(Instant scheduled, Instant now, ZoneId timezone) {
    if (now.atZone(timezone).toLocalDate().isBefore(scheduled.atZone(timezone).toLocalDate()))
      throw rule("VISIT_DATE_NOT_REACHED", "The encounter cannot start before its programmed day");
  }

  static void checkVersion(long current, long expected) {
    if (current != expected)
      throw new ConflictException(
          "ENCOUNTER_VERSION_CONFLICT", "Reload the encounter before modifying it");
  }

  private static void requireFinalizable(DraftState draft) {
    if (!draft.persisted()
        || !draft.status().equals("DRAFT")
        || draft.reason().isBlank()
        || draft.diagnosis().isBlank())
      throw rule("REPORT_INCOMPLETE", "Save a draft with reason and diagnosis before validation");
  }

  private DraftState state(UUID id) {
    return jdbc.query(
        "select * from care.visit_report where visit_id=?",
        rs ->
            rs.next()
                ? new DraftState(
                    true,
                    rs.getLong("version"),
                    rs.getInt("current_revision"),
                    rs.getString("status"),
                    rs.getString("reason"),
                    rs.getString("observations"),
                    rs.getString("diagnosis"),
                    rs.getString("treatment"),
                    rs.getString("follow_up"),
                    rs.getString("correction_reason"),
                    rs.getObject("ai_job_id", UUID.class),
                    rs.getBoolean("patient_visible"))
                : new DraftState(false, 0, 1, "DRAFT", "", "", "", "", "", null, null, true),
        id);
  }

  private EncounterDtos.Report response(CareVisit visit, DraftState draft) {
    var episode = visit.episode();
    return new EncounterDtos.Report(
        visit.getId(),
        episode.getId(),
        episode.getPatientId(),
        episode.getOrganizationId(),
        visit.getProfessionalId(),
        visit.getStatus().name(),
        visit.getVersion(),
        draft.version(),
        draft.revision(),
        draft.status(),
        draft.persisted() ? draft.reason() : visit.getReason(),
        draft.observations(),
        draft.diagnosis(),
        draft.treatment(),
        draft.followUp(),
        draft.correctionReason(),
        revisions(visit),
        draft.aiJobId());
  }

  private List<ValidatedVisitReport> revisions(CareVisit visit) {
    var episode = visit.episode();
    return jdbc.query(
        """
        select r.*,concat(p.first_name,' ',p.last_name) doctor_name from care.visit_report_revision r
        join identity.person p on p.id=r.validated_by where r.visit_id=? order by r.revision
        """,
        (rs, n) ->
            new ValidatedVisitReport(
                visit.getId(),
                episode.getId(),
                episode.getPatientId(),
                episode.getOrganizationId(),
                visit.getProfessionalId(),
                rs.getString("doctor_name"),
                rs.getInt("revision"),
                rs.getObject("id", UUID.class),
                rs.getTimestamp("validated_at").toInstant(),
                rs.getString("reason"),
                rs.getString("observations"),
                rs.getString("diagnosis"),
                rs.getString("treatment"),
                rs.getString("follow_up"),
                rs.getObject("ai_job_id", UUID.class),
                rs.getBoolean("patient_visible")),
        visit.getId());
  }

  private void auditReport(CareVisit visit, String action, int revision) {
    audit.change(
        users.current().personId(),
        visit.episode().getOrganizationId(),
        "CARE",
        "VisitReport",
        visit.getId(),
        action,
        null,
        Map.of("revision", revision));
  }

  private Timestamp retention(Instant now) {
    return Timestamp.from(
        now.atZone(ZoneOffset.UTC).plusYears(policy.retentionYears()).toInstant());
  }

  private static String text(String value) {
    return value == null ? "" : value.trim();
  }

  private static BusinessRuleException rule(String code, String message) {
    return new BusinessRuleException(code, message);
  }

  private record DraftState(
      boolean persisted,
      long version,
      int revision,
      String status,
      String reason,
      String observations,
      String diagnosis,
      String treatment,
      String followUp,
      String correctionReason,
      UUID aiJobId,
      boolean patientVisible) {}
}
