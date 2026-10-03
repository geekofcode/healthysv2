package org.novasos.healthysv2.consultation;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.Mockito.*;
import static org.mockito.ArgumentMatchers.*;
import java.util.*;
import org.junit.jupiter.api.Test;
import org.novasos.healthysv2.consultation.api.ConsultationDtos.*;
import org.novasos.healthysv2.patient.PatientAccessService;
import org.novasos.healthysv2.shared.api.error.ResourceNotFoundException;
import org.springframework.jdbc.core.JdbcTemplate;

class ConsultationPatientVisibilityTests {
    final ConsultationRepository repository=mock(ConsultationRepository.class);
    final JdbcTemplate jdbc=mock(JdbcTemplate.class);
    final PatientAccessService access=mock(PatientAccessService.class);
    final ConsultationService service=new ConsultationService(repository,jdbc,access);

    @Test void staffCanExplicitlyPublishAndRevokeCompletedNoteUnderParentLock(){
        var consultation=Consultation.start(UUID.randomUUID(),UUID.randomUUID(),UUID.randomUUID(),null,null,"GENERAL","Private reason");
        var note=consultation.addNote(UUID.randomUUID(),"SUMMARY","Summary");consultation.complete();
        when(repository.findLockedById(consultation.getId())).thenReturn(Optional.of(consultation));
        var published=service.noteVisibility(consultation.getId(),note.getId(),new PatientVisibilityRequest(true));assertThat(published.patientVisible()).isTrue();
        service.noteVisibility(consultation.getId(),note.getId(),new PatientVisibilityRequest(false));assertThat(note.isPatientVisible()).isFalse();
        verify(repository,times(2)).findLockedById(consultation.getId());verify(access,times(2)).requireAccess(consultation.getPatientId(),"MEDICAL_RECORD","WRITE");verify(repository,times(2)).flush();
    }
    @Test void publicationCannotTargetNoteOutsideTheConsultationAndDenialDoesNotChangeIt(){
        var consultation=Consultation.start(UUID.randomUUID(),UUID.randomUUID(),UUID.randomUUID(),null,null,"GENERAL",null);
        when(repository.findLockedById(consultation.getId())).thenReturn(Optional.of(consultation));
        assertThatThrownBy(()->service.noteVisibility(consultation.getId(),UUID.randomUUID(),new PatientVisibilityRequest(true))).isInstanceOf(ResourceNotFoundException.class);
        var note=consultation.addNote(UUID.randomUUID(),"PRIVATE","Private content");
        when(access.requireAccess(consultation.getPatientId(),"MEDICAL_RECORD","WRITE")).thenThrow(new org.springframework.security.access.AccessDeniedException("No care relationship"));
        assertThatThrownBy(()->service.noteVisibility(consultation.getId(),note.getId(),new PatientVisibilityRequest(true))).isInstanceOf(org.springframework.security.access.AccessDeniedException.class);assertThat(note.isPatientVisible()).isFalse();
    }
    @Test void omittedCreationVisibilityDefaultsToPrivate(){assertThat(new NoteRequest(UUID.randomUUID(),"SUMMARY","Note").patientVisible()).isFalse();assertThat(new DiagnosisRequest(null,"PRIMARY","Diagnosis","ACTIVE",null).patientVisible()).isFalse();}
}
