package org.novasos.healthysv2;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

import java.util.Set;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.novasos.healthysv2.patient.CurrentUserContext;
import org.novasos.healthysv2.profilecontract.api.ContractAccess;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;
import org.springframework.security.access.AccessDeniedException;

class ProfileContractGuardTests {
  private final CurrentUserContext users = mock(CurrentUserContext.class);
  private final ContractAccess contracts = mock(ContractAccess.class);
  private final ProfileContractGuard.Guard guard = new ProfileContractGuard.Guard(users, contracts);

  private boolean request(String path) {
    return guard.preHandle(
        new MockHttpServletRequest("GET", path), new MockHttpServletResponse(), new Object());
  }

  @Test
  void unsignedProfileCannotReadOperations() {
    UUID person = UUID.randomUUID(), profile = UUID.randomUUID();
    when(users.current())
        .thenReturn(
            new CurrentUserContext.UserContext(
                person, UUID.randomUUID(), Set.of("DOCTOR"), profile));
    doThrow(new AccessDeniedException("PROFILE_CONTRACT_REQUIRED"))
        .when(contracts)
        .requireSigned(person, profile);
    assertThrows(AccessDeniedException.class, () -> request("/api/v1/care/episodes"));
  }

  @Test
  void profileDiscoveryAndSigningRemainReachable() {
    assertTrue(request("/api/v1/me/access-profiles"));
    assertTrue(request("/api/v1/me/profile-contracts/" + UUID.randomUUID() + "/template"));
    verifyNoInteractions(users, contracts);
  }

  @Test
  void onlySelectedPlatformAdministratorCanBootstrapTemplates() {
    when(users.current())
        .thenReturn(
            new CurrentUserContext.UserContext(
                UUID.randomUUID(), null, Set.of("PLATFORM_ADMIN"), UUID.randomUUID()));
    assertTrue(request("/api/v1/admin/contract-templates"));
    verifyNoInteractions(contracts);
    when(users.current())
        .thenReturn(
            new CurrentUserContext.UserContext(
                UUID.randomUUID(), UUID.randomUUID(), Set.of("HOSPITAL_ADMIN"), UUID.randomUUID()));
    assertThrows(AccessDeniedException.class, () -> request("/api/v1/admin/contract-templates"));
  }

  @Test
  void accountWithoutSelectedProfileCannotReadBusinessData() {
    when(users.current())
        .thenReturn(new CurrentUserContext.UserContext(UUID.randomUUID(), null, Set.of(), null));
    assertThrows(AccessDeniedException.class, () -> request("/api/v1/invoices"));
    verifyNoInteractions(contracts);
  }
}
