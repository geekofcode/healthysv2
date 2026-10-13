package org.novasos.healthysv2.care;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.Mockito.*;
import static org.mockito.ArgumentMatchers.*;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.time.*;
import java.util.*;
import org.junit.jupiter.api.Test;
import org.novasos.healthysv2.audit.AuditTrail;
import org.novasos.healthysv2.licensing.api.LicensingAccess;
import org.novasos.healthysv2.patient.CurrentUserContext;
import org.novasos.healthysv2.patient.PatientAccessService;
import org.novasos.healthysv2.shared.api.error.BusinessRuleException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.access.AccessDeniedException;

class VisitIntakeServiceTests {
    final UUID org=UUID.randomUUID();
    final CurrentUserContext users=mock(CurrentUserContext.class);
    final CareVisitRepository visits=mock(CareVisitRepository.class);
    final JdbcTemplate jdbc=mock(JdbcTemplate.class);
    final PatientAccessService access=mock(PatientAccessService.class);
    final CarePolicy policy=new CarePolicy(Duration.ofDays(7),30);
    final VisitIntakeService service=new VisitIntakeService(visits,users,mock(LicensingAccess.class),access,jdbc,new ObjectMapper(),policy,mock(AuditTrail.class));
    @Test void physicianCannotOverwriteTheNursingIntake() {
        when(users.current()).thenReturn(new CurrentUserContext.UserContext(UUID.randomUUID(),org,Set.of("DOCTOR")));
        assertThatThrownBy(()->service.write(UUID.randomUUID(),null)).isInstanceOf(AccessDeniedException.class);
        verifyNoInteractions(visits,jdbc);
    }
    @Test void startedEncounterIntakeIsPreserved() {
        Instant now=Instant.now();var episode=CareEpisode.create(UUID.randomUUID(),org,"Reason",policy,now.minusSeconds(3600));
        episode.confirmPayment();var visit=CareVisit.schedule(episode,UUID.randomUUID(),now,false,policy,now.minusSeconds(3600));visit.start(org,now);
        when(users.current()).thenReturn(new CurrentUserContext.UserContext(UUID.randomUUID(),org,Set.of("NURSE")));
        when(jdbc.queryForObject(anyString(),eq(Integer.class),any(Object[].class))).thenReturn(1);
        when(visits.findLockedById(visit.getId())).thenReturn(Optional.of(visit));
        assertThatThrownBy(()->service.write(visit.getId(),null)).isInstanceOf(BusinessRuleException.class);
        verify(jdbc,never()).update(anyString(),any(Object[].class));
    }
}
