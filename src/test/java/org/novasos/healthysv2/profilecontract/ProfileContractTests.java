package org.novasos.healthysv2.profilecontract;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

import java.util.*;
import org.junit.jupiter.api.Test;
import org.novasos.healthysv2.accessprofile.api.AccessProfileResolver;
import org.novasos.healthysv2.profilecontract.api.ContractDtos.*;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.access.AccessDeniedException;

class ProfileContractTests {
  JdbcTemplate db = mock(JdbcTemplate.class);
  AccessProfileResolver profiles = mock(AccessProfileResolver.class);
  ProfileContractService service =
      new ProfileContractService(
          db, profiles, mock(ContractObjectStorage.class), "healthys-backend");

  @Test
  void unsignedProfileCannotAccessOperations() {
    UUID person = UUID.randomUUID(), profile = UUID.randomUUID();
    when(db.queryForObject(anyString(), eq(Integer.class), eq(person), eq(profile))).thenReturn(0);
    assertThatThrownBy(() -> service.requireSigned(person, profile))
        .isInstanceOf(AccessDeniedException.class)
        .hasMessage("PROFILE_CONTRACT_REQUIRED");
  }

  @Test
  void oneSignedProfileDoesNotAuthorizeAnotherSource() {
    UUID person = UUID.randomUUID(), signed = UUID.randomUUID(), other = UUID.randomUUID();
    var signedProfile =
        new AccessProfileResolver.AccessProfile(
            signed,
            person,
            "CASHIER",
            "ORGANIZATION_MEMBERSHIP",
            signed,
            null,
            null,
            "Caisse",
            true);
    var otherProfile =
        new AccessProfileResolver.AccessProfile(
            other, person, "DOCTOR", "ORGANIZATION_MEMBERSHIP", other, null, null, "Médecin", true);
    when(profiles.forPerson(person, signed)).thenReturn(signedProfile);
    when(profiles.forPerson(person, other)).thenReturn(otherProfile);
    when(db.queryForObject(
            anyString(),
            eq(Integer.class),
            eq(person),
            eq(signed),
            eq("CASHIER"),
            eq("ORGANIZATION_MEMBERSHIP"),
            eq(signed),
            isNull(),
            eq("STAFF")))
        .thenReturn(1);
    assertThatCode(() -> service.requireSigned(person, signed)).doesNotThrowAnyException();
    assertThatThrownBy(() -> service.requireSigned(person, other))
        .isInstanceOf(AccessDeniedException.class);
  }

  @org.junit.jupiter.api.AfterEach
  void clearAuthentication() {
    org.springframework.security.core.context.SecurityContextHolder.clearContext();
  }

  @Test
  void guardianMustExplicitlyAcknowledgeSigningForOwnedChild() {
    UUID parent = UUID.randomUUID(), child = UUID.randomUUID(), profileId = UUID.randomUUID();
    var resolver = mock(AccessProfileResolver.class);
    var family =
        new AccessProfileResolver.AccessProfile(
            profileId, parent, "PATIENT", "FAMILY_PATIENT", child, null, null, "Enfant", true);
    when(resolver.resolve(any(UUID.class), anySet(), eq(profileId), isNull())).thenReturn(family);
    var jwt =
        org.springframework.security.oauth2.jwt.Jwt.withTokenValue("test")
            .header("alg", "none")
            .subject(UUID.randomUUID().toString())
            .build();
    org.springframework.security.core.context.SecurityContextHolder.getContext()
        .setAuthentication(
            new org.springframework.security.oauth2.server.resource.authentication
                .JwtAuthenticationToken(jwt));
    var guardianService =
        new ProfileContractService(
            db, resolver, mock(ContractObjectStorage.class), "healthys-backend");
    assertThatThrownBy(
            () ->
                guardianService.sign(
                    profileId,
                    new SignRequest(
                        UUID.randomUUID(), true, "data:image/png;base64,invalid", false)))
        .isInstanceOf(IllegalArgumentException.class)
        .hasMessage("GUARDIAN_ACKNOWLEDGEMENT_REQUIRED");
    verifyNoInteractions(db);
  }

  @Test
  void termsRequireActualHttpsUrl() {
    assertThatThrownBy(
            () ->
                ProfileContractService.validateTemplate(
                    new TemplateRequest("PATIENT", "javascript:alert(1)", "1", "Modèle fourni")))
        .isInstanceOf(IllegalArgumentException.class);
    assertThatCode(
            () ->
                ProfileContractService.validateTemplate(
                    new TemplateRequest(
                        "PATIENT", "https://example.test/terms", "1", "Modèle fourni")))
        .doesNotThrowAnyException();
  }

  @Test
  void recipientCannotContainSmtpHeaderInjection() {
    assertThat(ProfileContractService.emailAddress("x@example.test\r\nBcc: other@example.test"))
        .isFalse();
    assertThat(ProfileContractService.emailAddress("verified@example.test")).isTrue();
  }

  @Test
  void unchangedMembershipIdWithChangedRoleRequiresNewContract() {
    UUID person = UUID.randomUUID(), profile = UUID.randomUUID();
    when(profiles.forPerson(person, profile))
        .thenReturn(
            new AccessProfileResolver.AccessProfile(
                profile,
                person,
                "DOCTOR",
                "ORGANIZATION_MEMBERSHIP",
                profile,
                null,
                null,
                "Médecin",
                true));
    when(db.queryForObject(
            anyString(),
            eq(Integer.class),
            eq(person),
            eq(profile),
            eq("CASHIER"),
            eq("ORGANIZATION_MEMBERSHIP"),
            eq(profile),
            isNull(),
            eq("STAFF")))
        .thenReturn(1);
    assertThatThrownBy(() -> service.requireSigned(person, profile))
        .isInstanceOf(AccessDeniedException.class)
        .hasMessage("PROFILE_CONTRACT_REQUIRED");
    verify(db)
        .queryForObject(
            contains("a.profile_type=?"),
            eq(Integer.class),
            eq(person),
            eq(profile),
            eq("DOCTOR"),
            eq("ORGANIZATION_MEMBERSHIP"),
            eq(profile),
            isNull(),
            eq("STAFF"));
  }

  @Test
  void activePersistedPlatformAdministratorCanUseOwnSignedContract() {
    UUID person = UUID.randomUUID(), profile = AccessProfileResolver.platformId(person);
    when(profiles.forPerson(person, profile))
        .thenReturn(
            new AccessProfileResolver.AccessProfile(
                profile,
                person,
                "PLATFORM_ADMIN",
                "PLATFORM",
                person,
                null,
                null,
                "Administration",
                true));
    when(db.queryForObject(
            anyString(),
            eq(Integer.class),
            eq(person),
            eq(profile),
            eq("PLATFORM_ADMIN"),
            eq("PLATFORM"),
            eq(person),
            isNull(),
            eq("ADMIN")))
        .thenReturn(1);
    assertThatCode(() -> service.requireSigned(person, profile)).doesNotThrowAnyException();
    verify(profiles).forPerson(person, profile);
  }

  @Test
  void revokedPlatformAdministratorCannotReuseHistoricalSignedAgreement() {
    UUID person = UUID.randomUUID(), profile = AccessProfileResolver.platformId(person);
    when(profiles.forPerson(person, profile))
        .thenThrow(new AccessDeniedException("PROFILE_NOT_OWNED_OR_INACTIVE"));
    assertThatThrownBy(() -> service.requireSigned(person, profile))
        .isInstanceOf(AccessDeniedException.class);
    verifyNoInteractions(db);
  }
}
