package org.novasos.healthysv2;

import java.util.Map;

/** Explicit bridge from the deployed Keycloak realm to existing domain policies. */
final class KeycloakRoleMapping {
    private static final Map<String, String> ROLES = Map.ofEntries(
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
