package org.novasos.healthysv2.maternalandchildhealth;
import java.util.*;import org.springframework.data.jpa.repository.*;import org.springframework.data.repository.query.Param;

interface PregnancyRepository extends JpaRepository<Pregnancy,UUID>{default Optional<Pregnancy>findDetailed(UUID id){return findById(id);}List<Pregnancy>findByMotherPatientIdOrderByCreatedAtDesc(UUID motherPatientId);}
interface ChildHealthRecordRepository extends JpaRepository<ChildHealthRecord,UUID>{Optional<ChildHealthRecord>findByChildPatientId(UUID childPatientId);boolean existsByChildPatientId(UUID childPatientId);}
