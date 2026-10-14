package org.novasos.healthysv2;

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
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.context.request.RequestContextHolder;
import org.springframework.web.context.request.ServletRequestAttributes;

class OrganizationAuthoritiesTests {
  final UUID subject = UUID.randomUUID(),
      person = UUID.randomUUID(),
      org = UUID.randomUUID(),
      profile = UUID.randomUUID();
  final AccessProfileResolver resolver = mock(AccessProfileResolver.class);

  @AfterEach
  void clear() {
    RequestContextHolder.resetRequestAttributes();
  }

  Jwt token() {
    return Jwt.withTokenValue("test").header("alg", "none").subject(subject.toString()).build();
  }

  void headers(UUID id, UUID organization) {
    var request = new MockHttpServletRequest();
    if (id != null) request.addHeader("X-Profile-ID", id.toString());
    if (organization != null) request.addHeader("X-Organization-ID", organization.toString());
    RequestContextHolder.setRequestAttributes(new ServletRequestAttributes(request));
  }

  AccessProfile persona(UUID id, String type, UUID organization) {
    return new AccessProfile(
        id, person, type, "ORGANIZATION_MEMBERSHIP", id, organization, "Hospital", "Profile", true);
  }

  @Test
  void selectedDoctorProfileOfPlatformAccountDoesNotInheritPlatformOrOtherHospitalRoles() {
    headers(profile, org);
    when(resolver.resolve(
            subject, Set.of("PLATFORM_ADMIN", "DOCTOR", "CASHIER", "PATIENT"), profile, org))
        .thenReturn(persona(profile, "DOCTOR", org));
    var result =
        new OrganizationAuthorities(resolver)
            .augment(
                token(),
                List.of(
                    new SimpleGrantedAuthority("ROLE_PLATFORM_ADMIN"),
                    new SimpleGrantedAuthority("ROLE_DOCTOR"),
                    new SimpleGrantedAuthority("ROLE_CASHIER"),
                    new SimpleGrantedAuthority("ROLE_PATIENT"),
                    new SimpleGrantedAuthority("SCOPE_openid")));
    assertThat(result)
        .extracting("authority")
        .containsExactlyInAnyOrder("ROLE_DOCTOR", "SCOPE_openid");
  }

  @Test
  void platformProfileNeverInheritsDoctorAuthority() {
    UUID selectedPlatform = AccessProfileResolver.platformId(person);
    headers(selectedPlatform, null);
    when(resolver.resolve(subject, Set.of("PLATFORM_ADMIN", "DOCTOR"), selectedPlatform, null))
        .thenReturn(persona(AccessProfileResolver.platformId(person), "PLATFORM_ADMIN", null));
    var result =
        new OrganizationAuthorities(resolver)
            .augment(
                token(),
                List.of(
                    new SimpleGrantedAuthority("ROLE_PLATFORM_ADMIN"),
                    new SimpleGrantedAuthority("ROLE_DOCTOR")));
    assertThat(result).extracting("authority").containsExactly("ROLE_PLATFORM_ADMIN");
  }

  @Test
  void foreignProfileCannotBeSelectedByForgingHeader() {
    headers(profile, org);
    when(resolver.resolve(subject, Set.of("DOCTOR"), profile, org))
        .thenThrow(new AccessDeniedException("PROFILE_NOT_OWNED"));
    assertThatThrownBy(
            () ->
                new OrganizationAuthorities(resolver)
                    .augment(token(), List.of(new SimpleGrantedAuthority("ROLE_DOCTOR"))))
        .isInstanceOf(AccessDeniedException.class)
        .hasMessage("PROFILE_NOT_OWNED");
  }

  @Test
  void twoHospitalProfilesAreResolvedIndividuallyWithoutUnion() {
    var org2 = UUID.randomUUID();
    var profile2 = UUID.randomUUID();
    when(resolver.resolve(subject, Set.of("DOCTOR", "CASHIER"), profile, org))
        .thenReturn(persona(profile, "DOCTOR", org));
    when(resolver.resolve(subject, Set.of("DOCTOR", "CASHIER"), profile2, org2))
        .thenReturn(persona(profile2, "CASHIER", org2));
    var original =
        List.<org.springframework.security.core.GrantedAuthority>of(
            new SimpleGrantedAuthority("ROLE_DOCTOR"), new SimpleGrantedAuthority("ROLE_CASHIER"));
    headers(profile, org);
    assertThat(new OrganizationAuthorities(resolver).augment(token(), original))
        .extracting("authority")
        .containsExactly("ROLE_DOCTOR");
    headers(profile2, org2);
    assertThat(new OrganizationAuthorities(resolver).augment(token(), original))
        .extracting("authority")
        .containsExactly("ROLE_CASHIER");
  }

