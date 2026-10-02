package org.novasos.healthysv2.prescription;

import jakarta.persistence.LockModeType;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

interface PrescriptionRepository extends JpaRepository<Prescription, UUID> {
    @Query("select p from Prescription p where (:patient is null or p.patientId=:patient) "
            + "and (:organization is null or p.organizationId=:organization) "
            + "and (:status is null or p.status=:status) order by p.prescribedAt desc")
    Page<Prescription> search(@Param("patient") UUID patient, @Param("organization") UUID organization,
                              @Param("status") String status, Pageable pageable);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select p from Prescription p where p.id=:id")
    Optional<Prescription> lock(@Param("id") UUID id);
}
