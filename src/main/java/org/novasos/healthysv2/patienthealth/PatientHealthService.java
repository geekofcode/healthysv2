package org.novasos.healthysv2.patienthealth;

import java.math.BigDecimal;
import java.sql.Timestamp;
import java.time.*;
import java.util.*;
import org.novasos.healthysv2.accessprofile.api.AccessProfileResolver;
import org.novasos.healthysv2.identity.api.*;
import org.novasos.healthysv2.organizationaccess.api.OrganizationPermissionAccess;
import org.novasos.healthysv2.patient.CurrentUserContext;
import org.novasos.healthysv2.patient.PatientAccessService;
import org.novasos.healthysv2.patienthealth.api.*;
import org.novasos.healthysv2.patienthealth.api.PatientHealthDtos.*;
import org.novasos.healthysv2.shared.api.error.*;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@Transactional
public class PatientHealthService implements PatientObservationAccess {
  private final JdbcTemplate db;
  private final CurrentUserContext users;
  private final AccessProfileResolver profiles;
  private final PatientAccessService access;
  private final OrganizationPermissionAccess permissions;
  private final SelectedPatientIdentityAccess identities;

  PatientHealthService(
      JdbcTemplate db,
      CurrentUserContext users,
      AccessProfileResolver profiles,
      PatientAccessService access,
      OrganizationPermissionAccess permissions,
      SelectedPatientIdentityAccess identities) {
    this.db = db;
    this.users = users;
    this.profiles = profiles;
    this.access = access;
    this.permissions = permissions;
    this.identities = identities;
  }

  UUID selectedPatient() {
    var u = users.current();
    if (!u.has("PATIENT") || u.personId() == null || u.profileId() == null || u.patientId() == null)
      throw denied("SELECTED_PATIENT_PROFILE_REQUIRED");
    var profile = profiles.forPerson(u.personId(), u.profileId());
    if (profile == null
        || !profile.active()
        || !Set.of("PATIENT", "FAMILY_PATIENT").contains(profile.sourceType())
        || !u.patientId().equals(profile.sourceId()))
      throw denied("SELECTED_PATIENT_PROFILE_REQUIRED");
    return profile.sourceId();
  }

  UUID lockSelectedPatient() {
    UUID patient = selectedPatient();
    db.queryForObject(
        "select id from identity.person where id=? and status='ACTIVE' for update",
        UUID.class,
        users.current().personId());
    return selectedPatient();
  }

  public Parameter createSelfParameter(ParameterInput input) {
    return createParameter(lockSelectedPatient(), input);
  }

  public Parameter createCareParameter(UUID visit, ParameterInput input) {
    var c = visitContext(visit, true);
    return createParameter(c.patient(), input);
  }

  private AccessDeniedException denied(String code) {
    return new AccessDeniedException(code);
  }

  private CurrentUserContext.UserContext operator(String resource, String action) {
    var u = users.current();
    if (u.personId() == null
        || u.profileId() == null
        || u.organizationId() == null
        || u.has("PLATFORM_ADMIN")) throw denied("ORGANIZATION_PROFILE_REQUIRED");
    permissions.require(u.organizationId(), resource, action);
    return u;
  }

  private CurrentUserContext.UserContext clinician(String resource, String action) {
    var u = users.current();
    if (!u.hasAny("DOCTOR", "NURSE")) throw denied("CLINICAL_PROFILE_REQUIRED");
    return operator(resource, action);
  }

  void clinicalRead(UUID patient) {
    var u = clinician("clinicalrecords", "READ");
    access.requireAccess(patient, "MEDICAL_RECORD", "READ");
  }

  @Transactional(readOnly = true)
  public List<Parameter> parameters(UUID patient) {
    return db.query(
        "select * from patienthealth.parameter where active and (patient_id is null or"
            + " patient_id=?) order by patient_id nulls first,label",
        (rs, n) ->
            new Parameter(
                (UUID) rs.getObject("id"),
                rs.getString("code"),
                rs.getString("label"),
                rs.getString("value_type"),
                rs.getString("unit"),
                rs.getString("loinc_code"),
                rs.getObject("patient_id") != null,
                rs.getBoolean("active")),
        patient);
  }

