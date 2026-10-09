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
    void provisioningNeverCompletesBusinessDataFromAuthenticationClaims() {
        UUID subject = UUID.randomUUID();
        Person person = Person.create("PER-EXISTING", subject, "À compléter", "Marie",
                "Original", "female", java.time.LocalDate.of(1990, 4, 12), null);
        when(repository.findByKeycloakUserId(subject)).thenReturn(Optional.of(person));
        service.provisionIdentity(subject, "Changed", "Changed", "changed@example.org", true);
        assertThat(person.getFirstName()).isEqualTo("À compléter");
        assertThat(person.getLastName()).isEqualTo("Original");
        assertThat(person.getMiddleName()).isEqualTo("Marie");
        assertThat(person.getBirthDate()).isEqualTo(java.time.LocalDate.of(1990, 4, 12));
        assertThat(person.getContacts()).isEmpty();
    }
}
