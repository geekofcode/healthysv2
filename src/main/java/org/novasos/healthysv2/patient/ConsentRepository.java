package org.novasos.healthysv2.patient;
import java.util.*; import org.springframework.data.jpa.repository.JpaRepository;
interface ConsentRepository extends JpaRepository<Consent,UUID>{List<Consent> findByPatientIdOrderByGrantedAtDesc(UUID patientId);}
