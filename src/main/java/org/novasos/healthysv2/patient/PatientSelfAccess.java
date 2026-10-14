package org.novasos.healthysv2.patient;

import java.util.UUID;
import org.novasos.healthysv2.identity.api.PersonLookup;
import org.novasos.healthysv2.shared.api.error.ResourceNotFoundException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationToken;
import org.springframework.stereotype.Component;

/** Strict Keycloak linkage for patient-facing reads, including legacy endpoints. */
@Component
public class PatientSelfAccess {
  private final PersonLookup identities;
  private final JdbcTemplate jdbc;
  private final CurrentUserContext users;

  public PatientSelfAccess(PersonLookup identities, JdbcTemplate jdbc, CurrentUserContext users) {
    this.identities = identities;
    this.jdbc = jdbc;
    this.users = users;
  }

  public Identity resolve(UUID subject) {
    var person = identities.findMe(subject);
    var user = users.current();
    if (!user.has("PATIENT") || user.patientId() == null || !person.id().equals(user.personId()))
      throw new AccessDeniedException("SELECTED_PATIENT_PROFILE_REQUIRED");
    return new Identity(person.id(), user.patientId());
  }

  public void requireOwn(UUID patient) {
    var authentication = SecurityContextHolder.getContext().getAuthentication();
    if (!(authentication instanceof JwtAuthenticationToken jwt))
      throw new AccessDeniedException("Patient authentication required");
    UUID subject;
    try {
      subject = UUID.fromString(jwt.getToken().getSubject());
    } catch (IllegalArgumentException exception) {
      throw new AccessDeniedException("Invalid patient identity");
    }
    Identity self;
    try {
      self = resolve(subject);
    } catch (ResourceNotFoundException exception) {
      throw new AccessDeniedException("Linked patient identity required");
    }
    if (!self.patient().equals(patient))
      throw new AccessDeniedException("Resource belongs to another patient");
  }

  public record Identity(UUID person, UUID patient) {}
}
