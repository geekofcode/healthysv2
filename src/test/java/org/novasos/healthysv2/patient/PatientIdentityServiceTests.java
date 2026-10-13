package org.novasos.healthysv2.patient;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

import com.fasterxml.jackson.databind.ObjectMapper;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.novasos.healthysv2.identity.api.ReceptionIdentityEditor;
import org.novasos.healthysv2.licensing.api.LicensingAccess;
import org.novasos.healthysv2.patient.api.PatientIdentityDtos.*;
import org.novasos.healthysv2.shared.api.error.ConflictException;
import org.springframework.data.domain.PageRequest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.RowMapper;
import org.springframework.security.access.AccessDeniedException;

class PatientIdentityServiceTests {
    private final JdbcTemplate jdbc = mock(JdbcTemplate.class);
    private final PatientRepository patients = mock(PatientRepository.class);
    private final CurrentUserContext users = mock(CurrentUserContext.class);
    private final LicensingAccess licensing = mock(LicensingAccess.class);
    private final PatientAuditService audit = mock(PatientAuditService.class);
    private final PatientIdentityService service = new PatientIdentityService(jdbc, patients, users,
            licensing, audit, mock(ReceptionIdentityEditor.class));
    private final UUID organization = UUID.randomUUID();

    @Test void receptionSearchRequiresLicensedMembershipWithoutClinicalGrant() {
        when(users.current()).thenReturn(new CurrentUserContext.UserContext(UUID.randomUUID(), organization, Set.of("NURSE")));
        var result = service.search("", PageRequest.of(0, 20));
        assertThat(result.content()).isEmpty();
        verify(licensing).requireMember(organization);
        verify(licensing).requireActive(organization);
        verifyNoInteractions(patients, jdbc);
    }

    @Test void staffCannotSearchWithoutAnEstablishment() {
        when(users.current()).thenReturn(new CurrentUserContext.UserContext(UUID.randomUUID(), null, Set.of("NURSE")));
        assertThatThrownBy(() -> service.search("Ada", PageRequest.of(0, 20)))
                .isInstanceOf(AccessDeniedException.class);
        verifyNoInteractions(patients, jdbc);
    }

    @Test void rejectsSecondPatientForTheSamePersonEvenAtAnotherHospital() {
        UUID person = UUID.randomUUID();
        when(users.current()).thenReturn(new CurrentUserContext.UserContext(UUID.randomUUID(), organization, Set.of("NURSE")));
        when(jdbc.query(anyString(), org.mockito.ArgumentMatchers.<RowMapper<UUID>>any(), eq(person))).thenReturn(List.of(person));
        when(patients.existsByPersonId(person)).thenReturn(true);
        assertThatThrownBy(() -> service.create(new CreateIdentityRequest(person, List.of())))
                .isInstanceOf(ConflictException.class).hasMessage("error.patient.person.exists");
        verify(patients, never()).saveAndFlush(any());
    }

    @Test void identityResponseNeverSerializesClinicalFields() throws Exception {
        var identity = new PatientIdentityResponse(UUID.randomUUID(), UUID.randomUUID(), "PAT-1", "Ada", "Lovelace",
                null, null, "ada@example.com", null, "ACTIVE", List.of());
        var json = new ObjectMapper().valueToTree(identity);
        assertThat(json.has("bloodGroup")).isFalse();
        assertThat(json.has("allergies")).isFalse();
        assertThat(json.has("medicalHistories")).isFalse();
        assertThat(json.has("keycloakUserId")).isFalse();
        assertThat(json.path("firstName").asText()).isEqualTo("Ada");
    }
}
