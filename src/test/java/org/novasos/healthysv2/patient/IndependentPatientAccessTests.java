package org.novasos.healthysv2.patient;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

import java.util.Set;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.ResultSetExtractor;
import org.springframework.security.access.AccessDeniedException;

class IndependentPatientAccessTests {
    private final JdbcTemplate jdbc = mock(JdbcTemplate.class);
    private final CurrentUserContext users = mock(CurrentUserContext.class);
    private final PatientAuditService audit = mock(PatientAuditService.class);
    private final PatientAccessService service = new PatientAccessService(jdbc, users, audit);
    private final UUID patient = UUID.randomUUID();
    private final UUID person = UUID.randomUUID();
    private final UUID professional = UUID.randomUUID();

    private void independent() {
        when(users.current()).thenReturn(new CurrentUserContext.UserContext(person, null, Set.of("DOCTOR")));
        when(jdbc.queryForObject(anyString(), eq(Integer.class), any(Object[].class))).thenReturn(1);
        when(jdbc.query(anyString(), org.mockito.ArgumentMatchers.<ResultSetExtractor<UUID>>any(), eq(person)))
                .thenReturn(professional);
    }

    @Test
    void independentRequiresPersonalRelationshipAndPersonalConsent() {
        independent();
        assertThat(service.requireAccess(patient, "PRESCRIPTIONS", "WRITE").reason())
                .isEqualTo("INDEPENDENT_CARE_CONTEXT_AUTHORIZED");
        verify(jdbc).queryForObject(contains("organization_id is null"), eq(Integer.class), eq(patient), eq(professional));
        verify(jdbc).queryForObject(contains("grantee_organization_id is null"), eq(Integer.class), eq(patient), eq(person), eq("PRESCRIPTIONS"));
    }

    @Test
    void revokedConsentRemovesIndependentAccess() {
        independent();
        when(jdbc.queryForObject(contains("grantee_organization_id is null"), eq(Integer.class), eq(patient), eq(person), eq("MEDICAL_RECORD")))
                .thenReturn(0);
        assertThatThrownBy(() -> service.requireAccess(patient, "MEDICAL_RECORD", "READ"))
                .isInstanceOf(AccessDeniedException.class).hasMessage("CONSENT_MISSING_OR_INACTIVE");
    }

    @Test
    void organizationProfessionalWithEndedAssignmentIsDenied() {
        independent();
        UUID organization = UUID.randomUUID();
        when(users.current()).thenReturn(new CurrentUserContext.UserContext(person, organization, Set.of("DOCTOR")));
        when(jdbc.queryForObject(contains("professional_assignment"), eq(Integer.class), eq(professional), eq(organization)))
                .thenReturn(0);
        assertThatThrownBy(() -> service.requireAccess(patient, "MEDICAL_RECORD", "READ"))
                .isInstanceOf(AccessDeniedException.class).hasMessage("PROFESSIONAL_NOT_ASSIGNED");
    }
}
