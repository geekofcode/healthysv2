package org.novasos.healthysv2.patienthealth;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

import java.time.*;
import java.util.*;
import org.junit.jupiter.api.Test;
import org.novasos.healthysv2.accessprofile.api.AccessProfileResolver;
import org.novasos.healthysv2.identity.api.*;
import org.novasos.healthysv2.organizationaccess.api.OrganizationPermissionAccess;
import org.novasos.healthysv2.patient.*;
import org.novasos.healthysv2.patienthealth.api.PatientHealthDtos.*;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.access.AccessDeniedException;

class PatientHealthTests {
  JdbcTemplate db = mock(JdbcTemplate.class);
  CurrentUserContext users = mock(CurrentUserContext.class);
  AccessProfileResolver profiles = mock(AccessProfileResolver.class);
  SelectedPatientIdentityAccess identities = mock(SelectedPatientIdentityAccess.class);
  PatientHealthService service =
      new PatientHealthService(
          db,
          users,
          profiles,
          mock(PatientAccessService.class),
          mock(OrganizationPermissionAccess.class),
          identities);
  UUID parent = UUID.randomUUID(),
      child = UUID.randomUUID(),
      childPerson = UUID.randomUUID(),
      profile = UUID.randomUUID();

  void family() {
    when(users.current())
        .thenReturn(
            new CurrentUserContext.UserContext(parent, null, Set.of("PATIENT"), profile, child));
    when(profiles.forPerson(parent, profile))
        .thenReturn(
            new AccessProfileResolver.AccessProfile(
                profile, parent, "PATIENT", "FAMILY_PATIENT", child, null, null, "Enfant", true));
    when(db.queryForObject(
            contains("select person_id from patient.patient"), eq(UUID.class), eq(child)))
        .thenReturn(childPerson);
  }

  @Test
  void guardianPersonalReadTargetsChildPersonRatherThanGuardianAccount() {
    family();
    service.personal();
    verify(identities).profilePerson(childPerson);
    verify(identities, never()).profilePerson(parent);
  }

  @Test
  void guardianCannotChangeChildBirthDateToExtendMinorAccess() {
    family();
    when(db.queryForObject(contains("select birth_date"), eq(java.sql.Date.class), eq(childPerson)))
        .thenReturn(java.sql.Date.valueOf("2010-01-01"));
    var input =
        new UpdatePersonProfileRequest(
            "Enfant",
            null,
            "Test",
            "OTHER",
            LocalDate.of(2015, 1, 1),
            null,
            null,
            List.of(),
            List.of());
    assertThatThrownBy(() -> service.updatePersonal(input))
        .isInstanceOf(AccessDeniedException.class)
        .hasMessage("FAMILY_BIRTH_DATE_REQUIRES_HOSPITAL_CORRECTION");
    verify(identities, never()).updatePerson(any(), any());
  }

  @Test
  void mismatchedSelectedPatientSourceCannotReadAnotherChild() {
    family();
    when(profiles.forPerson(parent, profile))
        .thenReturn(
            new AccessProfileResolver.AccessProfile(
                profile,
                parent,
                "PATIENT",
                "FAMILY_PATIENT",
                UUID.randomUUID(),
                null,
                null,
                "Autre enfant",
                true));
    assertThatThrownBy(() -> service.personal()).isInstanceOf(AccessDeniedException.class);
    verifyNoInteractions(db, identities);
  }

  @Test
  void selfMeasurementCannotCorrectAClinicianRecord() {
    family();
    UUID parameter = UUID.randomUUID();
    when(db.queryForList(
            contains("select * from patienthealth.parameter"), eq(parameter), eq(child)))
        .thenReturn(
            List.of(
                Map.of("code", "weight", "value_type", "NUMERIC", "label", "Poids", "unit", "kg")));
    when(db.queryForObject(
            contains("self_reported=?"),
            eq(Integer.class),
            any(),
            eq(child),
            eq(parameter),
            eq(true),
            isNull(),
            isNull()))
        .thenReturn(0);
    assertThatThrownBy(
            () ->
                service.appendSelf(
                    new ObservationInput(parameter, "20", Instant.now(), null, UUID.randomUUID())))
        .isInstanceOf(IllegalArgumentException.class)
        .hasMessage("INVALID_OBSERVATION_CORRECTION");
    verify(db, never()).update(anyString(), any(Object[].class));
  }

  @Test
  void valuesAreTypedAndNotInterpretedAsClinicalNormalRanges() {
    assertThat(PatientHealthService.validateValue("weight", "NUMERIC", "20.50")).isEqualTo("20.5");
    assertThatThrownBy(() -> PatientHealthService.validateValue("weight", "NUMERIC", "unknown"))
        .isInstanceOf(IllegalArgumentException.class);
    assertThatThrownBy(
            () -> PatientHealthService.validateValue("oxygenSaturation", "NUMERIC", "101"))
        .isInstanceOf(IllegalArgumentException.class);
    assertThatThrownBy(() -> PatientHealthService.validateValue("CUSTOM_x", "BOOLEAN", "yes"))
        .isInstanceOf(IllegalArgumentException.class);
  }
}
