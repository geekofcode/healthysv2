package org.novasos.healthysv2;

import java.util.Map;

/** Explicit bridge from the deployed Keycloak realm to existing domain policies. */
final class KeycloakRoleMapping {
    private static final Map<String, String> ROLES = Map.of(
            "admin", "PLATFORM_ADMIN",
            "comptable", "ACCOUNTANT",
            "gestionnaire", "HOSPITAL_VIEWER",
            "hopital", "HOSPITAL_ADMIN",
            "laboratoire", "LAB_TECHNICIAN",
            "medecin", "DOCTOR",
            "nurse", "NURSE",
            "patient", "PATIENT");

    private KeycloakRoleMapping() {}

    static String canonicalRole(String role) {
        String mapped = ROLES.get(role);
        if (mapped != null) return mapped;
        // Preserve existing deployments with explicitly assigned domain roles.
        try {
            return HealthysRole.valueOf(role).name();
        } catch (IllegalArgumentException exception) {
            return null;
        }
    }
}
