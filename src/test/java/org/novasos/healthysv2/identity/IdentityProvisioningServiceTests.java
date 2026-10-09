package org.novasos.healthysv2.identity;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.jdbc.core.JdbcTemplate;

class IdentityProvisioningServiceTests {
    private final PersonRepository repository = mock(PersonRepository.class);
    private final JdbcTemplate jdbc = mock(JdbcTemplate.class);
    private final IdentityProvisioningServiceImpl service =
            new IdentityProvisioningServiceImpl(repository, jdbc);

    @Test
    void existingIdentityIsPreservedRatherThanOverwrittenFromClaims() {
        UUID subject = UUID.randomUUID();
        Person existing = Person.create("PER-EXISTING", subject, "Original", null,
                "Name", null, null, null);
        when(repository.findByKeycloakUserId(subject)).thenReturn(Optional.of(existing));
        var result = service.provisionIdentity(subject, "Changed", "Changed", "new@test.com", true);
        assertThat(result.id()).isEqualTo(existing.getId());
        assertThat(result.created()).isFalse();
        verify(repository, never()).saveAndFlush(any());
    }

    @Test
    void missingProfileClaimsAllowIdentityCreationWithoutInventingClinicalData() {
        UUID subject = UUID.randomUUID();
        when(repository.findByKeycloakUserId(subject)).thenReturn(Optional.empty());
        var result = service.provisionIdentity(subject, null, " ", null, false);
        var person = org.mockito.ArgumentCaptor.forClass(Person.class);
        verify(repository).saveAndFlush(person.capture());
        assertThat(result.created()).isTrue();
        assertThat(person.getValue().getKeycloakUserId()).isEqualTo(subject);
        assertThat(person.getValue().getFirstName()).isEqualTo("À compléter");
        assertThat(person.getValue().getContacts()).isEmpty();
        assertThat(person.getValue().getBirthDate()).isNull();
    }
    @Test
    void registrationCompletesIdentityCreatedEarlierWithoutOverwritingKnownFields() {
        UUID subject = UUID.randomUUID();
        Person existing = Person.create("PER-EXISTING", subject, "À compléter", null,
                "Established", null, null, null);
        when(repository.findByKeycloakUserId(subject)).thenReturn(Optional.of(existing));
        var profile = new org.novasos.healthysv2.identity.api.IdentityProvisioningService.RegistrationProfile(
                "Marie", "1990-04-12", "female", "+15145551234");
        service.provisionIdentity(subject, "Anne", "Changed", "anne@example.org", true, profile);
        service.provisionIdentity(subject, "Anne", "Changed", "anne@example.org", true, profile);
        assertThat(existing.getFirstName()).isEqualTo("Anne");
        assertThat(existing.getLastName()).isEqualTo("Established");
        assertThat(existing.getMiddleName()).isEqualTo("Marie");
        assertThat(existing.getBirthDate()).isEqualTo(java.time.LocalDate.of(1990, 4, 12));
        assertThat(existing.getContacts()).hasSize(2);
        assertThat(existing.getContacts().get(1).isVerified()).isFalse();
    }

    @Test
    void rejectsImpossibleFutureAndOverlongRegistrationClaimsBeforePersistence() {
        UUID subject = UUID.randomUUID();
        for (String birthdate : java.util.List.of("1990-02-30", "not-a-date",
                java.time.LocalDate.now().plusDays(1).toString())) {
            var profile = new org.novasos.healthysv2.identity.api.IdentityProvisioningService.RegistrationProfile(
                    null, birthdate, null, null);
            org.assertj.core.api.Assertions.assertThatThrownBy(() ->
                    service.provisionIdentity(subject, "Anne", "Name", null, false, profile))
                    .isInstanceOf(org.novasos.healthysv2.shared.api.error.BusinessRuleException.class);
        }
        org.assertj.core.api.Assertions.assertThatThrownBy(() -> service.provisionIdentity(
                subject, "a".repeat(121), "Name", null, false))
                .isInstanceOf(org.novasos.healthysv2.shared.api.error.BusinessRuleException.class);
        verifyNoInteractions(repository);
    }
    @Test
    void importsLanguageAndAddressOnceFromRegistrationClaims() {
        UUID subject = UUID.randomUUID();
        UUID language = UUID.randomUUID();
        UUID country = UUID.randomUUID();
        Person person = Person.create("PER-EXISTING", subject, "Anne", null, "Name", null, null, null);
        when(repository.findByKeycloakUserId(subject)).thenReturn(Optional.of(person));
        when(jdbc.query(eq("select id from shared.language where code = ?"),
                org.mockito.ArgumentMatchers.<org.springframework.jdbc.core.RowMapper<UUID>>any(), eq("fr")))
                .thenReturn(java.util.List.of(language));
        when(jdbc.query(eq("select id from shared.country where iso2 = ?"),
                org.mockito.ArgumentMatchers.<org.springframework.jdbc.core.RowMapper<UUID>>any(), eq("CA")))
                .thenReturn(java.util.List.of(country));
        var profile = new org.novasos.healthysv2.identity.api.IdentityProvisioningService.RegistrationProfile(
                null, null, null, null, "fr-CA",
                new org.novasos.healthysv2.identity.api.IdentityProvisioningService.RegistrationAddress(
                        "12 Rue Test", "Apt 2", "Québec", "QC", "G1A 1A1", "CA"));
        service.provisionIdentity(subject, "Anne", "Name", null, false, profile);
        service.provisionIdentity(subject, "Anne", "Name", null, false, profile);
        assertThat(person.getPreferredLanguageId()).isEqualTo(language);
        assertThat(person.getAddresses()).hasSize(1);
        verify(jdbc, times(1)).update(contains("insert into shared.address"), any(UUID.class),
                eq("12 Rue Test"), eq("Apt 2"), eq("Québec"), eq("QC"), eq("G1A 1A1"), eq(country));
    }

    @Test
    void unknownCountryFailsInsteadOfDroppingTheCountry() {
        UUID subject = UUID.randomUUID();
        when(repository.findByKeycloakUserId(subject)).thenReturn(Optional.empty());
        when(jdbc.query(eq("select id from shared.country where iso2 = ?"),
                org.mockito.ArgumentMatchers.<org.springframework.jdbc.core.RowMapper<UUID>>any(), eq("CA")))
                .thenReturn(java.util.List.of());
        var profile = new org.novasos.healthysv2.identity.api.IdentityProvisioningService.RegistrationProfile(
                null, null, null, null, null,
                new org.novasos.healthysv2.identity.api.IdentityProvisioningService.RegistrationAddress(
                        "12 Rue Test", null, "Québec", null, null, "CA"));
        org.assertj.core.api.Assertions.assertThatThrownBy(() -> service.provisionIdentity(
                subject, "Anne", "Name", null, false, profile))
                .isInstanceOf(org.novasos.healthysv2.shared.api.error.BusinessRuleException.class);
        verify(repository, never()).saveAndFlush(any());
    }
}
