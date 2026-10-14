package org.novasos.healthysv2.accessprofile;

import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import org.novasos.healthysv2.accessprofile.api.AccessProfileResolver;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@Transactional(readOnly = true)
class OwnedAccessProfileService implements AccessProfileResolver {
  private final JdbcTemplate jdbc;
  private final int adultAge;

  @org.springframework.beans.factory.annotation.Autowired
  OwnedAccessProfileService(
      JdbcTemplate jdbc,
      @org.springframework.beans.factory.annotation.Value("${PATIENT_ADULT_AGE:18}") int adultAge) {
    if (adultAge < 16 || adultAge > 25)
      throw new IllegalArgumentException("Invalid patient adult age");
    this.jdbc = jdbc;
    this.adultAge = adultAge;
  }

  OwnedAccessProfileService(JdbcTemplate jdbc) {
    this(jdbc, 18);
  }

  @Override
  public List<AccessProfile> profiles(UUID subject, Set<String> originalRoles) {
    if (subject == null) throw denied("ACCOUNT_REQUIRED");
    var people =
        jdbc.query(
            "select id from identity.person where keycloak_user_id=? and status='ACTIVE'",
            (row, index) -> row.getObject(1, UUID.class),
            subject);
    if (people.size() != 1) throw denied("ACTIVE_ACCOUNT_REQUIRED");
    UUID person = people.getFirst();
    List<AccessProfile> profiles = new ArrayList<>(persisted(person));
    return List.copyOf(profiles);
  }

  private List<AccessProfile> persisted(UUID person) {
    List<AccessProfile> result = new ArrayList<>();
    var administrators =
        jdbc.queryForList(
            "select person_id from accessprofile.platform_administrator where person_id=? and"
                + " status='ACTIVE'",
            UUID.class,
            person);
    for (UUID administrator : administrators)
      result.add(
          new AccessProfile(
              AccessProfileResolver.platformId(administrator),
              administrator,
              "PLATFORM_ADMIN",
              "PLATFORM",
              administrator,
              null,
              null,
              "Administration HEALTH’YS",
              true));

    var patients =
        jdbc.query(
            "select id from patient.patient where person_id=? and status='ACTIVE'",
            (row, index) -> row.getObject(1, UUID.class),
            person);
    for (UUID patient : patients)
      result.add(
          new AccessProfile(
              patient, person, "PATIENT", "PATIENT", patient, null, null, "Patient", true));
    result.addAll(
        jdbc.query(
            """
            select m.id,m.organization_id,m.role,o.name,
             o.status='ACTIVE' and (m.role not in ('DOCTOR','NURSE') or exists (
                select 1 from professional.professional p
                join professional.professional_assignment a on a.professional_id=p.id
                where p.person_id=m.person_id and p.status='ACTIVE' and a.organization_id=m.organization_id
                  and a.status='ACTIVE' and a.start_date<=current_date and (a.end_date is null or a.end_date>=current_date)
                  and (p.professional_type=m.role or (m.role='DOCTOR' and p.professional_type='MEDECIN'))
                  and not exists(select 1 from professional.registration_request r where r.person_id=p.person_id and r.status<>'APPROVED')
             )) as operational
            from licensing.member m join organization.organization o on o.id=m.organization_id
            where m.person_id=? and m.status='ACTIVE' order by o.name,m.role,m.id
            """,
            (row, index) ->
                new AccessProfile(
                    row.getObject("id", UUID.class),
                    person,
                    row.getString("role"),
                    "ORGANIZATION_MEMBERSHIP",
                    row.getObject("id", UUID.class),
                    row.getObject("organization_id", UUID.class),
                    row.getString("name"),
                    row.getString("name") + " — " + row.getString("role"),
                    row.getBoolean("operational")),
            person));
    result.addAll(
        jdbc.query(
            """
            select o.id,o.name,o.status from organization.organization o
            where o.administrator_person_id=? and not exists(
                select 1 from licensing.member m where m.organization_id=o.id and m.person_id=?
                  and m.role='HOSPITAL_ADMIN' and m.status='ACTIVE') order by o.name,o.id
            """,
            (row, index) ->
                new AccessProfile(
                    AccessProfileResolver.administratorId(row.getObject("id", UUID.class), person),
                    person,
                    "HOSPITAL_ADMIN",
                    "ORGANIZATION_ADMINISTRATOR",
                    row.getObject("id", UUID.class),
                    row.getObject("id", UUID.class),
                    row.getString("name"),
                    row.getString("name") + " — HOSPITAL_ADMIN",
                    "ACTIVE".equals(row.getString("status"))),
            person,
            person));
    result.addAll(
        jdbc.query(
            """
                select g.id,g.patient_id,p.first_name,p.last_name
                from family.guardianship g join identity.person guardian on guardian.id=g.guardian_person_id
            join patient.patient patient on patient.id=g.patient_id
                join identity.person p on p.id=patient.person_id
                where g.guardian_person_id=? and g.status='ACTIVE' and patient.status='ACTIVE' and p.status='ACTIVE'
                  and p.birth_date is not null and p.birth_date<=current_date
                  and p.birth_date+make_interval(years=>?)>current_date
                  and guardian.birth_date is not null and guardian.birth_date+make_interval(years=>?)<=current_date
            and g.adulthood_at>current_date order by p.first_name,p.last_name,g.id
            """,
            (row, index) ->
                new AccessProfile(
                    row.getObject("id", UUID.class),
                    person,
                    "PATIENT",
                    "FAMILY_PATIENT",
                    row.getObject("patient_id", UUID.class),
                    null,
                    null,
                    row.getString("first_name") + " " + row.getString("last_name"),
                    true),
            person,
            adultAge,
            adultAge));
    return List.copyOf(result);
  }

