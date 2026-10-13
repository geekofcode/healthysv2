package org.novasos.healthysv2;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;
import java.util.*;
import org.junit.jupiter.api.Test;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.RowMapper;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.oauth2.jwt.Jwt;

class OrganizationAuthoritiesTests {
    @Test void selectedMembershipReplacesStaleGlobalStaffRoles() {
        var jdbc=mock(JdbcTemplate.class);
        when(jdbc.query(anyString(),any(RowMapper.class),any(),any(),any(),any())).thenReturn(List.of("CASHIER"));
        var token=Jwt.withTokenValue("test").header("alg","none").subject(UUID.randomUUID().toString())
                .claim("organization_id",UUID.randomUUID().toString()).build();
        var result=new OrganizationAuthorities(jdbc).augment(token,List.of(new SimpleGrantedAuthority("ROLE_DOCTOR"),new SimpleGrantedAuthority("ROLE_PATIENT")));
        assertThat(result).extracting("authority").containsExactlyInAnyOrder("ROLE_CASHIER","ROLE_PATIENT");
    }
    @Test void forgedOrganizationClaimDoesNotKeepAdministrativeRole() {
        var jdbc=mock(JdbcTemplate.class);
        when(jdbc.query(anyString(),any(RowMapper.class),any(),any(),any(),any())).thenReturn(List.of());
        var token=Jwt.withTokenValue("test").header("alg","none").subject(UUID.randomUUID().toString())
                .claim("organization_id",UUID.randomUUID().toString()).build();
        var result=new OrganizationAuthorities(jdbc).augment(token,List.of(new SimpleGrantedAuthority("ROLE_HOSPITAL_ADMIN")));
        assertThat(result).isEmpty();
    }
}
