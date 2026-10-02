package org.novasos.healthysv2.consultation;
import java.util.*;import org.springframework.data.jpa.repository.JpaRepository;
interface ConsultationRepository extends JpaRepository<Consultation,UUID>{
 boolean existsByAppointmentId(UUID appointmentId);
}
