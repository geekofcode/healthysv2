package org.novasos.healthysv2.patient;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.Mockito.*;

import java.util.*;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.novasos.healthysv2.accessprofile.api.AccessProfileResolver;
import org.novasos.healthysv2.accessprofile.api.AccessProfileResolver.AccessProfile;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationToken;
import org.springframework.web.context.request.RequestContextHolder;
import org.springframework.web.context.request.ServletRequestAttributes;

class CurrentOrganizationContextTests {
  final UUID subject = UUID.randomUUID(),
      person = UUID.randomUUID(),
      org = UUID.randomUUID(),
      profile = UUID.randomUUID();
  final AccessProfileResolver resolver = mock(AccessProfileResolver.class);
  final CurrentUserContext context = new CurrentUserContext(resolver, "healthys-backend");

  @AfterEach
  void clear() {
    SecurityContextHolder.clearContext();
    RequestContextHolder.resetRequestAttributes();
  }

  void authentication(String... selectedRoles) {
    Jwt jwt =
        Jwt.withTokenValue("test")
            .header("alg", "none")
            .subject(subject.toString())
            .claim("realm_access", Map.of("roles", List.of("admin", "medecin", "patient")))
            .claim("organization_id", UUID.randomUUID().toString())
            .build();
    SecurityContextHolder.getContext()
        .setAuthentication(
            new JwtAuthenticationToken(
                jwt,
                Arrays.stream(selectedRoles)
                    .map(r -> new SimpleGrantedAuthority("ROLE_" + r))
                    .toList()));
  }

  void headers(UUID id, UUID organization) {
    var request = new MockHttpServletRequest();
    request.addHeader("X-Profile-ID", id.toString());
    if (organization != null) request.addHeader("X-Organization-ID", organization.toString());
    RequestContextHolder.setRequestAttributes(new ServletRequestAttributes(request));
  }

  AccessProfile persona(String type, UUID organization) {
    return new AccessProfile(
        profile,
        person,
        type,
        "ORGANIZATION_MEMBERSHIP",
        profile,
        organization,
        "Hospital",
        "Profile",
        true);
  }

  @Test
  void selectedPersistedProfileDeterminesOrganizationAndOnlyItsRole() {
    authentication("DOCTOR");
    headers(profile, org);
    when(resolver.resolve(subject, Set.of(), profile, org)).thenReturn(persona("DOCTOR", org));
    var user = context.current();
    assertThat(user.personId()).isEqualTo(person);
    assertThat(user.organizationId()).isEqualTo(org);
    assertThat(user.profileId()).isEqualTo(profile);
    assertThat(user.roles()).containsExactly("DOCTOR");
  }

  @Test
  void explicitProfileIgnoresStaleJwtOrganizationWhenNoOrganizationHeaderProvided() {
    authentication("DOCTOR");
    headers(profile, null);
    when(resolver.resolve(subject, Set.of(), profile, null)).thenReturn(persona("DOCTOR", org));
    assertThat(context.current().organizationId()).isEqualTo(org);
  }

  @Test
  void revokedProfileIsRecheckedEvenWithPreviouslyAcceptedJwt() {
    authentication("DOCTOR");
    headers(profile, org);
    when(resolver.resolve(subject, Set.of(), profile, org))
        .thenThrow(new AccessDeniedException("PROFILE_INACTIVE"));
    assertThatThrownBy(context::current)
        .isInstanceOf(AccessDeniedException.class)
        .hasMessage("PROFILE_INACTIVE");
  }

  @Test
  void clinicalRoleRemovedByQualificationFilterIsNotRestoredByProfile() {
    authentication();
    headers(profile, org);
    when(resolver.resolve(subject, Set.of(), profile, org)).thenReturn(persona("DOCTOR", org));
    assertThat(context.current().roles()).isEmpty();
  }

  @Test
  void patientPersonaContainsNoOrganizationOrStaffPermissions() {
    authentication("PATIENT");
    headers(profile, null);
    when(resolver.resolve(subject, Set.of(), profile, null)).thenReturn(persona("PATIENT", null));
    assertThat(context.current().organizationId()).isNull();
    assertThat(context.current().roles()).containsExactly("PATIENT");
  }

  @Test
  void guardianProfileKeepsActorIdentityButTargetsOnlySelectedChild() {
    var child = UUID.randomUUID();
    authentication("PATIENT");
    headers(profile, null);
    when(resolver.resolve(subject, Set.of(), profile, null))
        .thenReturn(
            new AccessProfile(
                profile, person, "PATIENT", "FAMILY_PATIENT", child, null, null, "Child", true));
    var selected = context.current();
    assertThat(selected.personId()).isEqualTo(person);
    assertThat(selected.patientId()).isEqualTo(child);
    assertThat(selected.profileId()).isEqualTo(profile);
    assertThat(selected.isPatient(child)).isTrue();
    assertThat(selected.isPatient(UUID.randomUUID())).isFalse();
  }
}
