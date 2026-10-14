package org.novasos.healthysv2.patient;

import java.util.*;
import org.novasos.healthysv2.accessprofile.api.AccessProfileResolver;
import org.novasos.healthysv2.shared.security.KeycloakRoles;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationToken;
import org.springframework.stereotype.Component;
import org.springframework.web.context.request.RequestContextHolder;
import org.springframework.web.context.request.ServletRequestAttributes;

@Component
public class CurrentUserContext {
  private final AccessProfileResolver profiles;
  private final String apiClientId;

  public CurrentUserContext(
      AccessProfileResolver profiles,
      @Value("${healthys.security.api-client-id:healthys-backend}") String apiClientId) {
    this.profiles = profiles;
    this.apiClientId = apiClientId;
  }

  public UserContext current() {
    var authentication = SecurityContextHolder.getContext().getAuthentication();
    if (!(authentication instanceof JwtAuthenticationToken jwt))
      return new UserContext(null, null, Set.of(), null);
    UUID subject = uuid(jwt.getToken().getSubject(), "INVALID_IDENTITY_SUBJECT");
    UUID org = null, profileId = null;
    if (RequestContextHolder.getRequestAttributes()
        instanceof ServletRequestAttributes attributes) {
      var request = attributes.getRequest();
      profileId = uuid(request.getHeader("X-Profile-ID"), "INVALID_PROFILE_CONTEXT");
      if (profileId != null) org = null;
      String header = request.getHeader("X-Organization-ID");
      if (header != null && !header.isBlank()) org = uuid(header, "INVALID_ORGANIZATION_CONTEXT");
    }
    var profile =
        profiles.resolve(
            subject, KeycloakRoles.canonicalRoles(jwt.getToken(), apiClientId), profileId, org);
    if (profile == null) return new UserContext(null, null, Set.of(), null);
    Set<String> activeRoles = new LinkedHashSet<>();
    jwt.getAuthorities().stream()
        .map(a -> a.getAuthority())
        .filter(a -> a.equals("ROLE_" + profile.type()))
        .forEach(a -> activeRoles.add(profile.type()));
    return new UserContext(
        profile.personId(),
        profile.organizationId(),
        Set.copyOf(activeRoles),
        profile.id(),
        "PATIENT".equals(profile.type())
                && Set.of("PATIENT", "FAMILY_PATIENT").contains(profile.sourceType())
            ? profile.sourceId()
            : null);
  }

  private UUID uuid(String value, String code) {
    if (value == null || value.isBlank()) return null;
    try {
      return UUID.fromString(value);
    } catch (IllegalArgumentException e) {
      throw new AccessDeniedException(code);
    }
  }

  public record UserContext(
      UUID personId, UUID organizationId, Set<String> roles, UUID profileId, UUID patientId) {
    public UserContext(UUID personId, UUID organizationId, Set<String> roles) {
      this(personId, organizationId, roles, null, null);
    }

    public UserContext(UUID personId, UUID organizationId, Set<String> roles, UUID profileId) {
      this(personId, organizationId, roles, profileId, null);
    }

    public boolean isPatient(UUID requested) {
      return has("PATIENT")
          && personId != null
          && profileId != null
          && patientId != null
          && patientId.equals(requested);
    }

    public boolean has(String role) {
      return roles.contains(role);
    }

    public boolean hasAny(String... values) {
      return Arrays.stream(values).anyMatch(roles::contains);
    }
  }
}
