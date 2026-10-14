package org.novasos.healthysv2.patient;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.novasos.healthysv2.identity.api.PersonLookup;
import org.novasos.healthysv2.identity.api.PersonResponse;
import org.novasos.healthysv2.shared.api.error.ResourceNotFoundException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.RowMapper;

class PatientDashboardServiceTests {
  private final PatientSelfAccess selves = mock(PatientSelfAccess.class);

  @org.junit.jupiter.api.BeforeEach
  void configureSelectedPatient() {
    service.configureSelfAccess(selves);
  }

  private final PersonLookup identities = mock(PersonLookup.class);
  private final PatientRepository patients = mock(PatientRepository.class);
  private final JdbcTemplate jdbc = mock(JdbcTemplate.class);
  private final PatientAuditService audit = mock(PatientAuditService.class);
  private final PatientDashboardService service =
      new PatientDashboardService(identities, patients, jdbc, audit);

  @Test
  void onlyReadsLinkedPatientAndFiltersInactiveAlertsWithoutExposingPrivateNotes() {
    UUID subject = UUID.randomUUID();
    UUID personId = UUID.randomUUID();
    PersonResponse person = mock(PersonResponse.class);
    when(person.id()).thenReturn(personId);
    when(identities.findMe(subject)).thenReturn(person);
    Patient patient = Patient.create(personId);
    patient.addFlag("MEDICAL", "Active", "HIGH", true, null);
    patient.addFlag("MEDICAL", "Inactive", "HIGH", false, null);
    patient.addAllergy("Peanuts", "FOOD", "Swelling", "HIGH", "ACTIVE", null, null);
    patient.addAllergy("Historical", "FOOD", "None", "LOW", "INACTIVE", null, null);
    patient.addNote(null, "PRIVATE", "Private clinical note", null);
    when(selves.resolve(subject))
        .thenReturn(new PatientSelfAccess.Identity(personId, patient.getId()));
    when(identities.findPerson(personId)).thenReturn(person);
    when(patients.findById(patient.getId())).thenReturn(Optional.of(patient));
    when(jdbc.query(
            anyString(),
            org.mockito.ArgumentMatchers
                .<RowMapper<
                        org.novasos.healthysv2.patient.api.PatientDashboardResponse
                            .DashboardAddress>>
                    any(),
            eq(personId)))
        .thenReturn(List.of());

    var response = service.findMe(subject);

    assertThat(response.person()).isSameAs(person);
    assertThat(response.patient().personId()).isEqualTo(personId);
    assertThat(response.flags()).extracting(flag -> flag.label()).containsExactly("Active");
    assertThat(response.allergies())
        .extracting(allergy -> allergy.allergen())
        .containsExactly("Peanuts");
    assertThat(response.getClass().getRecordComponents())
        .extracting(component -> component.getName())
        .containsExactly("person", "patient", "addresses", "insurances", "flags", "allergies");
    verify(identities).findPerson(personId);
    verify(patients).findById(patient.getId());
    verify(patients, never()).findByPersonId(any());
    verify(patients, never()).save(any());
    verify(audit)
        .access(
            eq(personId),
            eq(patient.getId()),
            isNull(),
            eq("PATIENT_DASHBOARD"),
            eq(patient.getId()),
            eq("READ"),
            eq("PATIENT_SELF"),
            any());
  }

  @Test
  void missingPersonNeverQueriesOrCreatesPatient() {
    UUID subject = UUID.randomUUID();
    when(selves.resolve(subject)).thenThrow(new ResourceNotFoundException("Person", subject));
    assertThatThrownBy(() -> service.findMe(subject)).isInstanceOf(ResourceNotFoundException.class);
    verifyNoInteractions(patients, jdbc, audit);
  }

  @Test
  void missingPatientReturnsNotFoundWithoutProvisioning() {
    UUID subject = UUID.randomUUID();
    UUID personId = UUID.randomUUID();
    PersonResponse person = mock(PersonResponse.class);
    when(person.id()).thenReturn(personId);
    when(identities.findMe(subject)).thenReturn(person);
    UUID selectedPatient = UUID.randomUUID();
    when(selves.resolve(subject))
        .thenReturn(new PatientSelfAccess.Identity(personId, selectedPatient));
    when(patients.findById(selectedPatient)).thenReturn(Optional.empty());
    assertThatThrownBy(() -> service.findMe(subject)).isInstanceOf(ResourceNotFoundException.class);
    verify(patients, never()).save(any());
    verifyNoInteractions(audit);
  }

  @Test
  void childDashboardUsesSelectedChildIdentityAndAuditsTheGuardian() {
    UUID subject = UUID.randomUUID(), guardian = UUID.randomUUID(), childPerson = UUID.randomUUID();
    Patient child = Patient.create(childPerson);
    var childIdentity = mock(PersonResponse.class);
    when(childIdentity.id()).thenReturn(childPerson);
    when(selves.resolve(subject))
        .thenReturn(new PatientSelfAccess.Identity(guardian, child.getId()));
    when(patients.findById(child.getId())).thenReturn(Optional.of(child));
    when(identities.findPerson(childPerson)).thenReturn(childIdentity);
    var response = service.findMe(subject);
    assertThat(response.person()).isSameAs(childIdentity);
    assertThat(response.patient().personId()).isEqualTo(childPerson);
    verify(patients, never()).findByPersonId(guardian);
    verify(audit)
        .access(
            eq(guardian),
            eq(child.getId()),
            isNull(),
            eq("PATIENT_DASHBOARD"),
            eq(child.getId()),
            eq("READ"),
            eq("PATIENT_SELF"),
            any());
  }
}
