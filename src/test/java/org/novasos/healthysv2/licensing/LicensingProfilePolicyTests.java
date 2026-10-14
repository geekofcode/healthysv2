package org.novasos.healthysv2.licensing;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.novasos.healthysv2.licensing.api.LicensingDtos.TeamRequest;
import org.novasos.healthysv2.organizationaccess.api.OrganizationPermissionAccess;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.ResultSetExtractor;
import org.springframework.jdbc.core.RowMapper;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationToken;

class LicensingProfilePolicyTests {
  private final UUID organization = UUID.randomUUID(),
      actor = UUID.randomUUID(),
      subject = UUID.randomUUID();
  private final JdbcTemplate jdbc = mock(JdbcTemplate.class);
  private final OrganizationPermissionAccess permissions = mock(OrganizationPermissionAccess.class);
  private final LicensingService service = new LicensingService(jdbc);

  @SuppressWarnings("unchecked")
  private void setup() {
    service.permissions(permissions);
    doReturn(List.of(organization))
        .when(jdbc)
        .query(contains("for update"), any(RowMapper.class), eq(organization));
    doReturn(actor)
        .when(jdbc)
        .query(contains("keycloak_user_id"), any(ResultSetExtractor.class), eq(subject));
    when(jdbc.queryForObject(anyString(), eq(Integer.class), eq(organization))).thenReturn(10);
    when(jdbc.queryForMap(anyString(), any(UUID.class)))
        .thenReturn(Map.of("id", UUID.randomUUID()));
    Jwt jwt = Jwt.withTokenValue("token").header("alg", "none").subject(subject.toString()).build();
    SecurityContextHolder.getContext()
        .setAuthentication(new JwtAuthenticationToken(jwt, List.of()));
  }

  @AfterEach
  void clear() {
    SecurityContextHolder.clearContext();
  }

  @Test
  void invitationCreatesAnExplicitEmptyPolicyInsteadOfInheritingLegacyPermissions() {
    setup();
    service.invite(organization, new TeamRequest(actor, null, "NURSE"));
    verify(permissions, times(2)).require(organization, "employees", "CREATE");
    verify(jdbc)
        .update(
            contains("insert into organizationaccess.member_policy"),
            any(UUID.class),
            eq(organization));
  }

  @Test
  void revocationWhileWaitingForOrganizationLockStopsEmployeeCreation() {
    setup();
    doNothing()
        .doThrow(new AccessDeniedException("REVOKED"))
        .when(permissions)
        .require(organization, "employees", "CREATE");
    assertThrows(
        AccessDeniedException.class,
        () -> service.invite(organization, new TeamRequest(actor, null, "NURSE")));
    verify(jdbc, never()).update(contains("insert into licensing.member"), any(Object[].class));
  }
}
