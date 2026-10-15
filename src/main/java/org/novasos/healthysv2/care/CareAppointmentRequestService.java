package org.novasos.healthysv2.care;

import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Timestamp;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.List;
import java.util.UUID;
import org.novasos.healthysv2.licensing.api.LicensingAccess;
import org.novasos.healthysv2.organizationaccess.api.OrganizationPermissionAccess;
import org.novasos.healthysv2.patient.CurrentUserContext;
import org.novasos.healthysv2.shared.api.error.BusinessRuleException;
import org.novasos.healthysv2.shared.api.error.ResourceNotFoundException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class CareAppointmentRequestService {
  private final JdbcTemplate jdbc;
  private final CurrentUserContext users;
  private final OrganizationPermissionAccess permissions;
  private final LicensingAccess licensing;
  private final ReceptionService reception;
  private final CarePolicy policy;

  public CareAppointmentRequestService(
      JdbcTemplate jdbc,
      CurrentUserContext users,
      OrganizationPermissionAccess permissions,
      LicensingAccess licensing,
      ReceptionService reception,
      CarePolicy policy) {
    this.jdbc = jdbc;
    this.users = users;
    this.permissions = permissions;
    this.licensing = licensing;
    this.reception = reception;
    this.policy = policy;
  }

  @Transactional(readOnly = true)
  public List<CareAppointmentRequestDtos.Hospital> hospitals(String city, UUID countryId) {
    patientUser();
    return jdbc.query(
        """
        select o.id,o.name,a.city,a.country_id,c.name
        from organization.organization o
        left join lateral (select address.* from organization.organization_address oa
          join shared.address address on address.id=oa.address_id where oa.organization_id=o.id
          order by oa.is_primary desc,oa.id limit 1) a on true
        left join shared.country c on c.id=a.country_id
        where o.status='ACTIVE' and (?::text is null or lower(a.city) like lower(?))
          and (?::uuid is null or a.country_id=?) order by o.name,o.id limit 100
        """,
        (r, n) ->
            new CareAppointmentRequestDtos.Hospital(
                r.getObject(1, UUID.class),
                r.getString(2),
                r.getString(3),
                r.getObject(4, UUID.class),
                r.getString(5)),
        city,
        city == null ? null : "%" + city.trim() + "%",
        countryId,
        countryId);
  }

  @Transactional(readOnly = true)
  public List<ReceptionDtos.ReceptionSpeciality> specialities(UUID organizationId) {
    patientUser();
    exists(
        "select count(*) from organization.organization where id=? and status='ACTIVE'",
        organizationId,
        "Organization");
    return jdbc.query(
        """
        select distinct c.id,c.name from catalog.speciality_catalog c
        join professional.professional_speciality ps on ps.speciality_catalog_id=c.id
        join professional.professional p on p.id=ps.professional_id and p.status='ACTIVE'
        join licensing.member m on m.person_id=p.person_id and m.organization_id=? and ((m.role='DOCTOR' and p.professional_type in ('DOCTOR','MEDECIN')) or (m.role='NURSE' and p.professional_type='NURSE')) and m.status='ACTIVE'
        where c.active and exists(select 1 from professional.professional_assignment a
          where a.professional_id=p.id and a.organization_id=? and a.status='ACTIVE' and a.start_date<=current_date
          and (a.end_date is null or a.end_date>=current_date)) order by c.name,c.id
        """,
        (r, n) -> new ReceptionDtos.ReceptionSpeciality(r.getObject(1, UUID.class), r.getString(2)),
        organizationId,
        organizationId);
  }

  @Transactional
  public CareAppointmentRequestDtos.Request self(CareAppointmentRequestDtos.SelfRequest r) {
    var u = patientUser();
    return create(
        r.organizationId(),
        u.patientId(),
        r.specialityId(),
        r.reason(),
        r.preferredAt(),
        "SELF",
        u.personId());
  }

  @Transactional
  public CareAppointmentRequestDtos.Request staff(CareAppointmentRequestDtos.StaffRequest r) {
    var u = staffUser("CREATE");
    return create(
        u.organizationId(),
        r.patientId(),
        r.specialityId(),
        r.reason(),
        r.preferredAt(),
        r.source(),
        u.personId());
  }

  @Transactional(readOnly = true)
  public List<CareAppointmentRequestDtos.Request> mine() {
    var u = patientUser();
    return jdbc.query(
        "select * from care.appointment_request where patient_id=? order by created_at desc limit"
            + " 200",
        this::map,
        u.patientId());
  }

  @Transactional(readOnly = true)
  public List<CareAppointmentRequestDtos.Request> list(String status) {
    var u = staffUser("READ");
    if (status != null && !List.of("PENDING", "APPROVED", "REJECTED").contains(status))
      throw rule("INVALID_REQUEST_STATUS");
    return jdbc.query(
        "select * from care.appointment_request where organization_id=? and (?::text is null or"
            + " status=?) order by created_at desc limit 200",
        this::map,
        u.organizationId(),
        status,
        status);
  }

  @Transactional
  public CareAppointmentRequestDtos.Request approve(
      UUID id, CareAppointmentRequestDtos.Approval r) {
    var u = staffUser("UPDATE");
    var request = pending(id, u.organizationId());
    var episode =
        reception.openForAppointmentRequest(
            new ReceptionDtos.OpenEpisode(
                request.patientId(),
                request.reason(),
                request.specialityId(),
                r.professionalId(),
                r.scheduledAt()));
    var visit = episode.visits().getFirst();
    jdbc.update(
        "update care.appointment_request set"
            + " status='APPROVED',decided_by=?,decided_at=?,episode_id=?,visit_id=? where id=?",
        u.personId(),
        Timestamp.from(Instant.now()),
        episode.id(),
        visit.id(),
        id);
    return read(id);
  }

  @Transactional
  public CareAppointmentRequestDtos.Request reject(
      UUID id, CareAppointmentRequestDtos.Rejection r) {
    var u = staffUser("UPDATE");
    pending(id, u.organizationId());
    jdbc.update(
        "update care.appointment_request set"
            + " status='REJECTED',decided_by=?,decided_at=?,rejection_reason=? where id=?",
        u.personId(),
        Timestamp.from(Instant.now()),
        r.reason().trim(),
        id);
    return read(id);
  }

  private CareAppointmentRequestDtos.Request create(
      UUID org,
      UUID patient,
      UUID specialty,
      String reason,
      Instant date,
      String source,
      UUID actor) {
    if (date == null || !date.isAfter(Instant.now())) throw rule("INVALID_VISIT_DATE");
    if (!List.of("SELF", "PHONE", "RECEPTION").contains(source))
      throw rule("INVALID_REQUEST_SOURCE");
    exists(
        "select count(*) from organization.organization where id=? and status='ACTIVE'",
        org,
        "Organization");
    exists(
        "select count(*) from patient.patient where id=? and status='ACTIVE'", patient, "Patient");
    exists(
        "select count(*) from catalog.speciality_catalog where id=? and active",
        specialty,
        "Speciality");
    var id = UUID.randomUUID();
    var now = Instant.now();
    jdbc.update(
        """
        insert into care.appointment_request(id,organization_id,patient_id,speciality_id,reason,preferred_at,source,status,created_by,created_at,retention_expires_at)
        values(?,?,?,?,?,?,?,'PENDING',?,?,?)
        """,
        id,
        org,
        patient,
        specialty,
        reason.trim(),
        Timestamp.from(date),
        source,
        actor,
        Timestamp.from(now),
        Timestamp.from(now.atZone(ZoneOffset.UTC).plusYears(policy.retentionYears()).toInstant()));
    // Pending requests are administrative only: no care relationship or dossier grant is created.
    return read(id);
  }

  private CurrentUserContext.UserContext patientUser() {
    var u = users.current();
    if (!u.isPatient(u.patientId())) throw new AccessDeniedException("PATIENT_PROFILE_REQUIRED");
    exists(
        "select count(*) from patient.patient where id=? and status='ACTIVE'",
        u.patientId(),
        "Patient");
    return u;
  }

  private CurrentUserContext.UserContext staffUser(String action) {
    var u = users.current();
    if (u.organizationId() == null || u.personId() == null)
      throw new AccessDeniedException("ORGANIZATION_CONTEXT_REQUIRED");
    permissions.require(u.organizationId(), "scheduling", action);
    licensing.requireMember(u.organizationId());
    licensing.requireActive(u.organizationId());
    return u;
  }

  private CareAppointmentRequestDtos.Request pending(UUID id, UUID org) {
    var found =
        jdbc.query(
            "select * from care.appointment_request where id=? and organization_id=? for update",
            this::map,
            id,
            org);
    if (found.isEmpty()) throw new ResourceNotFoundException("AppointmentRequest", id);
    if (!found.getFirst().status().equals("PENDING")) throw rule("REQUEST_ALREADY_DECIDED");
    return found.getFirst();
  }

  private CareAppointmentRequestDtos.Request read(UUID id) {
    return jdbc.queryForObject("select * from care.appointment_request where id=?", this::map, id);
  }

  private void exists(String sql, UUID id, String resource) {
    var count = jdbc.queryForObject(sql, Integer.class, id);
    if (count == null || count == 0) throw new ResourceNotFoundException(resource, id);
  }

  private CareAppointmentRequestDtos.Request map(ResultSet r, int n) throws SQLException {
    var decided = r.getTimestamp("decided_at");
    return new CareAppointmentRequestDtos.Request(
        r.getObject("id", UUID.class),
        r.getObject("organization_id", UUID.class),
        r.getObject("patient_id", UUID.class),
        r.getObject("speciality_id", UUID.class),
        r.getString("reason"),
        r.getTimestamp("preferred_at").toInstant(),
        r.getString("source"),
        r.getString("status"),
        r.getTimestamp("created_at").toInstant(),
        r.getObject("decided_by", UUID.class),
        decided == null ? null : decided.toInstant(),
        r.getString("rejection_reason"),
        r.getObject("episode_id", UUID.class),
        r.getObject("visit_id", UUID.class));
  }

  private BusinessRuleException rule(String code) {
    return new BusinessRuleException(code, code);
  }
}
