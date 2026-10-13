package org.novasos.healthysv2.care;

import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

interface CareEpisodeRepository extends JpaRepository<CareEpisode, UUID> {}
