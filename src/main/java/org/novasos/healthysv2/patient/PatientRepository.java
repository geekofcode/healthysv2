package org.novasos.healthysv2.patient;

import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.domain.*;
import org.springframework.data.repository.query.Param;

interface PatientRepository extends JpaRepository<Patient, UUID> {
    Optional<Patient> findByPersonId(UUID personId);
    boolean existsByPersonId(UUID personId);
    @Query("select p from Patient p where :query='' or lower(p.patientNumber) like lower(concat('%',:query,'%')) or lower(p.status) like lower(concat('%',:query,'%')) or lower(coalesce(p.bloodGroup,'')) like lower(concat('%',:query,'%')) or lower(coalesce(p.occupation,'')) like lower(concat('%',:query,'%'))")
    Page<Patient> search(@Param("query") String query, Pageable pageable);
}
