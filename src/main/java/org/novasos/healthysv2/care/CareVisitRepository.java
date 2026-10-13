package org.novasos.healthysv2.care;

import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

interface CareVisitRepository extends JpaRepository<CareVisit, UUID> {
    @org.springframework.data.jpa.repository.Lock(jakarta.persistence.LockModeType.PESSIMISTIC_WRITE)
    @org.springframework.data.jpa.repository.Query("select v from CareVisit v where v.id=:id")
    java.util.Optional<CareVisit> findLockedById(@org.springframework.data.repository.query.Param("id") UUID id);
    java.util.List<CareVisit> findByEpisodeIdOrderByCreatedAtAsc(UUID episodeId);
}
