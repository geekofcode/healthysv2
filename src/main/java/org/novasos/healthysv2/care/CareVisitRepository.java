package org.novasos.healthysv2.care;

import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

interface CareVisitRepository extends JpaRepository<CareVisit, UUID> {
    java.util.List<CareVisit> findByEpisodeIdOrderByCreatedAtAsc(UUID episodeId);
}