  public Parameter createParameter(UUID patient, ParameterInput input) {
    if (!Set.of("NUMERIC", "TEXT", "BOOLEAN").contains(input.valueType()))
      throw new IllegalArgumentException("INVALID_PARAMETER_TYPE");
    UUID id = UUID.randomUUID();
    String code = "CUSTOM_" + id;
    String unit = input.unit() == null || input.unit().isBlank() ? null : input.unit().trim();
    if (!"NUMERIC".equals(input.valueType()) && unit != null)
      throw new IllegalArgumentException("NON_NUMERIC_PARAMETER_UNIT");
    db.update(
        "insert into patienthealth.parameter(id,patient_id,code,label,value_type,unit,created_by)"
            + " values(?,?,?,?,?,?,?)",
        id,
        patient,
        code,
        input.label().trim(),
        input.valueType(),
        unit,
        users.current().personId());
    return new Parameter(id, code, input.label().trim(), input.valueType(), unit, null, true, true);
  }

  @Transactional(readOnly = true)
  public ObservationPage observations(UUID patient, UUID parameterId, int page, int size) {
    if (page < 0 || page > 100000 || size < 1 || size > 200)
      throw new IllegalArgumentException("INVALID_OBSERVATION_PAGE");
    String where = " o.patient_id=?" + (parameterId == null ? "" : " and o.parameter_id=?");
    var args = new ArrayList<Object>();
    args.add(patient);
    if (parameterId != null) args.add(parameterId);
    Long total =
        db.queryForObject(
            "select count(*) from patienthealth.observation o where" + where,
            Long.class,
            args.toArray());
    args.add(size);
    args.add(page * size);
    var items =
        db.query(
            observationSelect()
                + " where"
                + where
                + " order by o.measured_at desc,o.recorded_at desc,o.id limit ? offset ?",
            (rs, n) -> observation(rs),
            args.toArray());
    return new ObservationPage(items, total == null ? 0 : total, page, size);
  }

  private String observationSelect() {
    return "select o.*,org.name organization_name,concat_ws(' ',p.first_name,p.last_name)"
        + " actor_name from patienthealth.observation o join identity.person p on"
        + " p.id=o.actor_person_id left join organization.organization org on"
        + " org.id=o.organization_id";
  }

  private Observation observation(java.sql.ResultSet r) throws java.sql.SQLException {
    return new Observation(
        (UUID) r.getObject("id"),
        (UUID) r.getObject("parameter_id"),
        r.getString("code"),
        r.getString("label"),
        r.getString("value_type"),
        r.getString("value_text"),
        r.getString("unit"),
        r.getString("note"),
        r.getTimestamp("measured_at").toInstant(),
        r.getTimestamp("recorded_at").toInstant(),
        r.getBoolean("self_reported"),
        (UUID) r.getObject("organization_id"),
        r.getString("organization_name"),
        (UUID) r.getObject("visit_id"),
        r.getString("actor_name"),
        (UUID) r.getObject("supersedes_id"));
  }

  public Observation appendSelf(ObservationInput input) {
    UUID patient = lockSelectedPatient();
    return append(patient, null, null, users.current().personId(), true, input);
  }

