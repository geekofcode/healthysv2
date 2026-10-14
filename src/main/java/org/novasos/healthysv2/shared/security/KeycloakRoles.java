package org.novasos.healthysv2.shared.security;

import java.util.*;
import org.springframework.security.oauth2.jwt.Jwt;

/**
 * Original account capabilities are used only to enumerate profile choices, never unioned into an
 * active profile.
 */
public final class KeycloakRoles {
  private static final Map<String, String> ALIASES =
      Map.ofEntries(
          Map.entry("admin", "PLATFORM_ADMIN"),
          Map.entry("comptable", "ACCOUNTANT"),
          Map.entry("gestionnaire", "HOSPITAL_VIEWER"),
          Map.entry("hopital", "HOSPITAL_ADMIN"),
          Map.entry("laboratoire", "LAB_TECHNICIAN"),
          Map.entry("medecin", "DOCTOR"),
          Map.entry("nurse", "NURSE"),
          Map.entry("patient", "PATIENT"),
          Map.entry("caissier", "CASHIER"),
          Map.entry("cashier", "CASHIER"),
          Map.entry("hospital_agent", "HOSPITAL_AGENT"));
  private static final Set<String> CANONICAL =
      Set.of(
          "PLATFORM_ADMIN",
          "ACCOUNTANT",
          "HOSPITAL_VIEWER",
          "HOSPITAL_ADMIN",
          "LAB_TECHNICIAN",
          "DOCTOR",
          "NURSE",
          "PATIENT",
          "CASHIER",
          "HOSPITAL_AGENT",
          "PHARMACIST");

  private KeycloakRoles() {}

  public static String canonicalRole(String role) {
    return ALIASES.getOrDefault(role, CANONICAL.contains(role) ? role : null);
  }

  public static Set<String> canonicalRoles(Jwt jwt, String apiClientId) {
    var result = new LinkedHashSet<String>();
    add(result, jwt.getClaimAsMap("realm_access"));
    Map<String, Object> clients = jwt.getClaimAsMap("resource_access");
    if (clients != null && clients.get(apiClientId) instanceof Map<?, ?> access)
      add(result, access);
    return Set.copyOf(result);
  }

  private static void add(Set<String> target, Map<?, ?> access) {
    if (access != null && access.get("roles") instanceof Collection<?> roles)
      for (Object value : roles)
        if (value instanceof String role) {
          String canonical = canonicalRole(role);
          if (canonical != null) target.add(canonical);
        }
  }
}
