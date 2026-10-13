package org.novasos.healthysv2.care;

import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

interface CareEpisodeRepository extends JpaRepository<CareEpisode, UUID> {
    @org.springframework.data.jpa.repository.Lock(jakarta.persistence.LockModeType.PESSIMISTIC_WRITE)
    @org.springframework.data.jpa.repository.Query("select e from CareEpisode e where e.id=:id")
    java.util.Optional<CareEpisode> findLockedById(@org.springframework.data.repository.query.Param("id") UUID id);
    java.util.List<CareEpisode> findByOrganizationIdOrderByCreatedAtDesc(UUID organizationId);
    java.util.List<CareEpisode> findByOrganizationIdAndPatientIdOrderByCreatedAtDesc(UUID organizationId, UUID patientId);
}
