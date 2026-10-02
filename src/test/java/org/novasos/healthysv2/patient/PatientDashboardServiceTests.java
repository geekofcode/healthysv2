package org.novasos.healthysv2.patient;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.Mockito.*;
import static org.mockito.ArgumentMatchers.*;

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
    private final PersonLookup identities = mock(PersonLookup.class);
    private final PatientRepository patients = mock(PatientRepository.class);
    private final JdbcTemplate jdbc = mock(JdbcTemplate.class);
    private final PatientAuditService audit = mock(PatientAuditService.class);
    private final PatientDashboardService service = new PatientDashboardService(identities, patients, jdbc, audit);

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
        when(patients.findByPersonId(personId)).thenReturn(Optional.of(patient));
        when(jdbc.query(anyString(), org.mockito.ArgumentMatchers.<RowMapper<org.novasos.healthysv2.patient.api.PatientDashboardResponse.DashboardAddress>>any(), eq(personId))).thenReturn(List.of());

        var response = service.findMe(subject);

        assertThat(response.person()).isSameAs(person);
        assertThat(response.patient().personId()).isEqualTo(personId);
        assertThat(response.flags()).extracting(flag -> flag.label()).containsExactly("Active");
        assertThat(response.allergies()).extracting(allergy -> allergy.allergen()).containsExactly("Peanuts");
        assertThat(response.getClass().getRecordComponents()).extracting(component -> component.getName())
                .containsExactly("person", "patient", "addresses", "insurances", "flags", "allergies");
        verify(identities).findMe(subject);
        verify(patients).findByPersonId(personId);
        verify(patients, never()).findById(any());
        verify(patients, never()).save(any());
        verify(audit).access(eq(personId), eq(patient.getId()), isNull(), eq("PATIENT_DASHBOARD"),
                eq(patient.getId()), eq("READ"), eq("PATIENT_SELF"), any());
    }

    @Test
    void missingPersonNeverQueriesOrCreatesPatient() {
        UUID subject = UUID.randomUUID();
        when(identities.findMe(subject)).thenThrow(new ResourceNotFoundException("Person", subject));
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
        when(patients.findByPersonId(personId)).thenReturn(Optional.empty());
        assertThatThrownBy(() -> service.findMe(subject)).isInstanceOf(ResourceNotFoundException.class);
        verify(patients, never()).save(any());
        verifyNoInteractions(audit);
    }
}
