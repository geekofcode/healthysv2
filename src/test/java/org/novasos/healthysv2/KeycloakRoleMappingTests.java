package org.novasos.healthysv2;

import static org.assertj.core.api.Assertions.assertThat;
import org.junit.jupiter.api.Test;
import java.util.List;
import java.util.Map;
import org.springframework.security.oauth2.jwt.Jwt;

class KeycloakRoleMappingTests {
    @Test
    void mapsAllDeployedRolesWithoutGivingViewerWritePrivileges() {
        var expected = Map.of("admin", "PLATFORM_ADMIN", "comptable", "ACCOUNTANT",
                "gestionnaire", "HOSPITAL_VIEWER", "hopital", "HOSPITAL_ADMIN",
                "laboratoire", "LAB_TECHNICIAN", "medecin", "DOCTOR",
                "nurse", "NURSE", "patient", "PATIENT");
        expected.forEach((role, authority) -> assertThat(KeycloakRoleMapping.canonicalRole(role)).isEqualTo(authority));
        assertThat(KeycloakRoleMapping.canonicalRole("offline_access")).isNull();
        assertThat(KeycloakRoleMapping.canonicalRole("default-roles-healthys")).isNull();
        assertThat(KeycloakRoleMapping.canonicalRole("Admin")).isNull();
    }

    @Test
    void converterReadsRealmAndOnlyConfiguredClientRoles() {
        var properties = new SecurityConfiguration.SecurityProperties("healthys-backend-apps", null);
        var converter = new SecurityConfiguration().jwtAuthenticationConverter(properties);
        var token = Jwt.withTokenValue("test").header("alg", "RS256").subject("user")
                .claim("realm_access", Map.of("roles", List.of("patient", "offline_access")))
                .claim("resource_access", Map.of(
                        "healthys-backend-apps", Map.of("roles", List.of("laboratoire")),
                        "unrelated-client", Map.of("roles", List.of("admin"))))
                .build();
        assertThat(converter.convert(token).getAuthorities()).extracting("authority")
                .contains("ROLE_PATIENT", "ROLE_LAB_TECHNICIAN")
                .doesNotContain("ROLE_PLATFORM_ADMIN", "ROLE_offline_access");
    }
}
