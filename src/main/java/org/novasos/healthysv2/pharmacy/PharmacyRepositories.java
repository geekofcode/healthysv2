package org.novasos.healthysv2.pharmacy;

import jakarta.persistence.LockModeType;
import java.util.*;
import org.springframework.data.jpa.repository.*;
import org.springframework.data.repository.query.Param;

interface DispenseRepository extends JpaRepository<Dispense, UUID> {
    List<Dispense> findByPrescriptionIdOrderByDispensedAtDesc(UUID prescriptionId);
}

interface MedicationStockRepository extends JpaRepository<MedicationStock, UUID> {
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select s from MedicationStock s where s.organizationId=:organization and s.medicationId=:medication and s.batch=:batch")
    Optional<MedicationStock> lock(@Param("organization") UUID organization,
                                   @Param("medication") UUID medication, @Param("batch") String batch);

    @Query("select s from MedicationStock s where (:organization is null or s.organizationId=:organization) "
            + "and (:medication is null or s.medicationId=:medication) order by s.expirationDate")
    List<MedicationStock> search(@Param("organization") UUID organization, @Param("medication") UUID medication);
}
