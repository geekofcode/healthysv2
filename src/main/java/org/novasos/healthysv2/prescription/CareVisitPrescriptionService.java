package org.novasos.healthysv2.prescription;

import static org.novasos.healthysv2.prescription.api.CareVisitPrescriptionDtos.*;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.time.Instant;
import java.util.List;
import java.util.UUID;
import org.novasos.healthysv2.care.api.VisitReportReader;
import org.novasos.healthysv2.licensing.api.LicensingAccess;
import org.novasos.healthysv2.patient.CurrentUserContext;
import org.novasos.healthysv2.patient.PatientAccessService;
import org.novasos.healthysv2.prescription.api.CareVisitPrescriptionReader;
import org.novasos.healthysv2.shared.api.error.*;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@Transactional
class CareVisitPrescriptionService implements CareVisitPrescriptionReader {
  private final JdbcTemplate jdbc;
  private final CurrentUserContext users;
  private final PatientAccessService access;
  private final LicensingAccess licensing;
  private final VisitReportReader reports;
  private final ObjectMapper json;
  private org.novasos.healthysv2.organizationaccess.api.OrganizationPermissionAccess permissions;

  @org.springframework.beans.factory.annotation.Autowired
  void configurePermissions(
      org.novasos.healthysv2.organizationaccess.api.OrganizationPermissionAccess permissions) {
    this.permissions = permissions;
  }

  CareVisitPrescriptionService(
      JdbcTemplate jdbc,
      CurrentUserContext users,
      PatientAccessService access,
      LicensingAccess licensing,
      VisitReportReader reports,
      ObjectMapper json) {
    this.jdbc = jdbc;
    this.users = users;
    this.access = access;
    this.licensing = licensing;
    this.reports = reports;
    this.json = json;
  }

  @Transactional(readOnly = true)
  VisitPrescriptionResponse read(UUID visitId) {
    var visit = visit(visitId, false);
    access.requireAccess(visit.patientId(), "PRESCRIPTIONS", "READ");
    requireReadContext(visit);
    var existing = load(visitId);
    var user = users.current();
    if (user.has("PATIENT") && !clinicalReader(visit, user)) {
      if (existing == null || existing.currentRevision() == 0)
        throw new ResourceNotFoundException("ValidatedPrescription", visitId);
      return jdbc.query(
          "select * from care.visit_prescription_revision where visit_id=? and revision=?",
          rs -> {
            if (!rs.next()) throw new ResourceNotFoundException("ValidatedPrescription", visitId);
            return new VisitPrescriptionResponse(
                visitId,
                existing.version(),
                existing.currentRevision(),
                "VALIDATED",
                decode(rs.getString("items")),
                rs.getString("correction_reason"),
                rs.getTimestamp("validated_at").toInstant());
          },
          visitId,
          existing.currentRevision());
    }
    return existing == null
        ? new VisitPrescriptionResponse(visitId, 0, 0, "DRAFT", List.of(), null, null)
        : existing;
  }

  @Transactional(readOnly = true)
  List<Revision> revisions(UUID visitId) {
    read(visitId);
    return jdbc.query(
        "select * from care.visit_prescription_revision where visit_id=? order by revision desc",
        (rs, n) ->
            new Revision(
                rs.getObject("id", UUID.class),
                rs.getInt("revision"),
                decode(rs.getString("items")),
                rs.getString("correction_reason"),
                rs.getTimestamp("validated_at").toInstant(),
                rs.getObject("validated_by", UUID.class)),
        visitId);
  }

  VisitPrescriptionResponse save(UUID visitId, SaveRequest request) {
    lockOrganizationForWrite();
    var visit = visit(visitId, true);
    var actor = writer(visit);
    ensureRow(visit);
    var current = load(visitId);
    checkVersion(current, request.expectedVersion());
    if (!"DRAFT".equals(current.status()))
      throw new ConflictException(
          "PRESCRIPTION_CONFLICT", "Validated prescriptions require a traced correction");
    validateItems(request.items(), false);
    // A reason cannot be changed after opening the correction.
    if (request.correctionReason() != null
        && !java.util.Objects.equals(request.correctionReason(), current.correctionReason()))
      throw new ConflictException(
          "PRESCRIPTION_CONFLICT", "Use the correction endpoint to record the correction reason");
    jdbc.update(
        "update care.visit_prescription set items=?::jsonb,version=version+1,updated_at=now() where"
            + " visit_id=?",
        encode(request.items()),
        visitId);
    audit(visitId, actor, "PRESCRIPTION_DRAFT", current.currentRevision());
    return load(visitId);
  }

