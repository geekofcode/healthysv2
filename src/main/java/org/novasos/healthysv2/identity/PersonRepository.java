package org.novasos.healthysv2.identity;

import java.util.Optional;
import java.util.UUID;

import org.springframework.data.jpa.repository.JpaRepository;

interface PersonRepository extends JpaRepository<Person, UUID> {

    @org.springframework.data.jpa.repository.Query("""
            select p from Person p where :query = '' or
            lower(p.firstName) like lower(concat('%', :query, '%')) or
            lower(p.lastName) like lower(concat('%', :query, '%')) or
            lower(p.personNumber) like lower(concat('%', :query, '%'))
            """)
    org.springframework.data.domain.Page<Person> search(String query, org.springframework.data.domain.Pageable pageable);

    Optional<Person> findByKeycloakUserId(UUID keycloakUserId);

    boolean existsByPersonNumber(String personNumber);

    boolean existsByKeycloakUserId(UUID keycloakUserId);
}