  private Observation append(
      UUID patient, UUID org, UUID visit, UUID actor, boolean self, ObservationInput input) {
    if (input.measuredAt() == null || input.measuredAt().isAfter(Instant.now().plusSeconds(300)))
      throw new IllegalArgumentException("INVALID_MEASUREMENT_DATE");
    var definitions =
        db.queryForList(
            "select * from patienthealth.parameter where id=? and active and (patient_id is null or"
                + " patient_id=?)",
            input.parameterId(),
            patient);
    if (definitions.isEmpty())
      throw new ResourceNotFoundException("HealthParameter", input.parameterId());
    var p = definitions.getFirst();
    String value =
        validateValue((String) p.get("code"), (String) p.get("value_type"), input.value());
    if (input.supersedesId() != null) {
      Integer count =
          db.queryForObject(
              "select count(*) from patienthealth.observation where id=? and patient_id=? and"
                  + " parameter_id=? and self_reported=? and organization_id is not distinct from ?"
                  + " and visit_id is not distinct from ?",
              Integer.class,
              input.supersedesId(),
              patient,
              input.parameterId(),
              self,
              org,
              visit);
      if (count == null || count != 1)
        throw new IllegalArgumentException("INVALID_OBSERVATION_CORRECTION");
    }
    UUID id = UUID.randomUUID();
    db.update(
        "insert into"
            + " patienthealth.observation(id,patient_id,parameter_id,code,label,value_type,unit,value_text,note,measured_at,actor_person_id,self_reported,organization_id,visit_id,supersedes_id)"
            + " values(?,?,?,?,?,?,?,?,?,?,?,?,?,?,?)",
        id,
        patient,
        input.parameterId(),
        p.get("code"),
        p.get("label"),
        p.get("value_type"),
        p.get("unit"),
        value,
        input.note(),
        Timestamp.from(input.measuredAt()),
        actor,
        self,
        org,
        visit,
        input.supersedesId());
    audit(actor, org, patient, "OBSERVATION_APPENDED", id);
    return db.query(observationSelect() + " where o.id=?", (rs, n) -> observation(rs), id)
        .getFirst();
  }

  static String validateValue(String code, String type, String value) {
    if (value == null || value.isBlank() || value.length() > 2000)
      throw new IllegalArgumentException("HEALTH_VALUE_REQUIRED");
    String normalized = value.trim();
    if ("NUMERIC".equals(type)) {
      BigDecimal number;
      try {
        number = new BigDecimal(normalized);
      } catch (NumberFormatException e) {
        throw new IllegalArgumentException("NUMERIC_HEALTH_VALUE_REQUIRED");
      }
      if (number.abs().compareTo(new BigDecimal("1000000")) > 0
          || number.scale() > 6
          || number.precision() > 16)
        throw new IllegalArgumentException("HEALTH_VALUE_OUT_OF_BOUNDS");
      if ("oxygenSaturation".equals(code)
          && (number.signum() < 0 || number.compareTo(new BigDecimal("100")) > 0))
        throw new IllegalArgumentException("INVALID_OXYGEN_PERCENT");
      if ("painScore".equals(code) && (number.signum() < 0 || number.compareTo(BigDecimal.TEN) > 0))
        throw new IllegalArgumentException("INVALID_PAIN_SCORE");
      if (Set.of(
                  "glucoseMgDl",
                  "glucoseMmolL",
                  "waistCircumference",
                  "hipCircumference",
                  "peakFlow",
                  "heartRate",
                  "respiratoryRate",
                  "height",
                  "headCircumference",
                  "weight",
                  "bmi",
                  "systolicPressure",
                  "diastolicPressure")
              .contains(code)
          && number.signum() < 0) throw new IllegalArgumentException("NEGATIVE_VITAL_VALUE");
      return number.stripTrailingZeros().toPlainString();
    }
    if ("BOOLEAN".equals(type) && !Set.of("true", "false").contains(normalized))
      throw new IllegalArgumentException("BOOLEAN_HEALTH_VALUE_REQUIRED");
    return normalized;
  }

  private record VisitContext(UUID patient, UUID organization, UUID actor, boolean reception) {}

