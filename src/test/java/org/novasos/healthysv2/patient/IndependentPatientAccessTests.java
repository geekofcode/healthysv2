package org.novasos.healthysv2.patient;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

import java.time.Duration;
import java.util.Set;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.novasos.healthysv2.licensing.api.LicensingAccess;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.access.AccessDeniedException;

class IndependentPatientAccessTests {
    private final JdbcTemplate jdbc=mock(JdbcTemplate.class);
    private final CurrentUserContext users=mock(CurrentUserContext.class);
    private final PatientAuditService audit=mock(PatientAuditService.class);
    private final LicensingAccess licensing=mock(LicensingAccess.class);
    private final PatientAccessService service=new PatientAccessService(jdbc,users,audit,licensing,new PatientAccessPolicy(Duration.ofHours(48),Duration.ofHours(1),Duration.ofHours(1)));
    private final UUID patient=UUID.randomUUID();
    private final UUID person=UUID.randomUUID();
    private void context(Set<String> roles,UUID org) {
        when(users.current()).thenReturn(new CurrentUserContext.UserContext(person,org,roles));
        when(jdbc.queryForObject(anyString(),eq(Integer.class),any(Object[].class))).thenReturn(0);
        when(jdbc.queryForObject(eq("select count(*) from patient.patient where id=?"),eq(Integer.class),eq(patient))).thenReturn(1);
    }
    @Test void independentStaffCannotBypassOrganizationLicense() {
        context(Set.of("DOCTOR"),null);
        assertThatThrownBy(()->service.requireAccess(patient,"MEDICAL_RECORD","READ")).isInstanceOf(AccessDeniedException.class).hasMessage("LICENSED_ORGANIZATION_REQUIRED");
        verify(audit).access(eq(person),eq(patient),isNull(),anyString(),eq(patient),eq("DENIED"),anyString(),any());
    }
    @Test void platformAdminDoesNotHaveAutomaticClinicalAccess() {
        context(Set.of("PLATFORM_ADMIN"),null);
        assertThatThrownBy(()->service.requireAccess(patient,"MEDICAL_RECORD","READ")).isInstanceOf(AccessDeniedException.class).hasMessage("ROLE_NOT_AUTHORIZED");
    }
    @Test void hospitalAdministratorNeedsTemporaryGrant() {
        UUID org=UUID.randomUUID(); context(Set.of("HOSPITAL_ADMIN"),org);
        assertThatThrownBy(()->service.requireAccess(patient,"MEDICAL_RECORD","READ")).isInstanceOf(AccessDeniedException.class).hasMessage("CLINICAL_ROLE_REQUIRED");
        verify(licensing).requireMember(org);verify(licensing).requireActive(org);
    }
    @Test void patientSelfAccessSurvivesOrganizationLicenseExpiry() {
        context(Set.of("PATIENT"),null);
        when(jdbc.queryForObject(eq("select count(*) from patient.patient where id=? and person_id=?"),eq(Integer.class),eq(patient),eq(person))).thenReturn(1);
        assertThat(service.requireAccess(patient,"DOCUMENT","READ").reason()).isEqualTo("PATIENT_SELF");
        verifyNoInteractions(licensing);
    }
    @Test void activeGrantMustMatchPersonOrganizationScopeAndDatabaseTime() {
        UUID org=UUID.randomUUID();context(Set.of("HOSPITAL_ADMIN"),org);
        when(jdbc.queryForObject(contains("patient.access_grant"),eq(Integer.class),eq(patient),eq(person),eq(org),eq("DOCUMENT"))).thenReturn(1);
        assertThat(service.requireAccess(patient,"DOCUMENT","READ").reason()).isEqualTo("TEMPORARY_GRANT");
        verify(jdbc).queryForObject(contains("expires_at>clock_timestamp()"),eq(Integer.class),eq(patient),eq(person),eq(org),eq("DOCUMENT"));
    }
    @Test void patientRevocationBlocksDerivedEpisodeAndVisitAccess() {
        UUID org=UUID.randomUUID(); context(Set.of("DOCTOR"),org);
        when(jdbc.queryForObject(contains("patient.access_revocation"),eq(Integer.class),eq(patient),eq(person),eq(org))).thenReturn(1);
        assertThatThrownBy(()->service.requireAccess(patient,"MEDICAL_RECORD","READ")).isInstanceOf(AccessDeniedException.class).hasMessage("PATIENT_REVOKED_ACCESS");
        verify(jdbc,never()).queryForObject(contains("care.care_episode"),eq(Integer.class),any(Object[].class));
    }
    @Test void documentPluralAliasMatchesAcceptedDocumentGrant() {
        UUID org=UUID.randomUUID();context(Set.of("HOSPITAL_ADMIN"),org);
        when(jdbc.queryForObject(contains("patient.access_grant"),eq(Integer.class),eq(patient),eq(person),eq(org),eq("DOCUMENT"))).thenReturn(1);
        assertThat(service.requireAccess(patient,"DOCUMENTS","READ").reason()).isEqualTo("TEMPORARY_GRANT");
    }
    @Test void backgroundActorDoesNotBorrowInteractiveMembership() {
        UUID org=UUID.randomUUID();context(Set.of("DOCTOR"),org);
        assertThatThrownBy(()->service.requireAccessForActor(new CurrentUserContext.UserContext(person,org,Set.of("DOCTOR")),patient,"MEDICAL_RECORD","READ"))
                .isInstanceOf(AccessDeniedException.class).hasMessage("ORGANIZATION_LICENSE_OR_MEMBERSHIP_REQUIRED");
        verifyNoInteractions(users,licensing);
    }
    @Test void backgroundActorRechecksPatientRevocationAndAuditsDenial() {
        UUID org=UUID.randomUUID();context(Set.of("DOCTOR"),org);
        when(jdbc.queryForObject(contains("licensing.member"),eq(Integer.class),eq(org),eq(person))).thenReturn(1);
        when(jdbc.queryForObject(contains("patient.access_revocation"),eq(Integer.class),eq(patient),eq(person),eq(org))).thenReturn(1);
        assertThatThrownBy(()->service.requireAccessForActor(new CurrentUserContext.UserContext(person,org,Set.of("DOCTOR")),patient,"MEDICAL_RECORD","READ"))
                .isInstanceOf(AccessDeniedException.class).hasMessage("PATIENT_REVOKED_ACCESS");
        verifyNoInteractions(users);
        verify(licensing).requireActive(org);
        verify(licensing,never()).requireMember(any());
        verify(audit).access(eq(person),eq(patient),eq(org),eq("MEDICAL_RECORD"),eq(patient),eq("DENIED"),eq("PATIENT_REVOKED_ACCESS"),any());
    }
}
