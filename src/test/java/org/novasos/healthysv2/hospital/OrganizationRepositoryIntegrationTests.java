package org.novasos.healthysv2.hospital;

import static org.assertj.core.api.Assertions.*;
import jakarta.persistence.EntityManager;
import org.junit.jupiter.api.Test;
import org.novasos.healthysv2.TestcontainersConfiguration;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.transaction.annotation.Transactional;

@ActiveProfiles("test") @Import(TestcontainersConfiguration.class) @SpringBootTest @Transactional
class OrganizationRepositoryIntegrationTests {
    @Autowired OrganizationRepository repository;
    @Autowired EntityManager entityManager;

    @Test void persistsAndReloadsTheCompleteHierarchy() {
        Organization organization = Organization.create("ORG-REPO", "Hospital", null, null, null, null, null, null);
        Department department = organization.addDepartment("CARD", "Cardiology", null, null);
        department.addService("CONS", "Consultations", null, null);
        Room room = organization.addRoom(department, "101", "CARE", null); room.addBed("A", null);
        repository.saveAndFlush(organization); entityManager.clear();
        Organization loaded = repository.findById(organization.getId()).orElseThrow();
        assertThat(loaded.getDepartments()).singleElement().satisfies(d -> assertThat(d.getServices()).hasSize(1));
        assertThat(loaded.getRooms()).singleElement().satisfies(r -> assertThat(r.getBeds()).hasSize(1));
    }

    @Test void databaseEnforcesOrganizationNumberUniqueness() {
        repository.saveAndFlush(Organization.create("ORG-UNIQUE", "One", null, null, null, null, null, null));
        assertThatThrownBy(() -> repository.saveAndFlush(Organization.create("ORG-UNIQUE", "Two", null, null, null, null, null, null)))
                .isInstanceOf(DataIntegrityViolationException.class);
    }
}
