package org.novasos.healthysv2.consultation;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.Mockito.*;
import static org.mockito.ArgumentMatchers.*;
import java.util.*;
import org.junit.jupiter.api.Test;
import org.novasos.healthysv2.audit.AuditTrail;
import org.novasos.healthysv2.identity.api.*;
import org.novasos.healthysv2.shared.api.error.ResourceNotFoundException;
import org.springframework.jdbc.core.*;
import org.springframework.security.access.AccessDeniedException;

class PatientConsultationServiceTests {
    final UUID subject=UUID.randomUUID(),person=UUID.randomUUID(),patient=UUID.randomUUID();
    final PersonLookup identities=mock(PersonLookup.class);
    final ConsultationRepository repository=mock(ConsultationRepository.class);
    final JdbcTemplate jdbc=mock(JdbcTemplate.class);
    final AuditTrail audit=mock(AuditTrail.class);
    final PatientConsultationService service=new PatientConsultationService(identities,repository,jdbc,audit);
    PatientConsultationServiceTests(){var profile=mock(PersonResponse.class);when(profile.id()).thenReturn(person);when(identities.findMe(subject)).thenReturn(profile);when(jdbc.query(eq("select id from patient.patient where person_id=?"),org.mockito.ArgumentMatchers.<ResultSetExtractor<UUID>>any(),eq(person))).thenReturn(patient);}

    @Test void onlyCompletedAndExplicitlyPublishedContentIsReturned(){
        var consultation=consultation(patient);
        var privateNote=consultation.addNote(UUID.randomUUID(),"PRIVATE","PRIVATE_NOTE");
        var shared=consultation.addNote(UUID.randomUUID(),"SUMMARY","Patient summary");shared.setPatientVisible(true);
        var privateDiagnosis=consultation.addDiagnosis(null,"PRIMARY","PRIVATE_DIAGNOSIS","ACTIVE");
        var diagnosis=consultation.addDiagnosis(null,"PRIMARY","Shared diagnosis","ACTIVE");diagnosis.setPatientVisible(true);
        consultation.addObservation(null,"OBSERVATION","PRIVATE_OBSERVATION","PRIVATE_NOTES",null);
        consultation.addTreatment("PRIVATE_TREATMENT",null,null,"ACTIVE");
        consultation.complete();when(repository.findById(consultation.getId())).thenReturn(Optional.of(consultation));
        var result=service.detail(subject,consultation.getId());
        assertThat(privateNote.isPatientVisible()).isFalse();assertThat(privateDiagnosis.isPatientVisible()).isFalse();
        assertThat(result.notes()).extracting(note->note.content()).containsExactly("Patient summary");
        assertThat(result.diagnoses()).extracting(item->item.description()).containsExactly("Shared diagnosis");
        assertThat(result.toString()).doesNotContain("PRIVATE_");
        verify(audit).access(eq(person),eq(patient),isNull(),eq("CONSULTATION"),eq(consultation.getId()),eq("READ"),eq("PATIENT_SELF"),any());
        shared.setPatientVisible(false);diagnosis.setPatientVisible(false);
        var revoked=service.detail(subject,consultation.getId());assertThat(revoked.notes()).isEmpty();assertThat(revoked.diagnoses()).isEmpty();
    }
    @Test void otherPatientOrInProgressCannotBeRead(){
        var other=consultation(UUID.randomUUID());other.complete();when(repository.findById(other.getId())).thenReturn(Optional.of(other));
        assertThatThrownBy(()->service.detail(subject,other.getId())).isInstanceOf(AccessDeniedException.class);
        var pending=consultation(patient);when(repository.findById(pending.getId())).thenReturn(Optional.of(pending));
        assertThatThrownBy(()->service.detail(subject,pending.getId())).isInstanceOf(ResourceNotFoundException.class);
    }
    @Test void listIsStrictlyScopedToCompletedPatientAndStableOrder(){
        when(repository.findByPatientIdAndStatus(eq(patient),eq("COMPLETED"),any())).thenAnswer(invocation->org.springframework.data.domain.Page.empty(invocation.getArgument(2)));
        assertThat(service.list(subject,0,20).content()).isEmpty();
        var page=org.mockito.ArgumentCaptor.forClass(org.springframework.data.domain.Pageable.class);
        verify(repository).findByPatientIdAndStatus(eq(patient),eq("COMPLETED"),page.capture());
        assertThat(page.getValue().getSort().getOrderFor("completedAt").isDescending()).isTrue();assertThat(page.getValue().getSort().getOrderFor("id")).isNotNull();
    }
    @Test void noLinkedIdentityDoesNotSearchGlobalConsultations(){
        when(identities.findMe(subject)).thenThrow(new ResourceNotFoundException("Person",subject));
        assertThatThrownBy(()->service.list(subject,0,20)).isInstanceOf(ResourceNotFoundException.class);verifyNoInteractions(repository);
    }
    private Consultation consultation(UUID owner){return Consultation.start(owner,UUID.randomUUID(),UUID.randomUUID(),null,null,"GENERAL","PRIVATE_REASON");}
}
