package org.novasos.healthysv2.family;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.time.LocalDate;
import java.util.HexFormat;
import java.util.UUID;
import org.novasos.healthysv2.accessprofile.api.AccessProfileResolver.AccessProfile;
import org.novasos.healthysv2.family.api.PatientProfileRequest;
import org.novasos.healthysv2.shared.api.error.BusinessRuleException;
import org.novasos.healthysv2.shared.api.error.ConflictException;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@Transactional
class FamilyPatientProfileService {
  private final JdbcTemplate jdbc;
  private final int adultAge;

  FamilyPatientProfileService(JdbcTemplate jdbc, @Value("${PATIENT_ADULT_AGE:18}") int adultAge) {
    if (adultAge < 16 || adultAge > 25)
      throw new IllegalArgumentException("Invalid patient adult age");
    this.jdbc = jdbc;
    this.adultAge = adultAge;
  }

  AccessProfile create(UUID subject, PatientProfileRequest request, String key) {
    validBirth(request.birthDate(), LocalDate.now());
    var accounts =
        jdbc.query(
            "select id,birth_date from identity.person where keycloak_user_id=? and status='ACTIVE'"
                + " for update",
            (row, index) ->
                new Account(row.getObject(1, UUID.class), row.getObject(2, LocalDate.class)),
            subject);
    if (accounts.size() != 1) throw new AccessDeniedException("ACTIVE_ACCOUNT_REQUIRED");
    Account account = accounts.getFirst();
    if (request.kind() == PatientProfileRequest.Kind.SELF) return self(account, request);
    return minor(account, request, key);
  }

  private AccessProfile self(Account account, PatientProfileRequest request) {
    var existing =
        jdbc.query(
            "select id,status from patient.patient where person_id=?",
            (row, index) -> new ExistingPatient(row.getObject(1, UUID.class), row.getString(2)),
            account.id());
    if (!existing.isEmpty()) {
      if (!"ACTIVE".equals(existing.getFirst().status()))
        throw new ConflictException("PATIENT_PROFILE_INACTIVE", "error.patient.inactive");
      return selfProfile(account.id(), existing.getFirst().id());
    }
    if (account.birthDate() != null && !account.birthDate().equals(request.birthDate()))
      throw new ConflictException(
          "ACCOUNT_BIRTH_DATE_MISMATCH", "error.account.birth-date.mismatch");
    jdbc.update(
        "update identity.person set"
            + " first_name=?,last_name=?,birth_date=?,gender=?,updated_at=now(),version=version+1"
            + " where id=?",
        request.firstName().trim(),
        request.lastName().trim(),
        request.birthDate(),
        request.gender(),
        account.id());
    UUID patient = createPatient(account.id());
    return selfProfile(account.id(), patient);
  }

