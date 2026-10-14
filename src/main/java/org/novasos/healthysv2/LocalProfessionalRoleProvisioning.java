package org.novasos.healthysv2;

import java.util.Objects;
import java.util.UUID;
import org.novasos.healthysv2.professional.api.ProfessionalRoleProvisioning;
import org.springframework.stereotype.Service;

/**
 * Compatibility hook: persisted professional approval and selected membership are authoritative.
 */
@Service
class LocalProfessionalRoleProvisioning implements ProfessionalRoleProvisioning {
  @Override
  public void activate(UUID subject, String role) {
    validate(subject, role);
  }

  @Override
  public void deactivate(UUID subject, String role) {
    validate(subject, role);
  }

  private void validate(UUID subject, String role) {
    Objects.requireNonNull(subject, "Account subject is required");
    Objects.requireNonNull(role, "Professional type is required");
    // The caller already persists approval/suspension in its transaction. No realm mapping or
    // delivery queue can confer local permissions, and no remote role update is attempted.
  }
}
