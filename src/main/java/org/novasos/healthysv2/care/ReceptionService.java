package org.novasos.healthysv2.care;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.List;
import java.util.UUID;
import org.novasos.healthysv2.licensing.api.LicensingAccess;
import org.novasos.healthysv2.patient.CurrentUserContext;
import org.novasos.healthysv2.patient.PatientAccessService;
import org.novasos.healthysv2.shared.api.error.BusinessRuleException;
import org.novasos.healthysv2.shared.api.error.ResourceNotFoundException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class ReceptionService {
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

  private VisitSlotService slots;

  @org.springframework.beans.factory.annotation.Autowired
  public void configureSlots(VisitSlotService slots) {
    this.slots = slots;
  }

  private final CareEpisodeRepository episodes;
  private final CareVisitRepository visits;
  private final CarePolicy policy;
  private final CurrentUserContext users;
  private final LicensingAccess licensing;
  private final PatientAccessService access;
  private final JdbcTemplate jdbc;
  private final ObjectMapper json;
  private final org.novasos.healthysv2.audit.AuditTrail audit;

  ReceptionService(
      CareEpisodeRepository episodes,
      CareVisitRepository visits,
      CarePolicy policy,
      CurrentUserContext users,
      LicensingAccess licensing,
      PatientAccessService access,
      JdbcTemplate jdbc,
      ObjectMapper json,
      org.novasos.healthysv2.audit.AuditTrail audit) {
    this.episodes = episodes;
    this.visits = visits;
    this.policy = policy;
    this.users = users;
    this.licensing = licensing;
    this.access = access;
    this.jdbc = jdbc;
    this.json = json;
    this.audit = audit;
  }

  @Transactional(readOnly = true)
  public ReceptionDtos.ReceptionOptions receptionOptions() {
    var user = receptionUser("episodes", "READ");
    licensing.requireActive(user.organizationId());
    var doctors =
        jdbc.query(
            """
            select p.id, person.first_name, person.last_name,
                   array_agg(distinct specialty.id order by specialty.id) as speciality_ids
            from professional.professional p
            join identity.person person on person.id=p.person_id and person.status='ACTIVE'
            join professional.professional_speciality ps on ps.professional_id=p.id
            join catalog.speciality_catalog specialty on specialty.id=ps.speciality_catalog_id and specialty.active
            where p.status='ACTIVE' and p.professional_type in ('DOCTOR','MEDECIN','NURSE')
              and exists(select 1 from professional.professional_assignment a where a.professional_id=p.id
                and a.organization_id=? and a.status='ACTIVE' and a.start_date<=current_date
                and (a.end_date is null or a.end_date>=current_date))
              and exists(select 1 from licensing.member m where m.person_id=p.person_id
                and m.organization_id=? and ((m.role='DOCTOR' and p.professional_type in ('DOCTOR','MEDECIN')) or (m.role='NURSE' and p.professional_type='NURSE')) and m.status='ACTIVE')
            group by p.id,person.first_name,person.last_name
            order by person.last_name,person.first_name,p.id
            """,
            (rs, row) ->
                new ReceptionDtos.ReceptionDoctor(
                    rs.getObject(1, UUID.class),
                    rs.getString(2),
                    rs.getString(3),
                    java.util.Arrays.stream((Object[]) rs.getArray(4).getArray())
                        .map(UUID.class::cast)
                        .toList()),
            user.organizationId(),
            user.organizationId());
    var specialities =
        jdbc.query(
            "select id,name from catalog.speciality_catalog where active order by name,id",
            (rs, row) ->
                new ReceptionDtos.ReceptionSpeciality(
                    rs.getObject(1, UUID.class), rs.getString(2)));
    return new ReceptionDtos.ReceptionOptions(doctors, specialities);
  }

  @Transactional
  public ReceptionDtos.Episode open(ReceptionDtos.OpenEpisode request) {
    return openInternal(request, receptionUser("episodes", "CREATE"), "episodes", "CREATE");
  }

  @Transactional
  ReceptionDtos.Episode openForAppointmentRequest(ReceptionDtos.OpenEpisode request) {
    return openInternal(request, receptionUser("scheduling", "UPDATE"), "scheduling", "UPDATE");
  }

  private ReceptionDtos.Episode openInternal(
      ReceptionDtos.OpenEpisode request,
      CurrentUserContext.UserContext user,
      String resource,
      String action) {
    requireExists(
        "select count(*) from patient.patient where id=? and status='ACTIVE'",
        "Patient",
        request.patientId());
    requireExists(
        "select count(*) from catalog.speciality_catalog where id=? and active",
        "Speciality",
        request.specialityId());
    requireDoctor(request.professionalId(), request.specialityId(), user.organizationId());
    jdbc.queryForObject(
        "select id from organization.organization where id=? for update",
        UUID.class,
        user.organizationId());
    receptionUser(resource, action);
    requireDoctor(request.professionalId(), request.specialityId(), user.organizationId());
    var now = Instant.now();
    if (request.scheduledAt().isBefore(now))
      throw rule("INVALID_VISIT_DATE", "Visit date is in the past");
    var episode =
        CareEpisode.create(
            request.patientId(), user.organizationId(), request.reason(), policy, now);
    episode.specifySpeciality(request.specialityId());
    var configured =
        jdbc.queryForList(
            "select p.follow_up_seconds,t.id,t.unit_price,t.currency,t.tax_rate from"
                + " care.speciality_policy p join billing.care_tariff t on t.id=p.tariff_id and"
                + " t.organization_id=p.organization_id and t.active where p.organization_id=? and"
                + " p.speciality_id=?",
            user.organizationId(),
            request.specialityId());
    if (configured.isEmpty())
      throw rule(
          "SPECIALITY_POLICY_REQUIRED",
          "Configure the specialty tariff and consultation validity before opening an episode");
    var pricing = configured.getFirst();
    episode.snapshotDuration(((Number) pricing.get("follow_up_seconds")).longValue());
    episodes.saveAndFlush(episode);
    jdbc.update(
        "update care.care_episode set"
            + " tariff_id=?,tariff_price=?,tariff_currency=?,tariff_tax_rate=? where id=?",
        pricing.get("id"),
        pricing.get("unit_price"),
        pricing.get("currency"),
        pricing.get("tax_rate"),
        episode.getId());
    var visit =
        CareVisit.prepare(episode, request.professionalId(), request.scheduledAt(), policy, now);
    int duration =
        slots.requireSlot(
            request.professionalId(),
            user.organizationId(),
            request.specialityId(),
            request.scheduledAt(),
            visit.getId());
    visit.snapshotSlot(duration);
    visits.saveAndFlush(visit);
    audit.change(
        user.personId(),
        user.organizationId(),
        "CARE",
        "CareEpisode",
        episode.getId(),
        "CREATE",
        null,
        java.util.Map.of(
            "patientId",
            episode.getPatientId(),
            "specialityId",
            request.specialityId(),
            "status",
            episode.getStatus()));
    return response(episode, false);
  }

  @Transactional(readOnly = true)
  public List<ReceptionDtos.Episode> list(UUID patientId) {
    var user = receptionUser("episodes", "READ");
    var found =
        patientId == null
            ? episodes.findByOrganizationIdOrderByCreatedAtDesc(user.organizationId())
            : episodes.findByOrganizationIdAndPatientIdOrderByCreatedAtDesc(
                user.organizationId(), patientId);
    return found.stream().map(e -> response(e, false)).toList();
  }

  @Transactional(readOnly = true)
  public ReceptionDtos.Episode detail(UUID id, boolean includeIntake) {
    var user = receptionUser("episodes", "READ");
    var episode = scoped(id, user.organizationId());
    boolean clinical = includeIntake;
    if (clinical) permission(user.organizationId(), "intakes", "READ");
    if (clinical && !user.hasAny("NURSE", "DOCTOR"))
      throw new AccessDeniedException("CLINICAL_ROLE_REQUIRED");
    if (clinical) access.requireAccess(episode.getPatientId(), "MEDICAL_RECORD", "READ");
    return response(episode, clinical);
  }

  /** Only the local reception observations, never the patient's global clinical record. */
  @Transactional(readOnly = true)
  public ReceptionDtos.Intake readIntake(UUID id) {
    var user = receptionUser("intakes", "READ");
    if (!user.has("NURSE") || user.personId() == null)
      throw new AccessDeniedException("NURSE_REQUIRED");
    licensing.requireActive(user.organizationId());
    var episode = scoped(id, user.organizationId());
    ensureReceptionOpen(episode);
    var result = loadIntake(id);
    audit.access(
        user.personId(),
        episode.getPatientId(),
        user.organizationId(),
        "RECEPTION_INTAKE",
        id,
        "READ",
        "NURSE_CURRENT_RECEPTION",
        java.util.Map.of("episodeId", id));
    return result == null
        ? new ReceptionDtos.Intake(
            "", new ReceptionDtos.Vitals(null, null, null, null, null, null, null, null))
        : result;
  }

  @Transactional
  public ReceptionDtos.Episode intake(UUID id, ReceptionDtos.Intake request) {
    var user = receptionUser("intakes", "UPDATE");
    if (!user.has("NURSE") || user.personId() == null)
      throw new AccessDeniedException("NURSE_REQUIRED");
    // Serialize concurrent intake updates with the episode's lifecycle operations.
    var episode = scoped(id, user.organizationId());
    jdbc.queryForObject(
        "select id from care.care_episode where id=? and organization_id=? for update",
        UUID.class,
        id,
        user.organizationId());
    ensureReceptionOpen(episode);
    Integer initial =
        jdbc.queryForObject(
            "select count(*) from care.care_visit where episode_id=? and not follow_up and"
                + " started_at is null and status<>'CANCELLED'",
            Integer.class,
            id);
    if (initial == null || initial == 0)
      throw rule(
          "INTAKE_CLOSED", "Use the new visit intake; the initial encounter has already started");
    var vital = request.vitals();
    if (vital.systolicBp() != null
        && vital.diastolicBp() != null
        && vital.systolicBp() <= vital.diastolicBp())
      throw rule("INVALID_BLOOD_PRESSURE", "Systolic pressure must exceed diastolic pressure");
    var now = Instant.now();
    jdbc.update(
        """
        insert into care.reception_intake(episode_id,observations,vitals,recorded_by,recorded_at,retention_expires_at)
        values(?,?,?::jsonb,?,?,?) on conflict(episode_id) do update set
        observations=excluded.observations,vitals=excluded.vitals,recorded_by=excluded.recorded_by,
        recorded_at=excluded.recorded_at
        """,
        id,
        request.observations() == null ? "" : request.observations().trim(),
        encode(vital),
        user.personId(),
        java.sql.Timestamp.from(now),
        java.sql.Timestamp.from(
            now.atZone(ZoneOffset.UTC).plusYears(policy.retentionYears()).toInstant()));
    audit.change(
        user.personId(),
        user.organizationId(),
        "CARE",
        "ReceptionIntake",
        id,
        "UPDATE",
        null,
        java.util.Map.of("episodeId", id, "recordedAt", now));
    audit.access(
        user.personId(),
        episode.getPatientId(),
        user.organizationId(),
        "RECEPTION_INTAKE",
        id,
        "WRITE",
        "NURSE_CURRENT_RECEPTION",
        java.util.Map.of("episodeId", id));
    // This operation writes only today's reception; it does not expose earlier medical records.
    return response(episode, true);
  }

  private CurrentUserContext.UserContext receptionUser(String resource, String action) {
    var user = users.current();
    if (user.organizationId() == null)
      throw new AccessDeniedException("ORGANIZATION_CONTEXT_REQUIRED");
    permission(user.organizationId(), resource, action);
    licensing.requireMember(user.organizationId());
    if (!action.equals("READ")) licensing.requireActive(user.organizationId());
    return user;
  }

  private void ensureReceptionOpen(CareEpisode episode) {
    if (episode.getStatus() == CareEpisode.Status.CLOSED
        || (episode.getStatus() == CareEpisode.Status.ACTIVE
            && episode.getStartedAt() != null
            && !episode.isActiveAt(Instant.now()))) {
      throw rule("EPISODE_CLOSED", "Reception is closed or follow-up coverage expired");
    }
  }

  private CareEpisode scoped(UUID id, UUID org) {
    return episodes
        .findById(id)
        .filter(e -> e.getOrganizationId().equals(org))
        .orElseThrow(() -> new ResourceNotFoundException("CareEpisode", id));
  }

  private void requireDoctor(UUID professional, UUID speciality, UUID org) {
    Integer count =
        jdbc.queryForObject(
            """
            select count(*) from professional.professional p
            where p.id=? and p.status='ACTIVE' and p.professional_type in ('DOCTOR','MEDECIN','NURSE')
            and exists(select 1 from identity.person person where person.id=p.person_id and person.status='ACTIVE')
            and exists(select 1 from professional.professional_assignment a where a.professional_id=p.id
              and a.organization_id=? and a.status='ACTIVE' and a.start_date<=current_date and (a.end_date is null or a.end_date>=current_date))
            and exists(select 1 from licensing.member m where m.person_id=p.person_id and m.organization_id=? and ((m.role='DOCTOR' and p.professional_type in ('DOCTOR','MEDECIN')) or (m.role='NURSE' and p.professional_type='NURSE')) and m.status='ACTIVE')
            and exists(select 1 from professional.professional_speciality s where s.professional_id=p.id and s.speciality_catalog_id=?)
            """,
            Integer.class,
            professional,
            org,
            org,
            speciality);
    if (count == null || count == 0)
      throw rule(
          "DOCTOR_NOT_AVAILABLE", "Doctor must be active in this establishment and specialty");
  }

  private void requireExists(String sql, String resource, UUID id) {
    Integer count = jdbc.queryForObject(sql, Integer.class, id);
    if (count == null || count == 0) throw new ResourceNotFoundException(resource, id);
  }

  private ReceptionDtos.Episode response(CareEpisode e, boolean includeIntake) {
    var summaries =
        visits.findByEpisodeIdOrderByCreatedAtAsc(e.getId()).stream()
            .map(
                v ->
                    new ReceptionDtos.Visit(
                        v.getId(),
                        v.getProfessionalId(),
                        v.getScheduledAt(),
                        v.getStatus(),
                        v.isFollowUp(),
                        v.getStartedAt(),
                        v.getVersion(),
                        v.getSlotMinutes(),
                        v.getReason()))
            .toList();
    ReceptionDtos.Intake intake = null;
    if (includeIntake) intake = loadIntake(e.getId());
    var price =
        jdbc.queryForMap(
            "select tariff_id,tariff_price,tariff_currency,tariff_tax_rate from care.care_episode"
                + " where id=?",
            e.getId());
    return new ReceptionDtos.Episode(
        e.getId(),
        e.getPatientId(),
        e.getOrganizationId(),
        e.getReason(),
        e.getSpecialityId(),
        e.getStatus(),
        e.getCreatedAt(),
        e.getStartedAt(),
        e.getExpiresAt(),
        e.getFollowUpSeconds(),
        summaries,
        intake,
        (UUID) price.get("tariff_id"),
        (java.math.BigDecimal) price.get("tariff_price"),
        (String) price.get("tariff_currency"),
        (java.math.BigDecimal) price.get("tariff_tax_rate"));
  }

  private ReceptionDtos.Intake loadIntake(UUID id) {
    return jdbc.query(
        "select i.observations,i.vitals::text from care.visit_intake i join care.care_visit v on"
            + " v.id=i.visit_id where v.episode_id=? and not v.follow_up and v.status<>'CANCELLED'",
        rs -> rs.next() ? new ReceptionDtos.Intake(rs.getString(1), decode(rs.getString(2))) : null,
        id);
  }

  private String encode(Object value) {
    try {
      return json.writeValueAsString(value);
    } catch (JsonProcessingException e) {
      throw new IllegalStateException(e);
    }
  }

  private ReceptionDtos.Vitals decode(String value) {
    try {
      return json.readValue(value, ReceptionDtos.Vitals.class);
    } catch (JsonProcessingException e) {
      throw new IllegalStateException(e);
    }
  }

  private BusinessRuleException rule(String code, String message) {
    return new BusinessRuleException(code, message);
  }
}
