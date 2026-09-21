package org.novasos.healthysv2.professional;
import java.util.*; import org.springframework.data.jpa.repository.JpaRepository;
interface ProfessionalScheduleRepository extends JpaRepository<ProfessionalSchedule,UUID>{Optional<ProfessionalSchedule> findByIdAndAssignmentId(UUID id,UUID assignmentId);}
