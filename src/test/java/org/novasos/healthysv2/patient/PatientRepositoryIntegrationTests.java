package org.novasos.healthysv2.patient;

import static org.assertj.core.api.Assertions.assertThat;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.novasos.healthysv2.TestcontainersConfiguration;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.transaction.annotation.Transactional;

@ActiveProfiles("test")
@Import(TestcontainersConfiguration.class)
@SpringBootTest
@Transactional
class PatientRepositoryIntegrationTests {
    @Autowired PatientRepository repository;
    @Autowired JdbcTemplate jdbc;

    @Test
    void persistsAndFindsPatientByPerson() {
        UUID personId = insertPerson();
        Patient saved = repository.saveAndFlush(Patient.create(personId));
        assertThat(repository.findByPersonId(personId))
                .contains(saved);
    }

    private UUID insertPerson() {
        UUID id = UUID.randomUUID();
        jdbc.update("""
                INSERT INTO identity.person
                    (id, person_number, first_name, last_name, status)
                VALUES (?, ?, 'Test', 'Patient', 'ACTIVE')
                """, id, "PER-" + id);
        return id;
    }
}
