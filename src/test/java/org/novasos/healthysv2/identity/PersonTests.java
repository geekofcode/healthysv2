package org.novasos.healthysv2.identity;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.time.LocalDate;
import java.util.UUID;

import org.junit.jupiter.api.Test;

import org.novasos.healthysv2.shared.api.error.BusinessRuleException;

class PersonTests {

    @Test
    void createsTheCompleteAggregate() {
        UUID keycloakUser = UUID.randomUUID();
        UUID address = UUID.randomUUID();

        Person person = Person.create(
                "PER-2026-0001",
                keycloakUser,
                "Ada",
                null,
                "Lovelace",
                "FEMALE",
                LocalDate.of(1815, 12, 10),
                null);

        person.addAddress(address, "HOME", true);
        person.addContact("EMAIL", "ada@example.com", true, true);
        person.addEmergencyContact(
                "Charles",
                "Babbage",
                "COLLEAGUE",
                "+1-555-0100",
                "charles@example.com");

        assertThat(person.getId()).isNotNull();
        assertThat(person.getStatus()).isEqualTo(PersonStatus.ACTIVE);
        assertThat(person.getAddresses()).singleElement()
                .satisfies(item -> {
                    assertThat(item.getAddressId()).isEqualTo(address);
                    assertThat(item.isPrimary()).isTrue();
                });
        assertThat(person.getContacts()).singleElement()
                .satisfies(item -> {
                    assertThat(item.getType()).isEqualTo("EMAIL");
                    assertThat(item.isVerified()).isTrue();
                });
        assertThat(person.getEmergencyContacts()).singleElement()
                .satisfies(item ->
                        assertThat(item.getPhone())
                                .isEqualTo("+1-555-0100"));
    }

    @Test
    void normalizesRequiredNames() {
        Person person = Person.create(
                " PER-1 ",
                null,
                " Ada ",
                " ",
                " Lovelace ",
                null,
                null,
                null);

        assertThat(person.getPersonNumber()).isEqualTo("PER-1");
        assertThat(person.getFirstName()).isEqualTo("Ada");
        assertThat(person.getMiddleName()).isNull();
        assertThat(person.getLastName()).isEqualTo("Lovelace");
    }

    @Test
    void rejectsASecondPrimaryAddress() {
        Person person = person();
        person.addAddress(UUID.randomUUID(), "HOME", true);

        assertThatThrownBy(() ->
                person.addAddress(UUID.randomUUID(), "WORK", true))
                .isInstanceOf(BusinessRuleException.class)
                .hasMessage("error.person.primary-address.exists");
    }

    @Test
    void rejectsASecondPrimaryContactOfTheSameType() {
        Person person = person();
        person.addContact("EMAIL", "first@example.com", true, true);

        assertThatThrownBy(() -> person.addContact(
                "email",
                "second@example.com",
                true,
                false))
                .isInstanceOf(BusinessRuleException.class)
                .hasMessage("error.person.primary-contact.exists");
    }

    @Test
    void rejectsBlankRequiredValues() {
        assertThatThrownBy(() -> Person.create(
                "PER-1",
                null,
                " ",
                null,
                "Lovelace",
                null,
                null,
                null))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage("firstName must not be blank");
    }

    private Person person() {
        return Person.create(
                "PER-" + UUID.randomUUID(),
                UUID.randomUUID(),
                "Test",
                null,
                "Person",
                null,
                null,
                null);
    }
}
