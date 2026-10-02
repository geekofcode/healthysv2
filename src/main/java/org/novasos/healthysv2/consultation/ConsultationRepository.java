package org.novasos.healthysv2.consultation;
import java.util.*;import org.springframework.data.jpa.repository.JpaRepository;
interface ConsultationRepository extends JpaRepository<Consultation,UUID>{
 @org.springframework.data.jpa.repository.Lock(jakarta.persistence.LockModeType.PESSIMISTIC_WRITE)
 @org.springframework.data.jpa.repository.Query("select c from Consultation c where c.id=:id")
 Optional<Consultation> findLockedById(@org.springframework.data.repository.query.Param("id")UUID id);
 org.springframework.data.domain.Page<Consultation> findByPatientIdAndStatus(UUID patientId,String status,org.springframework.data.domain.Pageable pageable);
 boolean existsByAppointmentId(UUID appointmentId);
}
