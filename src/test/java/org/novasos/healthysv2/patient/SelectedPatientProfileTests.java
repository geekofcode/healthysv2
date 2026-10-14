package org.novasos.healthysv2.patient;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.Mockito.*;

import java.util.*;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.novasos.healthysv2.identity.api.PersonLookup;
import org.novasos.healthysv2.identity.api.PersonResponse;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationToken;

class SelectedPatientProfileTests {
  final UUID subject = UUID.randomUUID(),
      guardian = UUID.randomUUID(),
      child1 = UUID.randomUUID(),
      child2 = UUID.randomUUID();
  final PersonLookup identities = mock(PersonLookup.class);
  final CurrentUserContext users = mock(CurrentUserContext.class);
  final JdbcTemplate jdbc = mock(JdbcTemplate.class);
  final PatientSelfAccess selves = new PatientSelfAccess(identities, jdbc, users);

  @AfterEach
  void cleanup() {
    SecurityContextHolder.clearContext();
  }

  void identity() {
    var person = mock(PersonResponse.class);
    when(person.id()).thenReturn(guardian);
    when(identities.findMe(subject)).thenReturn(person);
    var jwt = Jwt.withTokenValue("test").header("alg", "none").subject(subject.toString()).build();
    SecurityContextHolder.getContext()
        .setAuthentication(
            new JwtAuthenticationToken(jwt, List.of(new SimpleGrantedAuthority("ROLE_PATIENT"))));
  }

  @Test
  void selectedChildNeverAuthorizesSiblingEvenForSameGuardian() {
    identity();
    when(users.current())
        .thenReturn(
            new CurrentUserContext.UserContext(
                guardian, null, Set.of("PATIENT"), UUID.randomUUID(), child1));
    assertThat(selves.resolve(subject)).isEqualTo(new PatientSelfAccess.Identity(guardian, child1));
    assertThatCode(() -> selves.requireOwn(child1)).doesNotThrowAnyException();
    assertThatThrownBy(() -> selves.requireOwn(child2)).isInstanceOf(AccessDeniedException.class);
    verifyNoInteractions(jdbc);
  }

  @Test
  void switchingChildChangesOnlySelectedTargetAndPreservesGuardianAuditActor() {
    identity();
    when(users.current())
        .thenReturn(
            new CurrentUserContext.UserContext(
                guardian, null, Set.of("PATIENT"), UUID.randomUUID(), child2));
    assertThat(selves.resolve(subject)).isEqualTo(new PatientSelfAccess.Identity(guardian, child2));
    assertThatThrownBy(() -> selves.requireOwn(child1)).isInstanceOf(AccessDeniedException.class);
  }

  @Test
  void doctorPersonaDoesNotInheritTheSameAccountsPatientSelfAccess() {
    identity();
    when(users.current())
        .thenReturn(
            new CurrentUserContext.UserContext(
                guardian, UUID.randomUUID(), Set.of("DOCTOR"), UUID.randomUUID(), null));
    assertThatThrownBy(() -> selves.resolve(subject))
        .isInstanceOf(AccessDeniedException.class)
        .hasMessage("SELECTED_PATIENT_PROFILE_REQUIRED");
    verifyNoInteractions(jdbc);
  }
}
