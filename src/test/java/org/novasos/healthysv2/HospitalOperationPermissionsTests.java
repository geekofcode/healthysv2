package org.novasos.healthysv2;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.Mockito.*;

import java.util.Set;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.novasos.healthysv2.organizationaccess.api.OrganizationPermissionAccess;
import org.novasos.healthysv2.patient.CurrentUserContext;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;
import org.springframework.security.access.AccessDeniedException;

class HospitalOperationPermissionsTests {
  @Test
  void readCashGrantCannotAuthorizeInvoiceCreation() {
    var org = UUID.randomUUID();
    var users = mock(CurrentUserContext.class);
    var access = mock(OrganizationPermissionAccess.class);
    when(users.current())
        .thenReturn(new CurrentUserContext.UserContext(UUID.randomUUID(), org, Set.of()));
    doThrow(new AccessDeniedException("ORGANIZATION_PERMISSION_DENIED"))
        .when(access)
        .require(org, "invoices", "CREATE");
    var guard = new HospitalOperationPermissions.Guard(access, users);
    assertThat(
            guard.preHandle(
                new MockHttpServletRequest("GET", "/api/v1/billing/care/cash-report"),
                new MockHttpServletResponse(),
                new Object()))
        .isTrue();
    assertThatThrownBy(
            () ->
                guard.preHandle(
                    new MockHttpServletRequest(
                        "POST", "/api/v1/billing/care/episodes/" + UUID.randomUUID() + "/invoice"),
                    new MockHttpServletResponse(),
                    new Object()))
        .isInstanceOf(AccessDeniedException.class);
    verify(access).require(org, "cashreports", "READ");
    verify(access).require(org, "invoices", "CREATE");
  }

  @Test
  void cashAndRefundAreSeparatePermissions() {
    assertThat(HospitalOperationPermissions.Guard.resource("/api/v1/billing/care/invoices/x/cash"))
        .isEqualTo("payments");
    assertThat(
            HospitalOperationPermissions.Guard.resource("/api/v1/billing/care/invoices/x/refunds"))
        .isEqualTo("refunds");
  }

  @Test
  void medicalRecordIsNotMistakenForTheMeSelfPrefix() {
    var org = UUID.randomUUID();
    var users = mock(CurrentUserContext.class);
    var access = mock(OrganizationPermissionAccess.class);
    when(users.current())
        .thenReturn(new CurrentUserContext.UserContext(UUID.randomUUID(), org, Set.of("PATIENT")));
    new HospitalOperationPermissions.Guard(access, users)
        .preHandle(
            new MockHttpServletRequest(
                "GET", "/api/v1/patients/" + UUID.randomUUID() + "/medical-record"),
            new MockHttpServletResponse(),
            new Object());
    verify(access).require(org, "clinicalrecords", "READ");
  }

  @Test
  void lifecycleCommandsUseUpdateRatherThanCreate() {
    for (String path :
        java.util.List.of(
            "/api/v1/care/visits/x/start",
            "/api/v1/care/visits/x/report/finalize",
            "/api/v1/care/visits/x/report/corrections",
            "/api/v1/care/queue/presence",
            "/api/v1/care/queue/next"))
      assertThat(HospitalOperationPermissions.Guard.action("POST", path)).isEqualTo("UPDATE");
    assertThat(
            HospitalOperationPermissions.Guard.action(
                "POST", "/api/v1/billing/care/invoices/x/cash"))
        .isEqualTo("CREATE");
    assertThat(HospitalOperationPermissions.Guard.action("POST", "/api/v1/care/episodes"))
        .isEqualTo("CREATE");
  }

  @Test
  void clinicalHistoryUsesSeparateClinicalRecordPermission() {
    assertThat(HospitalOperationPermissions.Guard.resource("/api/v1/patients/x/care-history"))
        .isEqualTo("clinicalrecords");
  }
}
