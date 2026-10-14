package org.novasos.healthysv2;

import java.util.*;
import org.novasos.healthysv2.accessprofile.api.AccessProfileResolver;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.stereotype.Component;
import org.springframework.web.context.request.RequestContextHolder;
import org.springframework.web.context.request.ServletRequestAttributes;

/** Exactly one persisted source profile contributes roles to a request. */
@Component
class OrganizationAuthorities {
  private final AccessProfileResolver profiles;

  OrganizationAuthorities(AccessProfileResolver profiles) {
    this.profiles = profiles;
  }

  Collection<GrantedAuthority> augment(Jwt jwt, Collection<GrantedAuthority> original) {
    UUID subject = uuid(jwt.getSubject(), "INVALID_IDENTITY_SUBJECT");
    if (subject == null) throw new AccessDeniedException("INVALID_IDENTITY_SUBJECT");
    UUID profileId = null;
    UUID org = null;
    if (RequestContextHolder.getRequestAttributes()
        instanceof ServletRequestAttributes attributes) {
      var request = attributes.getRequest();
      profileId = uuid(request.getHeader("X-Profile-ID"), "INVALID_PROFILE_CONTEXT");
      if (profileId != null) org = null;
      String selected = request.getHeader("X-Organization-ID");
      if (selected != null && !selected.isBlank())
        org = uuid(selected, "INVALID_ORGANIZATION_CONTEXT");
    }
    AccessProfileResolver.AccessProfile profile;
    try {
      profile = profiles.resolve(subject, Set.of(), profileId, org);
    } catch (AccessDeniedException denied) {
      if (!"ACTIVE_ACCOUNT_REQUIRED".equals(denied.getMessage()) || !selfBootstrap()) throw denied;
      return original.stream().filter(a -> !a.getAuthority().startsWith("ROLE_")).toList();
    }
    var result = new LinkedHashSet<GrantedAuthority>();
    original.stream().filter(a -> !a.getAuthority().startsWith("ROLE_")).forEach(result::add);
    if (profile != null
        && Set.of(
                "PLATFORM_ADMIN",
                "PATIENT",
                "HOSPITAL_ADMIN",
                "HOSPITAL_AGENT",
                "HOSPITAL_VIEWER",
                "CASHIER",
                "ACCOUNTANT",
                "DOCTOR",
                "NURSE")
            .contains(profile.type()))
      result.add(new SimpleGrantedAuthority("ROLE_" + profile.type()));
    return result;
  }

  private boolean selfBootstrap() {
    if (!(RequestContextHolder.getRequestAttributes()
        instanceof ServletRequestAttributes attributes)) return false;
    var request = attributes.getRequest();
    if (request.getHeader("X-Profile-ID") != null || request.getHeader("X-Organization-ID") != null)
      return false;
    String path = request.getRequestURI().substring(request.getContextPath().length());
    return ((request.getMethod().equals("GET") || request.getMethod().equals("HEAD"))
            && path.matches("/api/v1/me/data-deletion(/[0-9a-fA-F-]{36})?"))
        || path.equals("/api/v1/persons/me")
        || path.equals("/api/v1/persons/me/profile")
        || path.equals("/api/v1/patients/me/provision")
        || path.equals("/api/v1/me/access-profiles");
  }

  private UUID legacyOrganization(Jwt jwt) {
    String value = jwt.getClaimAsString("healthys_organization_id");
    if (value == null) value = jwt.getClaimAsString("organization_id");
    return uuid(value, "INVALID_ORGANIZATION_CONTEXT");
  }

  private UUID uuid(String value, String code) {
    if (value == null || value.isBlank()) return null;
    try {
      return UUID.fromString(value);
    } catch (IllegalArgumentException e) {
      throw new AccessDeniedException(code);
    }
  }
}
