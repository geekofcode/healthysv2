package org.novasos.healthysv2.patient;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

import java.time.Instant;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.ResultSetExtractor;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationToken;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.web.context.request.RequestContextHolder;
import org.springframework.web.context.request.ServletRequestAttributes;

class CurrentOrganizationContextTests {
    @AfterEach void clean() {
        SecurityContextHolder.clearContext();
        RequestContextHolder.resetRequestAttributes();
    }

    @Test
    void selectedOrganizationRequiresLiveAssignmentEvenWithOldJwt() {
        JdbcTemplate jdbc = mock(JdbcTemplate.class);
        UUID subject = UUID.randomUUID(), person = UUID.randomUUID(), selected = UUID.randomUUID();
        Jwt jwt = Jwt.withTokenValue("test").header("alg", "RS256").subject(subject.toString())
                .issuedAt(Instant.now()).expiresAt(Instant.now().plusSeconds(300)).build();
        SecurityContextHolder.getContext().setAuthentication(new JwtAuthenticationToken(jwt,
                List.of(new SimpleGrantedAuthority("ROLE_DOCTOR"))));
        MockHttpServletRequest request = new MockHttpServletRequest();
        request.addHeader("X-Organization-ID", selected.toString());
        RequestContextHolder.setRequestAttributes(new ServletRequestAttributes(request));
        when(jdbc.query(anyString(), org.mockito.ArgumentMatchers.<ResultSetExtractor<UUID>>any(), eq(subject)))
                .thenReturn(person);
        when(jdbc.queryForObject(contains("professional_assignment"), eq(Integer.class), eq(person), eq(selected)))
                .thenReturn(0);
        assertThatThrownBy(() -> new CurrentUserContext(jdbc).current())
                .isInstanceOf(AccessDeniedException.class).hasMessage("PROFESSIONAL_NOT_ASSIGNED");
        when(jdbc.queryForObject(contains("professional_assignment"), eq(Integer.class), eq(person), eq(selected)))
                .thenReturn(1);
        assertThat(new CurrentUserContext(jdbc).current().organizationId()).isEqualTo(selected);
    }
    @Test
    void explicitIndependentContextOverridesJwtOrganizationButHospitalAdminCannotUseIt() {
        JdbcTemplate jdbc = mock(JdbcTemplate.class);
        UUID subject = UUID.randomUUID();
        Jwt jwt = Jwt.withTokenValue("test").header("alg", "RS256").subject(subject.toString())
                .claim("healthys_organization_id", UUID.randomUUID().toString())
                .issuedAt(Instant.now()).expiresAt(Instant.now().plusSeconds(300)).build();
        MockHttpServletRequest request = new MockHttpServletRequest();
        request.addHeader("X-Organization-ID", "independent");
        RequestContextHolder.setRequestAttributes(new ServletRequestAttributes(request));
        SecurityContextHolder.getContext().setAuthentication(new JwtAuthenticationToken(jwt,
                List.of(new SimpleGrantedAuthority("ROLE_DOCTOR"))));
        assertThat(new CurrentUserContext(jdbc).current().organizationId()).isNull();
        verify(jdbc).query(eq("select id from identity.person where keycloak_user_id=? limit 1"),
                org.mockito.ArgumentMatchers.<ResultSetExtractor<UUID>>any(), eq(subject));
        SecurityContextHolder.getContext().setAuthentication(new JwtAuthenticationToken(jwt,
                List.of(new SimpleGrantedAuthority("ROLE_HOSPITAL_ADMIN"))));
        assertThatThrownBy(() -> new CurrentUserContext(jdbc).current())
                .isInstanceOf(AccessDeniedException.class).hasMessage("INDEPENDENT_CONTEXT_DENIED");
    }
}
