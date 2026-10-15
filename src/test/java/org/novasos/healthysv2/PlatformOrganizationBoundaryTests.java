package org.novasos.healthysv2;

import static org.assertj.core.api.Assertions.*;

import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;

class PlatformOrganizationBoundaryTests {
  @AfterEach
  void cleanup() {
    SecurityContextHolder.clearContext();
  }

  void admin() {
    SecurityContextHolder.getContext()
        .setAuthentication(
            new UsernamePasswordAuthenticationToken(
                "admin",
                "",
                List.of(
                    new SimpleGrantedAuthority("ROLE_PLATFORM_ADMIN"),
                    new SimpleGrantedAuthority("ROLE_HOSPITAL_ADMIN"),
                    new SimpleGrantedAuthority("ROLE_DOCTOR"))));
  }

  boolean call(String method, String path) {
    return new PlatformOrganizationBoundary.Guard()
        .preHandle(
            new MockHttpServletRequest(method, path), new MockHttpServletResponse(), new Object());
  }

  @Test
  void mixedRolesNeverGivePlatformAccountsClinicalOrCashAccess() {
    admin();
    for (String path :
        List.of(
            "/api/v1/patients",
            "/api/v1/documents/" + UUID.randomUUID(),
            "/api/v1/care/visits/me",
            "/api/v1/billing/care/cash-report",
            "/api/v1/consultations",
            "/api/v1/professionals",
            "/api/v1/licensing/organizations/" + UUID.randomUUID() + "/team",
            "/api/v1/admin/data-access-logs",
            "/api/v1/admin/audit-logs",
            "/api/v1/persons"))
      assertThatThrownBy(() -> call("GET", path))
          .isInstanceOf(AccessDeniedException.class)
          .hasMessage("PLATFORM_OPERATION_DENIED");
  }

  @Test
  void organizationAccountsStatisticsAndCommercialSubscriptionsRemainAccessible() {
    admin();
    for (String path :
        List.of(
            "/api/v1/organizations",
            "/api/v1/platform/accounts",
            "/api/v1/admin/statistics",
            "/api/v1/admin/platform-audit",
            "/api/v1/admin/account-authentication",
            "/api/v1/admin/subscriptions",
            "/api/v1/admin/contract-templates",
            "/api/v1/admin/contract-templates/" + UUID.randomUUID(),
            "/api/v1/admin/organizations/" + UUID.randomUUID() + "/statistics",
            "/api/v1/licensing/organizations/" + UUID.randomUUID() + "/invoices",
            "/api/v1/persons/me")) assertThat(call("GET", path)).isTrue();
    assertThat(call("POST", "/api/v1/organizations")).isTrue();
  }

  @Test
  void futureUnknownHospitalRoutesFailClosedForPlatformAdmin() {
    admin();
    assertThatThrownBy(() -> call("GET", "/api/v1/new-clinical-module"))
        .isInstanceOf(AccessDeniedException.class);
  }

  @Test
  void selectedDoctorProfilePassesThePlatformBoundaryWithoutPlatformAuthority() {
    SecurityContextHolder.getContext()
        .setAuthentication(
            new UsernamePasswordAuthenticationToken(
                "same-account", "", List.of(new SimpleGrantedAuthority("ROLE_DOCTOR"))));
    assertThat(call("GET", "/api/v1/care/episodes")).isTrue();
  }

  @Test
  void regularHospitalActorPassesToItsOwnPermissionAndClinicalGuards() {
    assertThat(call("GET", "/api/v1/care/episodes")).isTrue();
  }
}
