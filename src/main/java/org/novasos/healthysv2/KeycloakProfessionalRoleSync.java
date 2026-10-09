package org.novasos.healthysv2;

import java.util.Set;
import java.util.UUID;
import org.novasos.healthysv2.professional.api.ProfessionalRoleProvisioning;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

@Service
class KeycloakProfessionalRoleSync implements ProfessionalRoleProvisioning {
    private static final Set<String> ROLES = Set.of("medecin", "nurse", "laboratoire");
    private final JdbcTemplate jdbc;
    KeycloakProfessionalRoleSync(JdbcTemplate jdbc) { this.jdbc = jdbc; }
    @Override
    @Transactional(propagation = Propagation.MANDATORY)
    public void activate(UUID subject, String role) { enqueue(subject, role, true); }
    @Override
    @Transactional(propagation = Propagation.MANDATORY)
    public void deactivate(UUID subject, String role) { enqueue(subject, role, false); }
    static void validateRole(String role) {
        if (!ROLES.contains(role)) throw new IllegalArgumentException("Unsupported professional realm role");
    }
    private void enqueue(UUID subject, String role, boolean enabled) {
        validateRole(role);
        jdbc.update("""
                INSERT INTO professional.role_sync_outbox(subject_id, realm_role, enabled, status, attempts)
                VALUES (?, ?, ?, 'PENDING', 0)
                ON CONFLICT (subject_id) DO UPDATE SET realm_role=EXCLUDED.realm_role,
                enabled=EXCLUDED.enabled, status='PENDING', attempts=0, last_error=NULL, updated_at=now()
                """, subject, role, enabled);
    }
}
