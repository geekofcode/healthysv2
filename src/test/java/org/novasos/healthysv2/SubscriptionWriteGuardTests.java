package org.novasos.healthysv2;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.Mockito.*;
import java.util.*;
import org.junit.jupiter.api.Test;
import org.novasos.healthysv2.licensing.api.LicensingAccess;
import org.novasos.healthysv2.patient.CurrentUserContext;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;
import org.springframework.security.access.AccessDeniedException;

class SubscriptionWriteGuardTests {
    final LicensingAccess licensing=mock(LicensingAccess.class);
    final CurrentUserContext users=mock(CurrentUserContext.class);
    final SubscriptionWebConfiguration.WriteGuard guard=new SubscriptionWebConfiguration.WriteGuard(licensing,users);
    @Test void readsRemainAvailableForExpiredSubscriptions() {
        assertThat(guard.preHandle(new MockHttpServletRequest("GET","/api/v1/consultations"),new MockHttpServletResponse(),new Object())).isTrue();
        verifyNoInteractions(users,licensing);
    }
    @Test void expiredSubscriptionBlocksWrites() {
        UUID org=UUID.randomUUID();
        when(users.current()).thenReturn(new CurrentUserContext.UserContext(UUID.randomUUID(),org,Set.of("DOCTOR")));
        doThrow(new AccessDeniedException("ACTIVE_SUBSCRIPTION_REQUIRED")).when(licensing).requireActive(org);
        assertThatThrownBy(()->guard.preHandle(new MockHttpServletRequest("POST","/api/v1/consultations"),new MockHttpServletResponse(),new Object())).isInstanceOf(AccessDeniedException.class);
        verify(licensing).requireMember(org);
    }
    @Test void aiCommandsRequireIncludedFeature() {
        UUID org=UUID.randomUUID();
        when(users.current()).thenReturn(new CurrentUserContext.UserContext(UUID.randomUUID(),org,Set.of("DOCTOR")));
        doThrow(new AccessDeniedException("AI_NOT_INCLUDED")).when(licensing).requireAi(org);
        assertThatThrownBy(()->guard.preHandle(new MockHttpServletRequest("POST","/api/v1/visits/visit/ai-jobs"),new MockHttpServletResponse(),new Object())).isInstanceOf(AccessDeniedException.class);
        verify(licensing).requireActive(org);
    }
    @Test void patientCanRevokeOwnAccessWithoutAnOrganizationSubscription() {
        UUID patient=UUID.randomUUID();
        when(users.current()).thenReturn(new CurrentUserContext.UserContext(UUID.randomUUID(),null,Set.of("PATIENT")));
        assertThat(guard.preHandle(new MockHttpServletRequest("POST","/api/v1/patients/"+patient+"/access-revocations"),new MockHttpServletResponse(),new Object())).isTrue();
        verifyNoInteractions(licensing);
    }
    @Test void patientCannotUseAccessDecisionExemptionToRequestStaffAccess() {
        UUID patient=UUID.randomUUID();
        when(users.current()).thenReturn(new CurrentUserContext.UserContext(UUID.randomUUID(),null,Set.of("PATIENT")));
        assertThatThrownBy(()->guard.preHandle(new MockHttpServletRequest("POST","/api/v1/patients/"+patient+"/access-requests"),new MockHttpServletResponse(),new Object())).isInstanceOf(AccessDeniedException.class);
    }
}
