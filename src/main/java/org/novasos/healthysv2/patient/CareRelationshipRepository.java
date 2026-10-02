package org.novasos.healthysv2.patient;
import java.util.*; import org.springframework.data.jpa.repository.JpaRepository;
interface CareRelationshipRepository extends JpaRepository<CareRelationship,UUID>{List<CareRelationship> findByPatientIdOrderByCreatedAtDesc(UUID patientId);}
