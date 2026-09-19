package org.novasos.healthysv2.identity;

import java.util.Optional;
import java.util.UUID;

import org.springframework.data.jpa.repository.JpaRepository;

interface PersonRepository extends JpaRepository<Person, UUID> {

    Optional<Person> findByKeycloakUserId(UUID keycloakUserId);

    boolean existsByPersonNumber(String personNumber);

    boolean existsByKeycloakUserId(UUID keycloakUserId);
}
