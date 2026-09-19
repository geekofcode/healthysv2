package org.novasos.healthysv2.identity;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.util.UUID;

import jakarta.persistence.EntityManager;
import org.junit.jupiter.api.Test;
import org.novasos.healthysv2.TestcontainersConfiguration;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.transaction.annotation.Transactional;

@ActiveProfiles("test")
@Import(TestcontainersConfiguration.class)
@SpringBootTest
@Transactional
class PersonRepositoryIntegrationTests {

    @Autowired
    PersonRepository repository;

    @Autowired
    JdbcTemplate jdbc;

    @Autowired
    EntityManager entityManager;

    @Test
    void persistsAndReloadsTheCompleteAggregate() {
        UUID keycloakUser = UUID.randomUUID();
        UUID addressId = insertAddress();
        Person person = Person.create(
                "PER-" + UUID.randomUUID(),
                keycloakUser,
                "Ada",
                null,
                "Lovelace",
                null,
                null,
                null);
        person.addAddress(addressId, "HOME", true);
        person.addContact("EMAIL", "ada@example.com", true, true);
        person.addEmergencyContact(
                "Charles",
                "Babbage",
                "COLLEAGUE",
                "+1-555-0100",
                null);

        repository.saveAndFlush(person);
        entityManager.clear();

        Person reloaded = repository.findByKeycloakUserId(keycloakUser)
                .orElseThrow();

        assertThat(reloaded.getAddresses()).hasSize(1);
        assertThat(reloaded.getContacts()).hasSize(1);
        assertThat(reloaded.getEmergencyContacts()).hasSize(1);
        assertThat(reloaded.getCreatedAt()).isNotNull();
        assertThat(reloaded.getUpdatedAt()).isNotNull();
    }

    @Test
    void findsPersonByKeycloakUserId() {
        UUID keycloakUser = UUID.randomUUID();
        repository.saveAndFlush(person("PER-" + UUID.randomUUID(), keycloakUser));

        assertThat(repository.findByKeycloakUserId(keycloakUser))
                .isPresent();
    }

    @Test
    void databaseEnforcesUniquePersonNumber() {
        String number = "PER-" + UUID.randomUUID();
        repository.saveAndFlush(person(number, UUID.randomUUID()));

        assertThatThrownBy(() ->
                repository.saveAndFlush(person(number, UUID.randomUUID())))
                .isInstanceOf(DataIntegrityViolationException.class);
    }

    @Test
    void databaseEnforcesUniqueKeycloakUser() {
        UUID keycloakUser = UUID.randomUUID();
        repository.saveAndFlush(
                person("PER-" + UUID.randomUUID(), keycloakUser));

        assertThatThrownBy(() -> repository.saveAndFlush(
                person("PER-" + UUID.randomUUID(), keycloakUser)))
                .isInstanceOf(DataIntegrityViolationException.class);
    }

    private Person person(String number, UUID keycloakUser) {
        return Person.create(
                number,
                keycloakUser,
                "Repository",
                null,
                "Test",
                null,
                null,
                null);
    }

    private UUID insertAddress() {
        UUID id = UUID.randomUUID();
        jdbc.update(
                """
                INSERT INTO shared.address (id, line1, city)
                VALUES (?, '1 Test Street', 'Test City')
                """,
                id);
        return id;
    }
}
