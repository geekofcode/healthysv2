package org.novasos.healthysv2.care;

import java.util.UUID;
import org.novasos.healthysv2.care.api.CareSettlement;
import org.novasos.healthysv2.shared.api.error.BusinessRuleException;
import org.novasos.healthysv2.shared.api.error.ResourceNotFoundException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@Transactional
class CareSettlementService implements CareSettlement {
    private final CareEpisodeRepository episodes;
    private final CareVisitRepository visits;
    private final PhysicianQueueService queues;
    private final JdbcTemplate jdbc;
    CareSettlementService(CareEpisodeRepository episodes, CareVisitRepository visits, PhysicianQueueService queues, JdbcTemplate jdbc) {
        this.episodes=episodes; this.visits=visits; this.queues=queues; this.jdbc=jdbc;
    }
    @Override public void markSettled(UUID episodeId,UUID organization) {
        var episode=locked(episodeId,organization);
        try { episode.confirmPayment(); }
        catch(IllegalStateException e) { throw invalid(e); }
        visits.findByEpisodeIdOrderByCreatedAtAsc(episodeId).forEach(CareVisit::markPaidForScheduling);
        visits.flush();
    }
    @Override public void reopenPayment(UUID episodeId,UUID organization) {
        var episode=locked(episodeId,organization);
        try {
            episode.reopenPayment();
            for(var visit:visits.findByEpisodeIdOrderByCreatedAtAsc(episodeId)) {
                queues.invalidateVisit(visit.getId());
                visit.suspendForOutstandingBalance();
            }
        } catch(IllegalStateException e) { throw invalid(e); }
        visits.flush();
    }
    private CareEpisode locked(UUID id,UUID organization) {
        jdbc.queryForObject("select id from organization.organization where id=? for update",UUID.class,organization);
        return episodes.findLockedById(id).filter(e->e.getOrganizationId().equals(organization))
                .orElseThrow(()->new ResourceNotFoundException("CareEpisode",id));
    }
    private BusinessRuleException invalid(IllegalStateException e) {
        return new BusinessRuleException("EPISODE_SETTLEMENT_STATE",e.getMessage());
    }
}