  VisitPrescriptionResponse finalizePrescription(UUID visitId, long expectedVersion) {
    lockOrganizationForWrite();
    var visit = visit(visitId, true);
    var actor = writer(visit);
    ensureRow(visit);
    var current = load(visitId);
    checkVersion(current, expectedVersion);
    if (!"DRAFT".equals(current.status()))
      throw new ConflictException("PRESCRIPTION_CONFLICT", "Prescription already validated");
    validateItems(current.items(), true);
    reports.validatedReport(visitId);
    UUID previous =
        current.currentRevision() == 0 ? null : revisionId(visitId, current.currentRevision());
    int next = current.currentRevision() + 1;
    Instant now = Instant.now();
    jdbc.update(
        """
        insert into care.visit_prescription_revision(id,visit_id,revision,items,correction_reason,validated_at,validated_by,previous_revision_id,retention_expires_at)
        values (?,?,?,?::jsonb,?,?,?,?,?)
        """,
        UUID.randomUUID(),
        visitId,
        next,
        encode(current.items()),
        current.correctionReason(),
        now,
        actor,
        previous,
        visit.retention());
    jdbc.update(
        "update care.visit_prescription set"
            + " current_revision=?,status='VALIDATED',validated_at=?,updated_at=?,version=version+1"
            + " where visit_id=?",
        next,
        now,
        now,
        visitId);
    audit(visitId, actor, "PRESCRIPTION_VALIDATED", next);
    return load(visitId);
  }

  VisitPrescriptionResponse correction(UUID visitId, CorrectionRequest request) {
    lockOrganizationForWrite();
    var visit = visit(visitId, true);
    var actor = writer(visit);
    var current = load(visitId);
    if (current == null)
      throw new ConflictException("PRESCRIPTION_CONFLICT", "A validated prescription is required");
    checkVersion(current, request.expectedVersion());
    if (!"VALIDATED".equals(current.status()))
      throw new ConflictException("PRESCRIPTION_CONFLICT", "A validated prescription is required");
    if (request.reason() == null || request.reason().isBlank() || request.reason().length() > 2000)
      throw new BusinessRuleException(
          "CORRECTION_REASON_REQUIRED", "A correction reason is required");
    jdbc.update(
        "update care.visit_prescription set"
            + " status='DRAFT',correction_reason=?,validated_at=null,version=version+1,updated_at=now()"
            + " where visit_id=?",
        request.reason().trim(),
        visitId);
    audit(visitId, actor, "PRESCRIPTION_CORRECTION_OPENED", current.currentRevision());
    return load(visitId);
  }

  @Override
  @Transactional(readOnly = true)
  public ValidatedPrescription validatedPrescription(UUID visitId) {
    var current = read(visitId);
    if (!"VALIDATED".equals(current.status()))
      throw new ConflictException(
          "PRESCRIPTION_CONFLICT", "Prescription must be validated before PDF generation");
    return new ValidatedPrescription(
        visitId,
        revisionId(visitId, current.currentRevision()),
        current.currentRevision(),
        current.validatedAt(),
        current.items());
  }

  private UUID revisionId(UUID visitId, int revision) {
    return jdbc.queryForObject(
        "select id from care.visit_prescription_revision where visit_id=? and revision=?",
        UUID.class,
        visitId,
        revision);
  }

  private void ensureRow(Visit visit) {
    jdbc.update(
        "insert into care.visit_prescription(visit_id,retention_expires_at) values (?,?) on"
            + " conflict(visit_id) do nothing",
        visit.id(),
        visit.retention());
  }

  private VisitPrescriptionResponse load(UUID visitId) {
    return jdbc.query(
        "select * from care.visit_prescription where visit_id=?",
        rs ->
            rs.next()
                ? new VisitPrescriptionResponse(
                    visitId,
                    rs.getLong("version"),
                    rs.getInt("current_revision"),
                    rs.getString("status"),
                    decode(rs.getString("items")),
                    rs.getString("correction_reason"),
                    rs.getTimestamp("validated_at") == null
                        ? null
                        : rs.getTimestamp("validated_at").toInstant())
                : null,
        visitId);
  }

  private Visit visit(UUID id, boolean lock) {
    var rows =
        jdbc.query(
            """
            select v.id,e.patient_id,e.organization_id,p.person_id,v.status,v.retention_expires_at
            from care.care_visit v join care.care_episode e on e.id=v.episode_id
            join professional.professional p on p.id=v.professional_id where v.id=?
            """
                + (lock ? " for update of v" : ""),
            (rs, n) ->
                new Visit(
                    rs.getObject(1, UUID.class),
                    rs.getObject(2, UUID.class),
                    rs.getObject(3, UUID.class),
                    rs.getObject(4, UUID.class),
                    rs.getString(5),
                    rs.getTimestamp(6).toInstant()),
            id);
    if (rows.isEmpty()) throw new ResourceNotFoundException("CareVisit", id);
    return rows.getFirst();
  }

  private void lockOrganizationForWrite() {
    var user = users.current();
    if (!user.has("DOCTOR") || user.personId() == null || user.organizationId() == null)
      throw new AccessDeniedException("Selected establishment doctor context required");
    jdbc.queryForObject(
        "select id from organization.organization where id=? for update",
        UUID.class,
        user.organizationId());
    permissions.require(user.organizationId(), "prescriptions", "UPDATE");
  }

