package org.novasos.healthysv2.professional;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.novasos.healthysv2.audit.AuditTrail;
import org.novasos.healthysv2.identity.api.IdentityProvisioningService;
import org.novasos.healthysv2.licensing.api.LicensingAccess;
import org.novasos.healthysv2.notification.api.ProfessionalRegistrationNotifications;
import org.novasos.healthysv2.organizationaccess.api.OrganizationPermissionAccess;
import org.novasos.healthysv2.patient.CurrentUserContext;
import org.novasos.healthysv2.professional.api.ProfessionalRoleProvisioning;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.access.AccessDeniedException;

class ProfessionalPendingApplicantTests {
  JdbcTemplate db = mock(JdbcTemplate.class);
  ProfessionalOnboardingService service =
      new ProfessionalOnboardingService(
          db,
          mock(IdentityProvisioningService.class),
          mock(ProfessionalRepository.class),
          mock(CurrentUserContext.class),
          mock(AuditTrail.class),
          mock(ProfessionalRoleProvisioning.class),
          mock(ProfessionalRegistrationNotifications.class),
          mock(LicensingAccess.class),
          mock(OrganizationPermissionAccess.class));

  @Test
  void boundUnexpiredInvitationQualifiesForLocalReview() {
    UUID org = UUID.randomUUID(), person = UUID.randomUUID();
    when(db.queryForObject(
            contains("status='INVITED' and expires_at>now()"),
            eq(Integer.class),
            eq(org),
            eq(person)))
        .thenReturn(1);
    assertThatCode(() -> service.requireLocalApplicant(org, person)).doesNotThrowAnyException();
    verify(db)
        .queryForObject(
            contains("where organization_id=? and person_id=?"),
            eq(Integer.class),
            eq(org),
            eq(person));
  }

  @Test
  void foreignOrExpiredInvitationCannotRevealApplicant() {
    UUID org = UUID.randomUUID(), person = UUID.randomUUID();
    when(db.queryForObject(anyString(), eq(Integer.class), eq(org), eq(person))).thenReturn(0);
    assertThatThrownBy(() -> service.requireLocalApplicant(org, person))
        .isInstanceOf(AccessDeniedException.class)
        .hasMessage("LOCAL_APPLICANT_REQUIRED");
  }
}
