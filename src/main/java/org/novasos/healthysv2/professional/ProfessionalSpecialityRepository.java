package org.novasos.healthysv2.professional;
import java.util.*; import org.springframework.data.jpa.repository.JpaRepository;
interface ProfessionalSpecialityRepository extends JpaRepository<ProfessionalSpeciality,ProfessionalSpecialityId>{Optional<ProfessionalSpeciality> findByProfessionalIdAndSpecialityCatalogId(UUID professionalId,UUID catalogueId);}
