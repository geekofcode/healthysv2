package org.novasos.healthysv2.professional;
import java.util.*; import org.springframework.data.jpa.repository.JpaRepository;
interface ProfessionalAssignmentRepository extends JpaRepository<ProfessionalAssignment,UUID>{Optional<ProfessionalAssignment> findByIdAndProfessionalId(UUID id,UUID professionalId);}
