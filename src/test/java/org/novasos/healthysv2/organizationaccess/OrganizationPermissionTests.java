package org.novasos.healthysv2.organizationaccess;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

import java.sql.ResultSet;
import java.time.Instant;
import java.util.*;
import org.junit.jupiter.api.*;
import org.springframework.jdbc.core.*;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationToken;

class OrganizationPermissionTests {
  JdbcTemplate db = mock(JdbcTemplate.class);
  org.novasos.healthysv2.accessprofile.api.AccessProfileResolver profiles =
      mock(org.novasos.healthysv2.accessprofile.api.AccessProfileResolver.class);
  OrganizationPermissionService service = new OrganizationPermissionService(db, profiles);
  UUID org = UUID.randomUUID(), person = UUID.randomUUID(), member = UUID.randomUUID();

  @AfterEach
  void cleanup() {
    SecurityContextHolder.clearContext();
  }

  void login(String role) {
    Jwt jwt =
        Jwt.withTokenValue("test")
            .header("alg", "none")
            .subject(UUID.randomUUID().toString())
            .issuedAt(Instant.now())
            .expiresAt(Instant.now().plusSeconds(60))
            .build();
    SecurityContextHolder.getContext()
        .setAuthentication(
            new JwtAuthenticationToken(jwt, List.of(new SimpleGrantedAuthority("ROLE_" + role))));
  }

  @SuppressWarnings("unchecked")
  void active(String role, boolean explicit) throws Exception {
    login(role);
    when(profiles.resolve(any(UUID.class), anySet(), isNull(), isNull()))
        .thenReturn(
            new org.novasos.healthysv2.accessprofile.api.AccessProfileResolver.AccessProfile(
                member,
                person,
                role,
                "ORGANIZATION_MEMBERSHIP",
                member,
                org,
                "Test",
                "Test",
                true));
    when(db.queryForList(
            contains("select id from licensing.member"), eq(UUID.class), eq(org), eq(person)))
        .thenReturn(List.of(member));
    when(db.query(anyString(), any(ResultSetExtractor.class), any(UUID.class))).thenReturn(person);
    when(db.queryForObject(anyString(), eq(Integer.class), any(UUID.class)))
        .thenAnswer(
            i -> i.getArgument(0, String.class).contains("member_policy") ? (explicit ? 1 : 0) : 1);
    when(db.queryForObject(anyString(), eq(Integer.class), eq(org), eq(person))).thenReturn(0);
    when(db.queryForObject(contains("member_policy"), eq(Integer.class), eq(member), eq(org)))
        .thenReturn(explicit ? 1 : 0);
    when(db.query(anyString(), any(RowMapper.class), eq(member), eq(org), eq(person)))
        .thenAnswer(
            i -> {
              ResultSet rs = mock(ResultSet.class);
              when(rs.getObject(1)).thenReturn(member);
              when(rs.getString(2)).thenReturn(role);
              return List.of(((RowMapper<?>) i.getArgument(1)).mapRow(rs, 0));
            });
  }

  @Test
  void platformAdministratorCannotReadHospitalEvenWithClaim() {
    login("PLATFORM_ADMIN");
    assertThatThrownBy(() -> service.require(org, "invoices", "READ"))
        .isInstanceOf(AccessDeniedException.class)
        .hasMessage("PLATFORM_OPERATIONAL_ACCESS_DENIED");
    verifyNoInteractions(db);
  }

  @Test
  void unknownPermissionFailsClosed() {
    login("CASHIER");
    assertThatThrownBy(() -> service.require(org, "unknown", "READ"))
        .isInstanceOf(IllegalArgumentException.class);
    verifyNoInteractions(db);
  }

  @Test
  void explicitEmptyPolicyRevokesLegacyCashierPrivileges() throws Exception {
    active("CASHIER", true);
    assertThatThrownBy(() -> service.require(org, "invoices", "READ"))
        .isInstanceOf(AccessDeniedException.class);
  }

  @Test
  void readOnlyCustomRoleDoesNotPermitCashMutation() throws Exception {
    active("CASHIER", true);
    doAnswer(
            i -> {
              ResultSet rs = mock(ResultSet.class);
              when(rs.getString(1)).thenReturn("invoices");
              when(rs.getString(2)).thenReturn("READ");
              ((RowCallbackHandler) i.getArgument(1)).processRow(rs);
              return null;
            })
        .when(db)
        .query(
            contains("select distinct p.resource"),
            any(RowCallbackHandler.class),
            eq(member),
            eq(org));
    service.require(org, "invoices", "READ");
    assertThatThrownBy(() -> service.require(org, "invoices", "UPDATE"))
        .isInstanceOf(AccessDeniedException.class);
  }

  @Test
  void expiredLicenseDeniesBeforeReadingCustomRoles() throws Exception {
    active("CASHIER", true);
    when(db.queryForObject(contains("licensing.subscription"), eq(Integer.class), eq(org)))
        .thenReturn(0);
    assertThatThrownBy(() -> service.effective(org))
        .isInstanceOf(AccessDeniedException.class)
        .hasMessage("ACTIVE_SUBSCRIPTION_REQUIRED");
    verify(db, never())
        .query(contains("select distinct p.resource"), any(RowCallbackHandler.class), any(), any());
  }

  @Test
  void accountantLegacyDefaultIsReadOnly() throws Exception {
    active("ACCOUNTANT", false);
    service.require(org, "cashreports", "READ");
    assertThatThrownBy(() -> service.require(org, "invoices", "DELETE"))
        .isInstanceOf(AccessDeniedException.class);
  }

  @Test
  void foreignOrganizationWithoutMembershipCannotResolvePermissions() throws Exception {
    active("CASHIER", true);
    when(db.query(anyString(), any(RowMapper.class), eq(member), eq(org), eq(person)))
        .thenReturn(List.of());
    assertThatThrownBy(() -> service.effective(org))
        .isInstanceOf(AccessDeniedException.class)
        .hasMessage("ORGANIZATION_MEMBERSHIP_REQUIRED");
  }

  @Test
  void backgroundActorResolutionUsesPersistedPolicyWithoutJwt() throws Exception {
    active("DOCTOR", true);
    SecurityContextHolder.clearContext();
    assertThatThrownBy(() -> service.requireForPerson(org, person, "ai", "CREATE"))
        .isInstanceOf(AccessDeniedException.class);
    verify(db)
        .queryForObject(
            contains("identity.person where id=? and status='ACTIVE'"),
            eq(Integer.class),
            eq(person));
  }

  @Test
  void ownerUsingDoctorProfileDoesNotInheritOrganizationAdministratorRights() throws Exception {
    active("DOCTOR", false);
    when(db.queryForObject(
            contains("administrator_person_id"), eq(Integer.class), eq(org), eq(person)))
        .thenReturn(1);
    assertThat(service.effective(org).administrator()).isFalse();
    assertThatThrownBy(() -> service.require(org, "roles", "CREATE"))
        .isInstanceOf(AccessDeniedException.class);
    verify(db, never())
        .queryForObject(
            contains("administrator_person_id"), eq(Integer.class), eq(org), eq(person));
  }

  @Test
  void backgroundWithoutSourceRefusesAmbiguousMemberships() throws Exception {
    active("DOCTOR", false);
    when(db.queryForList(
            contains("select id from licensing.member"), eq(UUID.class), eq(org), eq(person)))
        .thenReturn(List.of(member, UUID.randomUUID()));
    assertThatThrownBy(() -> service.requireForPerson(org, person, "invoices", "READ"))
        .isInstanceOf(AccessDeniedException.class)
        .hasMessage("ACCESS_PROFILE_REQUIRED");
  }
}
