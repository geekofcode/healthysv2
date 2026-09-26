package org.novasos.healthysv2.billing;

import jakarta.persistence.LockModeType;
import java.util.*;
import org.springframework.data.domain.*;
import org.springframework.data.jpa.repository.*;
import org.springframework.data.repository.query.Param;

interface InvoiceRepository extends JpaRepository<Invoice, UUID> {
    @Query("select i from Invoice i where (:patient is null or i.patientId=:patient) and "
            + "(:organization is null or i.organizationId=:organization) and (:status is null or i.status=:status) "
            + "order by i.issuedAt desc")
    Page<Invoice> search(@Param("patient") UUID patient, @Param("organization") UUID organization,
                         @Param("status") String status, Pageable pageable);
    @Lock(LockModeType.PESSIMISTIC_WRITE) @Query("select i from Invoice i where i.id=:id")
    Optional<Invoice> lock(@Param("id") UUID id);
}

interface PaymentRepository extends JpaRepository<Payment, UUID> {
    List<Payment> findByInvoiceIdOrderByPaidAtDesc(UUID invoiceId);
}
