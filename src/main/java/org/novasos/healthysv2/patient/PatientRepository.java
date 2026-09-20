package org.novasos.healthysv2.patient;

import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

interface PatientRepository extends JpaRepository<Patient, UUID> {
    Optional<Patient> findByPersonId(UUID personId);
}