  private VisitContext visitContext(UUID id, boolean write) {
    var u = operator(uResource(write), write ? "UPDATE" : "READ");
    if (write)
      db.queryForObject(
          "select id from organization.organization where id=? for update",
          UUID.class,
          u.organizationId());
    u = operator(uResource(write), write ? "UPDATE" : "READ");
    var rows =
        db.queryForList(
            "select e.patient_id,e.status"
                + " episode_status,e.started_at,e.expires_at,v.status,v.professional_id from"
                + " care.care_visit v join care.care_episode e on e.id=v.episode_id where v.id=?"
                + " and e.organization_id=?",
            id,
            u.organizationId());
    if (rows.isEmpty()) throw new ResourceNotFoundException("CareVisit", id);
    var r = rows.getFirst();
    boolean reception =
        !Set.of("IN_PROGRESS", "COMPLETED", "CANCELLED").contains(r.get("status"))
            && !Set.of("CLOSED", "EXPIRED").contains(r.get("episode_status"))
            && (!"ACTIVE".equals(r.get("episode_status"))
                || r.get("started_at") == null
                || (r.get("expires_at") instanceof Timestamp t
                    && t.toInstant().isAfter(Instant.now())));
    if (write && !reception && !"IN_PROGRESS".equals(r.get("status")))
      throw new BusinessRuleException(
          "VISIT_MEASUREMENTS_CLOSED",
          "Measurements are available during reception or an active consultation");
    if (reception) permissions.require(u.organizationId(), "intakes", write ? "UPDATE" : "READ");
    else {
      if (write && !u.hasAny("DOCTOR", "NURSE")) throw denied("ASSIGNED_CLINICIAN_REQUIRED");
      clinician("clinicalrecords", write ? "UPDATE" : "READ");
      Integer qualified =
          db.queryForObject(
              "select count(*) from licensing.member m join professional.professional p on"
                  + " p.person_id=m.person_id and p.status='ACTIVE' where m.id=? and"
                  + " m.organization_id=? and m.person_id=? and m.status='ACTIVE' and"
                  + " ((m.role='NURSE' and p.professional_type='NURSE' and ?) or (m.role='DOCTOR'"
                  + " and p.professional_type in ('DOCTOR','MEDECIN') and ?)) and p.id=? and"
                  + " exists(select 1 from professional.professional_assignment a where"
                  + " a.professional_id=p.id and a.organization_id=m.organization_id and"
                  + " a.status='ACTIVE' and a.start_date<=current_date and (a.end_date is null or"
                  + " a.end_date>=current_date))",
              Integer.class,
              u.profileId(),
              u.organizationId(),
              u.personId(),
              u.has("NURSE"),
              u.has("DOCTOR"),
              r.get("professional_id"));
      if (qualified == null || qualified != 1) throw denied("ASSIGNED_CLINICIAN_REQUIRED");
    }
    UUID patient = (UUID) r.get("patient_id");
    if (!reception) access.requireAccess(patient, "MEDICAL_RECORD", write ? "WRITE" : "READ");
    return new VisitContext(patient, u.organizationId(), u.personId(), reception);
  }

  private String uResource(boolean write) {
    var u = users.current();
    return u.has("DOCTOR") ? "clinicalrecords" : "intakes";
  }

  @Override
  public List<Observation> appendMeasured(UUID visit, List<ObservationInput> values) {
    if (values == null || values.isEmpty()) return List.of();
    if (values.size() > 100) throw new IllegalArgumentException("TOO_MANY_MEASUREMENTS");
    var c = visitContext(visit, true);
    return values.stream()
        .map(v -> append(c.patient(), c.organization(), visit, c.actor(), false, v))
        .toList();
  }

  @Override
  public void appendVitals(UUID visit, Map<String, Object> values, Instant measuredAt) {
    var inputs = new ArrayList<ObservationInput>();
    for (var entry : values.entrySet()) {
      if (entry.getValue() == null) continue;
      var defs =
          db.queryForList(
              "select id from patienthealth.parameter where code=? and patient_id is null",
              UUID.class,
              entry.getKey());
      if (defs.size() != 1) throw new IllegalArgumentException("UNKNOWN_VITAL_CODE");
      inputs.add(
          new ObservationInput(
              defs.getFirst(), entry.getValue().toString(), measuredAt, null, null));
    }
    appendMeasured(visit, inputs);
  }

  @Override
  @Transactional(readOnly = true)
  public List<Observation> forVisit(UUID visit) {
    var c = visitContext(visit, false);
    return db.query(
        observationSelect()
            + " where o.visit_id=? and o.patient_id=? order by o.measured_at desc,o.id",
        (rs, n) -> observation(rs),
        visit,
        c.patient());
  }

  @Transactional(readOnly = true)
  public List<Parameter> careParameters(UUID visit) {
    if (visit == null) {
      operator("intakes", "READ");
      return parameters(null);
    }
    return parameters(visitContext(visit, false).patient());
  }