  private AccessProfile minor(Account account, PatientProfileRequest request, String key) {
    if (key == null || key.isBlank() || key.length() > 128) throw rule("IDEMPOTENCY_KEY_REQUIRED");
    if (request.guardianRelationship() == null) throw rule("GUARDIAN_RELATIONSHIP_REQUIRED");
    LocalDate parentBirth =
        account.birthDate() == null ? request.guardianBirthDate() : account.birthDate();
    if (parentBirth == null) throw rule("GUARDIAN_BIRTH_DATE_REQUIRED");
    validBirth(parentBirth, LocalDate.now());
    if (request.guardianBirthDate() != null && !parentBirth.equals(request.guardianBirthDate()))
      throw rule("GUARDIAN_BIRTH_DATE_MISMATCH");
    if (!isAdult(parentBirth, LocalDate.now(), adultAge)) throw rule("ADULT_GUARDIAN_REQUIRED");
    if (isAdult(request.birthDate(), LocalDate.now(), adultAge))
      throw rule("MINOR_PATIENT_REQUIRED");
    String fingerprint = fingerprint(request);
    var replay =
        jdbc.query(
            """
            select id,patient_id,request_fingerprint,status from family.guardianship
            where guardian_person_id=? and idempotency_key=?
            """,
            (row, index) ->
                new Replay(
                    row.getObject(1, UUID.class),
                    row.getObject(2, UUID.class),
                    row.getString(3),
                    row.getString(4)),
            account.id(),
            key.trim());
    if (!replay.isEmpty()) {
      Replay previous = replay.getFirst();
      if (!fingerprint.equals(previous.fingerprint()))
        throw new ConflictException("IDEMPOTENCY_KEY_CONFLICT", "error.idempotency.conflict");
      if (!"ACTIVE".equals(previous.status())) throw rule("GUARDIANSHIP_REVOKED");
      return familyProfile(
          previous.id(),
          account.id(),
          previous.patientId(),
          request.firstName(),
          request.lastName());
    }
    if (account.birthDate() == null)
      jdbc.update(
          "update identity.person set birth_date=?,updated_at=now(),version=version+1 where id=?",
          parentBirth,
          account.id());
    UUID child = UUID.randomUUID(), relation = UUID.randomUUID();
    jdbc.update(
        "insert into"
            + " identity.person(id,person_number,first_name,last_name,birth_date,gender,status)"
            + " values(?,?,?,?,?,?,'ACTIVE')",
        child,
        "PER-" + child.toString().replace("-", ""),
        request.firstName().trim(),
        request.lastName().trim(),
        request.birthDate(),
        request.gender());
    UUID patient = createPatient(child);
    jdbc.update(
        """
        insert into family.guardianship(id,guardian_person_id,patient_id,relationship,adulthood_at,idempotency_key,request_fingerprint)
        values(?,?,?,?,?,?,?)
        """,
        relation,
        account.id(),
        patient,
        request.guardianRelationship().name(),
        request.birthDate().plusYears(adultAge),
        key.trim(),
        fingerprint);
    return familyProfile(relation, account.id(), patient, request.firstName(), request.lastName());
  }

  private UUID createPatient(UUID person) {
    UUID patient = UUID.randomUUID();
    jdbc.update(
        "insert into patient.patient(id,person_id,patient_number,status) values(?,?,?,'ACTIVE')",
        patient,
        person,
        "PAT-" + patient.toString().replace("-", ""));
    return patient;
  }

  private static AccessProfile selfProfile(UUID person, UUID patient) {
    return new AccessProfile(
        patient, person, "PATIENT", "PATIENT", patient, null, null, "Patient", true);
  }

  private static AccessProfile familyProfile(
      UUID relation, UUID guardian, UUID patient, String first, String last) {
    return new AccessProfile(
        relation,
        guardian,
        "PATIENT",
        "FAMILY_PATIENT",
        patient,
        null,
        null,
        first.trim() + " " + last.trim(),
        true);
  }

  static boolean isAdult(LocalDate birth, LocalDate today, int age) {
    return !birth.plusYears(age).isAfter(today);
  }

  private static void validBirth(LocalDate birth, LocalDate today) {
    if (birth == null || birth.isAfter(today) || birth.isBefore(today.minusYears(130)))
      throw rule("INVALID_BIRTH_DATE");
  }

  private static String fingerprint(PatientProfileRequest request) {
    try {
      String value =
          request.kind()
              + "\u0000"
              + request.firstName().trim()
              + "\u0000"
              + request.lastName().trim()
              + "\u0000"
              + request.birthDate()
              + "\u0000"
              + request.gender()
              + "\u0000"
              + request.guardianRelationship();
      return HexFormat.of()
          .formatHex(
              MessageDigest.getInstance("SHA-256").digest(value.getBytes(StandardCharsets.UTF_8)));
    } catch (java.security.NoSuchAlgorithmException exception) {
      throw new IllegalStateException(exception);
    }
  }

  private static BusinessRuleException rule(String code) {
    return new BusinessRuleException(code, "error.patient-profile.invalid");
  }

  private record Account(UUID id, LocalDate birthDate) {}

  private record ExistingPatient(UUID id, String status) {}

  private record Replay(UUID id, UUID patientId, String fingerprint, String status) {}
}
