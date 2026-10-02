package org.novasos.healthysv2.professional;
import java.util.*; import org.springframework.data.jpa.repository.JpaRepository;
interface ProfessionalAvailabilityRepository extends JpaRepository<ProfessionalAvailability,UUID>{Optional<ProfessionalAvailability> findByIdAndAssignmentId(UUID id,UUID assignmentId);}