  private UUID writer(Visit visit) {
    var user = users.current();
    if (!user.has("DOCTOR")
        || user.personId() == null
        || !user.personId().equals(visit.doctorPerson())
        || !visit.organization().equals(user.organizationId()))
      throw new AccessDeniedException(
          "Only the assigned establishment doctor may edit this prescription");
    licensing.requireMember(visit.organization());
    licensing.requireActive(visit.organization());
    if (!List.of("IN_PROGRESS", "COMPLETED").contains(visit.status()))
      throw new ConflictException(
          "PRESCRIPTION_CONFLICT", "Start the medical encounter before prescribing");
    Integer eligible =
        jdbc.queryForObject(
            """
            select count(*) from professional.professional p where p.person_id=? and p.status='ACTIVE'
              and p.professional_type in ('DOCTOR','MEDECIN')
              and exists(select 1 from professional.professional_assignment a where a.professional_id=p.id
                and a.organization_id=? and a.status='ACTIVE' and a.start_date<=current_date and (a.end_date is null or a.end_date>=current_date))
              and exists(select 1 from licensing.member m where m.person_id=p.person_id and m.organization_id=? and m.status='ACTIVE' and m.role='DOCTOR')
            """,
            Integer.class,
            user.personId(),
            visit.organization(),
            visit.organization());
    if (eligible == null || eligible == 0)
      throw new AccessDeniedException("Active doctor assignment required");
    access.requireAccess(visit.patientId(), "PRESCRIPTIONS", "WRITE");
    return user.personId();
  }

  private boolean clinicalReader(Visit visit, CurrentUserContext.UserContext user) {
    if (user.personId() == null
        || user.organizationId() == null
        || !visit.organization().equals(user.organizationId())
        || !user.hasAny("DOCTOR", "NURSE")) return false;
    Integer active =
        jdbc.queryForObject(
            """
            select count(*) from licensing.member m
            join professional.professional p on p.person_id=m.person_id and p.status='ACTIVE'
            join identity.person person on person.id=m.person_id and person.status='ACTIVE'
            where m.organization_id=? and m.person_id=? and m.status='ACTIVE'
              and ((m.role='DOCTOR' and p.professional_type in ('DOCTOR','MEDECIN') and ?)
                or (m.role='NURSE' and p.professional_type='NURSE' and ?))
              and exists(select 1 from professional.professional_assignment a where a.professional_id=p.id
                and a.organization_id=m.organization_id and a.status='ACTIVE' and a.start_date<=current_date
                and (a.end_date is null or a.end_date>=current_date))
            """,
            Integer.class,
            user.organizationId(),
            user.personId(),
            user.has("DOCTOR"),
            user.has("NURSE"));
    if (active == null || active == 0) return false;
    try {
      licensing.requireMember(visit.organization());
      licensing.requireActive(visit.organization());
      return true;
    } catch (AccessDeniedException denied) {
      return false;
    }
  }

  private void requireReadContext(Visit visit) {
    var user = users.current();
    if (user.organizationId() != null && !user.organizationId().equals(visit.organization()))
      throw new AccessDeniedException("Visit belongs to another establishment");
  }

  static void checkVersion(VisitPrescriptionResponse current, long expected) {
    if (current.version() != expected)
      throw new ConflictException(
          "PRESCRIPTION_CONFLICT", "Prescription changed; reload before editing");
  }

  static void validateItems(List<Item> items, boolean finalized) {
    if (items == null || items.size() > 100 || (finalized && items.isEmpty()))
      throw new BusinessRuleException(
          "INVALID_PRESCRIPTION_ITEMS", "Provide between 1 and 100 medication lines to finalize");
    for (var i : items) {
      if (i == null
          || !text(i.medication(), 500)
          || !text(i.dosage(), 500)
          || !text(i.route(), 150)
          || !text(i.frequency(), 300)
          || !text(i.duration(), 300)
          || i.instructions() != null && i.instructions().length() > 2000)
        throw new BusinessRuleException(
            "INVALID_PRESCRIPTION_ITEM",
            "Medication, dosage, route, frequency and duration are required");
    }
  }

  private static boolean text(String value, int limit) {
    return value != null && !value.isBlank() && value.length() <= limit;
  }

  private List<Item> decode(String value) {
    try {
      return json.readValue(value, new TypeReference<List<Item>>() {});
    } catch (JsonProcessingException e) {
      throw new IllegalStateException("Invalid stored prescription", e);
    }
  }

  private String encode(Object value) {
    try {
      return json.writeValueAsString(value);
    } catch (JsonProcessingException e) {
      throw new IllegalStateException("Cannot encode prescription", e);
    }
  }

  private void audit(UUID visitId, UUID actor, String action, int revision) {
    jdbc.update(
        "insert into audit.audit_log(actor_person_id,module,entity_type,entity_id,action,new_value)"
            + " values (?,'PRESCRIPTION','CareVisitPrescription',?,?,?::jsonb)",
        actor,
        visitId,
        action,
        "{\"revision\":" + revision + "}");
  }

  private record Visit(
      UUID id,
      UUID patientId,
      UUID organization,
      UUID doctorPerson,
      String status,
      Instant retention) {}
}