  @Transactional(readOnly = true)
  public List<Declaration> declarations(UUID patient) {
    return db.query(
        "select d.*,concat_ws(' ',p.first_name,p.last_name) actor_name from"
            + " patienthealth.declaration d join identity.person p on p.id=d.actor_person_id where"
            + " d.patient_id=? order by d.recorded_at desc,d.id",
        (r, n) ->
            new Declaration(
                (UUID) r.getObject("id"),
                r.getString("kind"),
                r.getString("label"),
                r.getString("value_text"),
                r.getString("detail"),
                r.getDate("occurred_on") == null ? null : r.getDate("occurred_on").toLocalDate(),
                r.getTimestamp("recorded_at").toInstant(),
                r.getString("actor_name"),
                (UUID) r.getObject("supersedes_id")),
        patient);
  }

  public Declaration declare(DeclarationInput input) {
    UUID patient = lockSelectedPatient();
    if ("BLOOD_GROUP".equals(input.kind())
        && !Set.of("A", "B", "AB", "O", "UNKNOWN").contains(input.value()))
      throw new IllegalArgumentException("INVALID_DECLARED_BLOOD_GROUP");
    if ("RHESUS".equals(input.kind()) && !Set.of("+", "-", "UNKNOWN").contains(input.value()))
      throw new IllegalArgumentException("INVALID_DECLARED_RHESUS");
    if (input.occurredOn() != null && input.occurredOn().isAfter(LocalDate.now(ZoneOffset.UTC)))
      throw new IllegalArgumentException("INVALID_DECLARATION_DATE");
    if (input.supersedesId() != null
        && !Integer.valueOf(1)
            .equals(
                db.queryForObject(
                    "select count(*) from patienthealth.declaration where id=? and patient_id=? and"
                        + " kind=?",
                    Integer.class,
                    input.supersedesId(),
                    patient,
                    input.kind())))
      throw new IllegalArgumentException("INVALID_DECLARATION_CORRECTION");
    UUID id = UUID.randomUUID();
    db.update(
        "insert into"
            + " patienthealth.declaration(id,patient_id,actor_person_id,kind,label,value_text,detail,occurred_on,supersedes_id)"
            + " values(?,?,?,?,?,?,?,?,?)",
        id,
        patient,
        users.current().personId(),
        input.kind(),
        input.label(),
        input.value(),
        input.detail(),
        input.occurredOn(),
        input.supersedesId());
    audit(users.current().personId(), null, patient, "SELF_DECLARATION_APPENDED", id);
    return declarations(patient).stream().filter(d -> d.id().equals(id)).findFirst().orElseThrow();
  }

  @Transactional(readOnly = true)
  public PersonProfileResponse personal() {
    UUID patient = selectedPatient();
    return identities.profilePerson(patientPerson(patient));
  }

  public PersonProfileResponse updatePersonal(UpdatePersonProfileRequest request) {
    UUID patient = lockSelectedPatient(), person = patientPerson(patient);
    db.queryForObject("select id from identity.person where id=? for update", UUID.class, person);
    selectedPatient();
    if (!person.equals(users.current().personId())) {
      var current =
          db.queryForObject(
              "select birth_date from identity.person where id=?", java.sql.Date.class, person);
      if (!Objects.equals(current == null ? null : current.toLocalDate(), request.birthDate()))
        throw denied("FAMILY_BIRTH_DATE_REQUIRES_HOSPITAL_CORRECTION");
    }
    var result = identities.updatePerson(person, request);
    audit(users.current().personId(), null, patient, "PATIENT_PERSONAL_UPDATED", person);
    return result;
  }

  UUID patientPerson(UUID patient) {
    return db.queryForObject(
        "select person_id from patient.patient where id=?", UUID.class, patient);
  }

  private void audit(UUID actor, UUID organization, UUID patient, String action, UUID target) {
    db.update(
        "insert into"
            + " audit.audit_log(actor_person_id,organization_id,module,entity_type,entity_id,action,new_value)"
            + " values(?,?,'PATIENT_HEALTH','PatientHealth',?,?,?::jsonb)",
        actor,
        organization,
        target,
        action,
        "{\"patientId\":\"" + patient + "\"}");
  }
}
