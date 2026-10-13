package org.novasos.healthysv2.care;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.Mockito.*;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.time.*;
import java.util.*;
import org.junit.jupiter.api.Test;
import org.novasos.healthysv2.audit.AuditTrail;
import org.novasos.healthysv2.licensing.api.LicensingAccess;
import org.novasos.healthysv2.patient.CurrentUserContext;
import org.novasos.healthysv2.patient.PatientAccessService;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.access.AccessDeniedException;

class ReceptionServiceTests {
    final UUID org=UUID.randomUUID();
    final CareEpisodeRepository episodes=mock(CareEpisodeRepository.class);
    final CareVisitRepository visits=mock(CareVisitRepository.class);
    final LicensingAccess licensing=mock(LicensingAccess.class);
    final CurrentUserContext users=mock(CurrentUserContext.class);
    final PatientAccessService access=mock(PatientAccessService.class);
    final JdbcTemplate jdbc=mock(JdbcTemplate.class);
    final ReceptionService service=new ReceptionService(episodes,visits,new CarePolicy(Duration.ofDays(7),30),users,
            licensing,access,jdbc,new ObjectMapper(),mock(AuditTrail.class));

    @Test void cannotOpenWithoutSelectedEstablishment() {
        when(users.current()).thenReturn(new CurrentUserContext.UserContext(UUID.randomUUID(),null,Set.of("NURSE")));
        assertThatThrownBy(()->service.open(new ReceptionDtos.OpenEpisode(UUID.randomUUID(),"Reason",UUID.randomUUID(),UUID.randomUUID(),Instant.now().plusSeconds(60))))
                .isInstanceOf(AccessDeniedException.class);
        verifyNoInteractions(episodes,visits,licensing);
    }
    @Test void expiredLicenseCannotOpenAnEpisode() {
        when(users.current()).thenReturn(new CurrentUserContext.UserContext(UUID.randomUUID(),org,Set.of("NURSE")));
        doThrow(new AccessDeniedException("expired")).when(licensing).requireActive(org);
        assertThatThrownBy(()->service.open(new ReceptionDtos.OpenEpisode(UUID.randomUUID(),"Reason",UUID.randomUUID(),UUID.randomUUID(),Instant.now().plusSeconds(60))))
                .isInstanceOf(AccessDeniedException.class);
        verifyNoInteractions(episodes,visits,jdbc);
    }
    @Test void administrativeReceptionCannotWriteVitals() {
        when(users.current()).thenReturn(new CurrentUserContext.UserContext(UUID.randomUUID(),org,Set.of("HOSPITAL_AGENT")));
        assertThatThrownBy(()->service.intake(UUID.randomUUID(),null)).isInstanceOf(AccessDeniedException.class);
        verifyNoInteractions(episodes,visits,jdbc);
    }
    @Test void clinicalDetailRequiresEffectivePatientAccess() {
        var episode=CareEpisode.create(UUID.randomUUID(),org,"Reason",new CarePolicy(Duration.ofDays(7),30),Instant.now());
        when(users.current()).thenReturn(new CurrentUserContext.UserContext(UUID.randomUUID(),org,Set.of("NURSE")));
        when(episodes.findById(episode.getId())).thenReturn(Optional.of(episode));
        doThrow(new AccessDeniedException("No clinical grant")).when(access).requireAccess(episode.getPatientId(),"MEDICAL_RECORD","READ");
        assertThatThrownBy(()->service.detail(episode.getId(),true)).isInstanceOf(AccessDeniedException.class);
        verifyNoInteractions(visits,jdbc);
    }
    @Test void localIntakeReadDoesNotOpenGlobalRecord() {
        var episode=CareEpisode.create(UUID.randomUUID(),org,"Reason",new CarePolicy(Duration.ofDays(7),30),Instant.now());
        when(users.current()).thenReturn(new CurrentUserContext.UserContext(UUID.randomUUID(),org,Set.of("NURSE")));
        when(episodes.findById(episode.getId())).thenReturn(Optional.of(episode));
        var intake=service.readIntake(episode.getId());
        assertThat(intake.observations()).isEmpty();
        assertThat(intake.vitals()).isNotNull();
        verify(licensing).requireActive(org);
        verifyNoInteractions(access,visits);
    }
    @Test void administrativeReceptionCannotReadVitals() {
        when(users.current()).thenReturn(new CurrentUserContext.UserContext(UUID.randomUUID(),org,Set.of("HOSPITAL_AGENT")));
        assertThatThrownBy(()->service.readIntake(UUID.randomUUID())).isInstanceOf(AccessDeniedException.class);
        verifyNoInteractions(episodes,visits,jdbc,access);
    }
    @Test void crossEstablishmentEpisodeCannotBeRead() {
        var episode=CareEpisode.create(UUID.randomUUID(),UUID.randomUUID(),"Reason",new CarePolicy(Duration.ofDays(7),30),Instant.now());
        when(users.current()).thenReturn(new CurrentUserContext.UserContext(UUID.randomUUID(),org,Set.of("NURSE")));
        when(episodes.findById(episode.getId())).thenReturn(Optional.of(episode));
        assertThatThrownBy(()->service.detail(episode.getId(),true)).isInstanceOf(org.novasos.healthysv2.shared.api.error.ResourceNotFoundException.class);
        verifyNoInteractions(access,visits,jdbc);
    }
}
