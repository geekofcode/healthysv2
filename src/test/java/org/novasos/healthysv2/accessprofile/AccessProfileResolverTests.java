package org.novasos.healthysv2.accessprofile;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

import java.util.List;
import java.util.Set;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.novasos.healthysv2.accessprofile.api.AccessProfileResolver;
import org.novasos.healthysv2.accessprofile.api.AccessProfileResolver.AccessProfile;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.oauth2.jwt.Jwt;

class AccessProfileResolverTests {
  private final UUID person = UUID.randomUUID(),
      subject = UUID.randomUUID(),
      org = UUID.randomUUID();
  private final OwnedAccessProfileService service =
      spy(new OwnedAccessProfileService(mock(JdbcTemplate.class)));

  private AccessProfile member(String role) {
    return new AccessProfile(
        UUID.randomUUID(),
        person,
        role,
        "ORGANIZATION_MEMBERSHIP",
        UUID.randomUUID(),
        org,
        "Hospital",
        "Hospital — " + role,
        true);
  }

  private void available(Set<String> roles, AccessProfile... profiles) {
    doReturn(List.of(profiles)).when(service).profiles(subject, roles);
  }

  @Test
  void selectedDoctorNeverInheritsAdministratorPersona() {
    AccessProfile doctor = member("DOCTOR"), admin = member("HOSPITAL_ADMIN");
    available(Set.of(), doctor, admin);
    assertEquals(doctor, service.resolve(subject, Set.of(), doctor.id(), org));
    assertEquals("DOCTOR", service.resolve(subject, Set.of(), doctor.id(), org).type());
  }

  @Test
  void multipleProfilesWithinSameHospitalRequireExplicitSelection() {
    available(Set.of(), member("DOCTOR"), member("HOSPITAL_ADMIN"));
    assertThrows(AccessDeniedException.class, () -> service.resolve(subject, Set.of(), null, org));
  }

  @Test
  void foreignOrMismatchedProfileCannotBeSelected() {
    AccessProfile doctor = member("DOCTOR");
    available(Set.of(), doctor);
    assertThrows(
        AccessDeniedException.class,
        () -> service.resolve(subject, Set.of(), UUID.randomUUID(), org));
    assertThrows(
        AccessDeniedException.class,
        () -> service.resolve(subject, Set.of(), doctor.id(), UUID.randomUUID()));
  }

  @Test
  void platformClaimsDoNotSelectAnyProfileWithoutExplicitHeader() {
    AccessProfile platform =
        new AccessProfile(
            AccessProfileResolver.platformId(person),
            person,
            "PLATFORM_ADMIN",
            "PLATFORM",
            person,
            null,
            null,
            "Platform",
            true);
    AccessProfile doctor = member("DOCTOR");
    available(Set.of("PLATFORM_ADMIN"), platform, doctor);
    assertThrows(
        AccessDeniedException.class,
        () -> service.resolve(subject, Set.of("PLATFORM_ADMIN"), null, org));
    assertEquals("ACCOUNT", service.resolve(subject, Set.of("PLATFORM_ADMIN"), null, null).type());
    assertEquals(doctor, service.resolve(subject, Set.of("PLATFORM_ADMIN"), doctor.id(), org));
  }

  @Test
  void inactiveClinicalProfileCannotBeSelected() {
    AccessProfile clinical =
        new AccessProfile(
            UUID.randomUUID(),
            person,
            "DOCTOR",
            "ORGANIZATION_MEMBERSHIP",
            UUID.randomUUID(),
            org,
            "Hospital",
            "Doctor",
            false);
    available(Set.of(), clinical);
    assertThrows(
        AccessDeniedException.class, () -> service.resolve(subject, Set.of(), clinical.id(), org));
  }

  @Test
  void noSelectionHasOnlyAnInternalAccountContext() {
    AccessProfile patient =
        new AccessProfile(
            UUID.randomUUID(),
            person,
            "PATIENT",
            "PATIENT",
            UUID.randomUUID(),
            null,
            null,
            "Patient",
            true);
    available(Set.of(), member("DOCTOR"), patient);
    assertEquals("ACCOUNT", service.resolve(subject, Set.of(), null, null).type());
    assertEquals(patient, service.resolve(subject, Set.of(), patient.id(), null));
  }

  @Test
  void administratorProfileIdsRemainStableAndDifferentFromPlatform() {
    assertEquals(
        AccessProfileResolver.administratorId(org, person),
        AccessProfileResolver.administratorId(org, person));
    assertNotEquals(
        AccessProfileResolver.administratorId(org, person),
        AccessProfileResolver.platformId(person));
    assertNotEquals(
        AccessProfileResolver.administratorId(org, person),
        AccessProfileResolver.administratorId(UUID.randomUUID(), person));
  }

  @Test
  void profileDiscoveryBootstrapsOnlyTheAuthenticatedIdentityBeforeListing() {
    var resolver = mock(AccessProfileResolver.class);
    var identities = mock(org.novasos.healthysv2.identity.api.IdentityProvisioningService.class);
    var controller = new AccessProfilesController(resolver, identities, "healthys-backend");
    controller.administrators(
        mock(org.novasos.healthysv2.accessprofile.api.PlatformAdministratorProvisioning.class));
    var jwt =
        Jwt.withTokenValue("token")
            .header("alg", "none")
            .subject(subject.toString())
            .claim("given_name", "Doctor")
            .claim("family_name", "One")
            .claim("email", "doctor@example.org")
            .claim("email_verified", true)
            .build();
    when(resolver.profiles(subject, Set.of())).thenReturn(List.of());
    assertTrue(controller.profiles(jwt).isEmpty());
    var ordered = inOrder(identities, resolver);
    ordered
        .verify(identities)
        .provisionIdentity(subject, "Doctor", "One", "doctor@example.org", true);
    ordered.verify(resolver).profiles(subject, Set.of());
    verifyNoMoreInteractions(identities);
  }
}
