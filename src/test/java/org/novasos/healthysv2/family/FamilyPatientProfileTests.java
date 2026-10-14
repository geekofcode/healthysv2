package org.novasos.healthysv2.family;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

import java.sql.ResultSet;
import java.time.LocalDate;
import java.util.Arrays;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.novasos.healthysv2.family.api.PatientProfileRequest;
import org.novasos.healthysv2.family.api.PatientProfileRequest.Kind;
import org.novasos.healthysv2.family.api.PatientProfileRequest.Relationship;
import org.novasos.healthysv2.shared.api.error.BusinessRuleException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.RowMapper;

class FamilyPatientProfileTests {
  private final UUID subject = UUID.randomUUID(), parent = UUID.randomUUID();
  private final JdbcTemplate jdbc = mock(JdbcTemplate.class);
  private final FamilyPatientProfileService service = new FamilyPatientProfileService(jdbc, 18);

  private PatientProfileRequest child(Relationship relation, LocalDate parentBirth) {
    return new PatientProfileRequest(
        Kind.MINOR, "Child", "One", LocalDate.now().minusYears(5), null, relation, parentBirth);
  }

  @SuppressWarnings("unchecked")
  private void parentBorn(LocalDate birth) throws Exception {
    ResultSet row = mock(ResultSet.class);
    when(row.getObject(1, UUID.class)).thenReturn(parent);
    when(row.getObject(2, LocalDate.class)).thenReturn(birth);
    doAnswer(
            call -> {
              RowMapper<?> mapper = call.getArgument(1);
              return List.of(mapper.mapRow(row, 0));
            })
        .when(jdbc)
        .query(anyString(), any(RowMapper.class), eq(subject));
  }

  @Test
  void adulthoodBeginsExactlyOnTheConfiguredBirthday() {
    LocalDate today = LocalDate.of(2026, 10, 14);
    assertTrue(FamilyPatientProfileService.isAdult(today.minusYears(18), today, 18));
    assertFalse(FamilyPatientProfileService.isAdult(today.minusYears(18).plusDays(1), today, 18));
    assertTrue(FamilyPatientProfileService.isAdult(today.minusYears(21), today, 21));
  }

  @Test
  void minorCannotActAsGuardian() throws Exception {
    parentBorn(LocalDate.now().minusYears(17));
    assertThrows(
        BusinessRuleException.class,
        () -> service.create(subject, child(Relationship.PARENT, null), "key"));
    verify(jdbc, never()).update(anyString(), any(Object[].class));
  }

  @Test
  void adultGuardianRelationshipAndIdempotencyAreMandatory() throws Exception {
    parentBorn(LocalDate.now().minusYears(30));
    assertThrows(
        BusinessRuleException.class, () -> service.create(subject, child(null, null), "key"));
    assertThrows(
        BusinessRuleException.class,
        () -> service.create(subject, child(Relationship.PARENT, null), null));
    verify(jdbc, never()).update(anyString(), any(Object[].class));
  }

  @Test
  void suppliedGuardianBirthDateCannotOverrideKnownBirthDate() throws Exception {
    parentBorn(LocalDate.now().minusYears(17));
    assertThrows(
        BusinessRuleException.class,
        () ->
            service.create(
                subject, child(Relationship.PARENT, LocalDate.now().minusYears(30)), "key"));
  }

  @Test
  void guardianMayCreateOnlyAMinorPatient() throws Exception {
    parentBorn(LocalDate.now().minusYears(45));
    var adult =
        new PatientProfileRequest(
            Kind.MINOR,
            "Adult",
            "Other",
            LocalDate.now().minusYears(18),
            null,
            Relationship.GUARDIAN,
            null);
    assertThrows(BusinessRuleException.class, () -> service.create(subject, adult, "key"));
    verify(jdbc, never()).update(anyString(), any(Object[].class));
  }

  @Test
  void requestCannotClaimAnExistingPatientOrPersonIdentifier() {
    assertFalse(
        Arrays.stream(PatientProfileRequest.class.getRecordComponents())
            .anyMatch(component -> component.getType().equals(UUID.class)));
  }
}
