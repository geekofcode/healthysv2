package org.novasos.healthysv2.care;

import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

interface CareEpisodeRepository extends JpaRepository<CareEpisode, UUID> {
    java.util.List<CareEpisode> findByOrganizationIdOrderByCreatedAtDesc(UUID organizationId);
    java.util.List<CareEpisode> findByOrganizationIdAndPatientIdOrderByCreatedAtDesc(UUID organizationId, UUID patientId);
}
