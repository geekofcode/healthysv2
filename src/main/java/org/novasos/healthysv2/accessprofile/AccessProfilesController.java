package org.novasos.healthysv2.accessprofile;

import java.util.List;
import java.util.UUID;
import org.novasos.healthysv2.accessprofile.api.AccessProfileResolver;
import org.novasos.healthysv2.accessprofile.api.AccessProfileResolver.AccessProfile;
import org.novasos.healthysv2.shared.security.KeycloakRoles;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/me/access-profiles")
class AccessProfilesController {
  private final AccessProfileResolver resolver;
  private final String apiClientId;
  private final org.novasos.healthysv2.identity.api.IdentityProvisioningService identities;

  AccessProfilesController(
      AccessProfileResolver resolver,
      org.novasos.healthysv2.identity.api.IdentityProvisioningService identities,
      @Value("${healthys.security.api-client-id:healthys-backend}") String apiClientId) {
    this.resolver = resolver;
    this.identities = identities;
    this.apiClientId = apiClientId;
  }

  @GetMapping
  List<AccessProfile> profiles(@AuthenticationPrincipal Jwt jwt) {
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
    return resolver.profiles(subject, KeycloakRoles.canonicalRoles(jwt, apiClientId));
  }
}
