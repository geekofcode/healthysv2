package org.novasos.healthysv2;

import java.util.Collection;
import java.util.Set;
import java.util.UUID;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.stereotype.Component;

@Component
class KeycloakProfessionalAccess {
    private static final Set<String> CLINICAL = Set.of("ROLE_DOCTOR", "ROLE_NURSE", "ROLE_LAB_TECHNICIAN");
    private final JdbcTemplate jdbc;
    KeycloakProfessionalAccess(JdbcTemplate jdbc) { this.jdbc = jdbc; }
    Collection<GrantedAuthority> filter(String subject, Collection<GrantedAuthority> authorities) {
        if (authorities.stream().noneMatch(a -> CLINICAL.contains(a.getAuthority()))) return authorities;
        UUID id;
        try { id = UUID.fromString(subject); } catch (IllegalArgumentException exception) { return authorities; }
        var states = jdbc.query("""
                SELECT r.status, p.status AS professional_status, r.profession
                FROM professional.registration_request r
                LEFT JOIN professional.professional p ON p.id=r.professional_id
                WHERE r.keycloak_user_id=?
                """, (rs, row) -> new State(rs.getString("status"), rs.getString("professional_status"), rs.getString("profession")), id);
        if (states.isEmpty()) return authorities; // Existing manually administered accounts.
        State state = states.getFirst();
        return authorities.stream().filter(a -> !CLINICAL.contains(a.getAuthority()) ||
                ("APPROVED".equals(state.status()) && "ACTIVE".equals(state.professionalStatus()) &&
                a.getAuthority().equals("ROLE_" + KeycloakRoleMapping.canonicalRole(state.profession())))).toList();
    }
    record State(String status, String professionalStatus, String profession) {}
}
