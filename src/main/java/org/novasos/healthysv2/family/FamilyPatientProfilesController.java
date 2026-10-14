package org.novasos.healthysv2.family;

import jakarta.validation.Valid;
import java.util.UUID;
import org.novasos.healthysv2.accessprofile.api.AccessProfileResolver.AccessProfile;
import org.novasos.healthysv2.family.api.PatientProfileRequest;
import org.novasos.healthysv2.identity.api.IdentityProvisioningService;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/me/patient-profiles")
class FamilyPatientProfilesController {
  private final FamilyPatientProfileService service;
  private final IdentityProvisioningService identities;

  FamilyPatientProfilesController(
      FamilyPatientProfileService service, IdentityProvisioningService identities) {
    this.service = service;
    this.identities = identities;
  }

  @PostMapping
  AccessProfile create(
      @AuthenticationPrincipal Jwt jwt,
      @Valid @RequestBody PatientProfileRequest request,
      @RequestHeader(value = "Idempotency-Key", required = false) String key) {
    if (jwt == null) throw new AccessDeniedException("ACCOUNT_REQUIRED");
    UUID subject;
    try {
      subject = UUID.fromString(jwt.getSubject());
    } catch (IllegalArgumentException exception) {
      throw new AccessDeniedException("INVALID_ACCOUNT_SUBJECT");
    }
    identities.provisionIdentity(
        subject,
        jwt.getClaimAsString("given_name"),
        jwt.getClaimAsString("family_name"),
        jwt.getClaimAsString("email"),
        Boolean.TRUE.equals(jwt.getClaimAsBoolean("email_verified")));
    return service.create(subject, request, key);
  }
}
