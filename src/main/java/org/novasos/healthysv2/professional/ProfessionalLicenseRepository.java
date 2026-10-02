package org.novasos.healthysv2.professional;
import java.util.*; import org.springframework.data.jpa.repository.JpaRepository;
interface ProfessionalLicenseRepository extends JpaRepository<ProfessionalLicense,UUID>{Optional<ProfessionalLicense> findByIdAndProfessionalId(UUID id,UUID professionalId);boolean existsByNumberAndAuthority(String number,String authority);}
