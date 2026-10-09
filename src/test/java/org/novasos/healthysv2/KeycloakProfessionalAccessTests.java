package org.novasos.healthysv2;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.RowMapper;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.authority.SimpleGrantedAuthority;

class KeycloakProfessionalAccessTests {
    private final UUID subject=UUID.randomUUID();
    private final JdbcTemplate jdbc=mock(JdbcTemplate.class);
    private final KeycloakProfessionalAccess access=new KeycloakProfessionalAccess(jdbc);
    private final List<GrantedAuthority> roles=List.of(new SimpleGrantedAuthority("ROLE_DOCTOR"),
            new SimpleGrantedAuthority("ROLE_NURSE"),new SimpleGrantedAuthority("ROLE_PATIENT"));
    @Test
    void limitsKeycloakWritesToClinicalRoles() {
        for (String role : List.of("medecin","nurse","laboratoire")) KeycloakProfessionalRoleSync.validateRole(role);
        for (String role : List.of("admin","hopital","patient","DOCTOR"))
            assertThatThrownBy(() -> KeycloakProfessionalRoleSync.validateRole(role)).isInstanceOf(IllegalArgumentException.class);
    }
    @Test
    void staleClinicalTokenIsDeniedImmediatelyAfterSuspension() {
        state("SUSPENDED","SUSPENDED","medecin");
        assertThat(access.filter(subject.toString(),roles)).extracting(GrantedAuthority::getAuthority).containsExactly("ROLE_PATIENT");
    }
    @Test
    void approvedApplicationRetainsOnlyItsExistingTokenRole() {
        state("APPROVED","ACTIVE","medecin");
        assertThat(access.filter(subject.toString(),roles)).extracting(GrantedAuthority::getAuthority).containsExactly("ROLE_DOCTOR","ROLE_PATIENT");
        assertThat(access.filter(subject.toString(),List.of(new SimpleGrantedAuthority("ROLE_PATIENT"))))
                .extracting(GrantedAuthority::getAuthority).containsExactly("ROLE_PATIENT");
    }
    @Test
    void pendingOrRejectedApplicationCannotUseManuallyGrantedClinicalRole() {
        for (String status : List.of("DRAFT","SUBMITTED","REJECTED")) {
            state(status,null,"medecin");
            assertThat(access.filter(subject.toString(),roles)).extracting(GrantedAuthority::getAuthority).containsExactly("ROLE_PATIENT");
        }
    }
    @Test
    void existingManuallyManagedProfessionalsRemainCompatible() {
        when(jdbc.query(anyString(), org.mockito.ArgumentMatchers.<RowMapper<KeycloakProfessionalAccess.State>>any(), eq(subject)))
                .thenReturn(List.of());
        assertThat(access.filter(subject.toString(),roles)).isEqualTo(roles);
    }
    private void state(String status,String professionalStatus,String profession) {
        when(jdbc.query(anyString(), org.mockito.ArgumentMatchers.<RowMapper<KeycloakProfessionalAccess.State>>any(), eq(subject)))
                .thenReturn(List.of(new KeycloakProfessionalAccess.State(status,professionalStatus,profession)));
    }
}