  @Test
  void invalidHeaderNeverFallsBackToGlobalRoles() {
    var request = new MockHttpServletRequest();
    request.addHeader("X-Profile-ID", "forged");
    RequestContextHolder.setRequestAttributes(new ServletRequestAttributes(request));
    assertThatThrownBy(
            () ->
                new OrganizationAuthorities(resolver)
                    .augment(token(), List.of(new SimpleGrantedAuthority("ROLE_PLATFORM_ADMIN"))))
        .isInstanceOf(AccessDeniedException.class)
        .hasMessage("INVALID_PROFILE_CONTEXT");
    verifyNoInteractions(resolver);
  }

  @Test
  void firstOwnIdentityRequestCanBootstrapWithoutAnyAccountRole() {
    var request = new MockHttpServletRequest("GET", "/api/v1/persons/me");
    RequestContextHolder.setRequestAttributes(new ServletRequestAttributes(request));
    when(resolver.resolve(subject, Set.of("PLATFORM_ADMIN"), null, null))
        .thenThrow(new AccessDeniedException("ACTIVE_ACCOUNT_REQUIRED"));
    assertThat(
            new OrganizationAuthorities(resolver)
                .augment(
                    token(),
                    List.of(
                        new SimpleGrantedAuthority("ROLE_PLATFORM_ADMIN"),
                        new SimpleGrantedAuthority("SCOPE_openid"))))
        .extracting("authority")
        .containsExactly("SCOPE_openid");
  }

  @Test
  void missingIdentityNeverBootstrapsAnOrganizationOperation() {
    var request = new MockHttpServletRequest("POST", "/api/v1/organizations");
    RequestContextHolder.setRequestAttributes(new ServletRequestAttributes(request));
    when(resolver.resolve(subject, Set.of("PLATFORM_ADMIN"), null, null))
        .thenThrow(new AccessDeniedException("ACTIVE_ACCOUNT_REQUIRED"));
    assertThatThrownBy(
            () ->
                new OrganizationAuthorities(resolver)
                    .augment(token(), List.of(new SimpleGrantedAuthority("ROLE_PLATFORM_ADMIN"))))
        .isInstanceOf(AccessDeniedException.class)
        .hasMessage("ACTIVE_ACCOUNT_REQUIRED");
  }

  @Test
  void explicitProfileCannotUseIdentityBootstrapException() {
    var request = new MockHttpServletRequest("GET", "/api/v1/persons/me");
    request.addHeader("X-Profile-ID", profile.toString());
    RequestContextHolder.setRequestAttributes(new ServletRequestAttributes(request));
    when(resolver.resolve(subject, Set.of("PLATFORM_ADMIN"), profile, null))
        .thenThrow(new AccessDeniedException("ACTIVE_ACCOUNT_REQUIRED"));
    assertThatThrownBy(
            () ->
                new OrganizationAuthorities(resolver)
                    .augment(token(), List.of(new SimpleGrantedAuthority("ROLE_PLATFORM_ADMIN"))))
        .isInstanceOf(AccessDeniedException.class)
        .hasMessage("ACTIVE_ACCOUNT_REQUIRED");
  }

  @Test
  void anonymizedAccountCanReadItsDeletionStatusWithoutObtainingAnyRole() {
    var request =
        new MockHttpServletRequest("GET", "/api/v1/me/data-deletion/" + UUID.randomUUID());
    RequestContextHolder.setRequestAttributes(new ServletRequestAttributes(request));
    when(resolver.resolve(subject, Set.of("PATIENT"), null, null))
        .thenThrow(new AccessDeniedException("ACTIVE_ACCOUNT_REQUIRED"));
    assertThat(
            new OrganizationAuthorities(resolver)
                .augment(token(), List.of(new SimpleGrantedAuthority("ROLE_PATIENT"))))
        .isEmpty();
  }

  @Test
  void inactiveAccountCannotCreateAnotherDeletionRequestThroughReadOnlyException() {
    var request = new MockHttpServletRequest("POST", "/api/v1/me/data-deletion");
    RequestContextHolder.setRequestAttributes(new ServletRequestAttributes(request));
    when(resolver.resolve(subject, Set.of("PATIENT"), null, null))
        .thenThrow(new AccessDeniedException("ACTIVE_ACCOUNT_REQUIRED"));
    assertThatThrownBy(
            () ->
                new OrganizationAuthorities(resolver)
                    .augment(token(), List.of(new SimpleGrantedAuthority("ROLE_PATIENT"))))
        .isInstanceOf(AccessDeniedException.class);
  }

  @Test
  void noProfileSelectionNeverGrantsPlatformOrPatientAuthorities() {
    when(resolver.resolve(subject, Set.of("PLATFORM_ADMIN", "PATIENT"), null, null))
        .thenReturn(
            new AccessProfile(
                person, person, "ACCOUNT", "ACCOUNT", person, null, null, "Account", true));
    assertThat(
            new OrganizationAuthorities(resolver)
                .augment(
                    token(),
                    List.of(
                        new SimpleGrantedAuthority("ROLE_PLATFORM_ADMIN"),
                        new SimpleGrantedAuthority("ROLE_PATIENT"))))
        .isEmpty();
  }
}