  @Override
  public AccessProfile resolve(
      UUID subject,
      Set<String> originalRoles,
      UUID selectedProfileId,
      UUID selectedOrganizationId) {
    List<AccessProfile> available = profiles(subject, originalRoles);
    if (selectedProfileId != null) {
      AccessProfile chosen =
          available.stream()
              .filter(p -> p.id().equals(selectedProfileId))
              .findFirst()
              .orElseThrow(() -> denied("PROFILE_NOT_OWNED"));
      if (selectedOrganizationId != null && !selectedOrganizationId.equals(chosen.organizationId()))
        throw denied("PROFILE_ORGANIZATION_MISMATCH");
      return selectable(chosen);
    }
    if (selectedOrganizationId != null) throw denied("PROFILE_SELECTION_REQUIRED");
    UUID person =
        available.stream()
            .findFirst()
            .map(AccessProfile::personId)
            .orElseGet(
                () -> {
                  var found =
                      jdbc.query(
                          "select id from identity.person where keycloak_user_id=? and"
                              + " status='ACTIVE'",
                          (row, index) -> row.getObject(1, UUID.class),
                          subject);
                  if (found.size() != 1) throw denied("ACTIVE_ACCOUNT_REQUIRED");
                  return found.getFirst();
                });
    // Internal account context is deliberately not returned by profile discovery.
    return new AccessProfile(
        person, person, "ACCOUNT", "ACCOUNT", person, null, null, "Mon compte", true);
  }

  private AccessProfile selectable(AccessProfile profile) {
    // Local administrators may select an inactive establishment to manage its licence/reactivation.
    // Operational controllers still enforce establishment/subscription status separately.
    if (!profile.active() && !"HOSPITAL_ADMIN".equals(profile.type()))
      throw denied("PROFILE_INACTIVE");
    return profile;
  }

  @Override
  public AccessProfile forPerson(UUID personId, UUID profileId) {
    if (personId == null
        || profileId == null
        || !Integer.valueOf(1)
            .equals(
                jdbc.queryForObject(
                    "select count(*) from identity.person where id=? and status='ACTIVE'",
                    Integer.class,
                    personId))) throw denied("ACTIVE_ACCOUNT_REQUIRED");
    return persisted(personId).stream()
        .filter(p -> p.id().equals(profileId) && p.active())
        .findFirst()
        .orElseThrow(() -> denied("PROFILE_INACTIVE_OR_NOT_OWNED"));
  }

  private static AccessDeniedException denied(String code) {
    return new AccessDeniedException(code);
  }
}
