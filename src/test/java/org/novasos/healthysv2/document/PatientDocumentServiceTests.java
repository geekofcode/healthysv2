package org.novasos.healthysv2.document;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.Mockito.*;
import static org.mockito.ArgumentMatchers.*;
import java.io.*;
import java.util.*;
import org.junit.jupiter.api.Test;
import org.novasos.healthysv2.audit.AuditTrail;
import org.novasos.healthysv2.identity.api.*;
import org.novasos.healthysv2.shared.api.error.*;
import org.springframework.jdbc.core.*;
import org.springframework.security.access.AccessDeniedException;

class PatientDocumentServiceTests {
    final UUID subject=UUID.randomUUID(),person=UUID.randomUUID(),patient=UUID.randomUUID();
    final PersonLookup identities=mock(PersonLookup.class);
    final HealthDocumentRepository repository=mock(HealthDocumentRepository.class);
    final DocumentStorage storage=mock(DocumentStorage.class);
    final JdbcTemplate jdbc=mock(JdbcTemplate.class);
    final AuditTrail audit=mock(AuditTrail.class);
    final PatientDocumentService service=new PatientDocumentService(identities,repository,storage,jdbc,audit);
    PatientDocumentServiceTests(){var profile=mock(PersonResponse.class);when(profile.id()).thenReturn(person);when(identities.findMe(subject)).thenReturn(profile);when(jdbc.query(eq("select id from patient.patient where person_id=?"),org.mockito.ArgumentMatchers.<ResultSetExtractor<UUID>>any(),eq(person))).thenReturn(patient);}

    @Test void privateArchivedAndOtherPatientDocumentsNeverReachStorage(){
        var privateDocument=document(patient);assertThat(privateDocument.patientVisible()).isFalse();
        var archived=document(patient);archived.setPatientVisible(true);archived.archive();
        var other=document(UUID.randomUUID());other.setPatientVisible(true);
        for(var document:List.of(privateDocument,archived,other)){
            when(repository.findById(document.id())).thenReturn(Optional.of(document));
            Class<? extends Throwable> denied=document==other?AccessDeniedException.class:ResourceNotFoundException.class;
            assertThatThrownBy(()->service.detail(subject,document.id())).isInstanceOf(denied);
            assertThatThrownBy(()->service.download(subject,document.id())).isInstanceOf(denied);
        }
        verifyNoInteractions(storage);
    }
    @Test void publishedDocumentMetadataExcludesStorageKeysAndDownloadAudits(){
        var document=document(patient);document.setPatientVisible(true);when(repository.findById(document.id())).thenReturn(Optional.of(document));
        var metadata=service.detail(subject,document.id());
        assertThat(metadata.toString()).doesNotContain("PRIVATE_STORAGE_KEY","CHECKSUM");
        when(storage.get("PRIVATE_STORAGE_KEY")).thenReturn(new DocumentStorage.StoredObject(new ByteArrayInputStream(new byte[]{1,2,3,4}),4,"application/pdf"));
        var download=service.download(subject,document.id());assertThat(download.size()).isEqualTo(4);assertThat(download.contentType()).isEqualTo("application/pdf");
        verify(audit).access(eq(person),eq(patient),isNull(),eq("DOCUMENT"),eq(document.id()),eq("DOWNLOAD"),eq("PATIENT_SELF"),any());
        document.setPatientVisible(false);
        assertThatThrownBy(()->service.download(subject,document.id())).isInstanceOf(ResourceNotFoundException.class);
        verify(storage,times(1)).get(anyString());
    }
    @Test void mismatchedContentSizeClosesStreamAndRefusesDownload()throws Exception{
        var document=document(patient);document.setPatientVisible(true);when(repository.findById(document.id())).thenReturn(Optional.of(document));
        var content=mock(InputStream.class);when(storage.get(anyString())).thenReturn(new DocumentStorage.StoredObject(content,5,"application/pdf"));
        assertThatThrownBy(()->service.download(subject,document.id())).isInstanceOf(BusinessRuleException.class);verify(content).close();
    }
    @Test void missingPatientNeverSearchesDocumentsAndConsultationFilterCannotEscapeOwnership(){
        UUID consultation=UUID.randomUUID();when(jdbc.query(eq("select patient_id from consultation.consultation where id=? and status='COMPLETED'"),org.mockito.ArgumentMatchers.<ResultSetExtractor<UUID>>any(),eq(consultation))).thenReturn(UUID.randomUUID());
        assertThatThrownBy(()->service.list(subject,consultation,0,20)).isInstanceOf(AccessDeniedException.class);verifyNoInteractions(repository,storage);
        when(jdbc.query(eq("select id from patient.patient where person_id=?"),org.mockito.ArgumentMatchers.<ResultSetExtractor<UUID>>any(),eq(person))).thenReturn(null);
        assertThatThrownBy(()->service.list(subject,null,0,20)).isInstanceOf(ResourceNotFoundException.class);verifyNoInteractions(repository,storage);
    }
    @Test void listUsesVisibleOnlyRepositoryScope(){
        when(repository.searchVisible(eq(patient),isNull(),isNull(),any())).thenAnswer(invocation->org.springframework.data.domain.Page.empty(invocation.getArgument(3)));
        assertThat(service.list(subject,null,0,20).content()).isEmpty();verify(repository).searchVisible(eq(patient),isNull(),isNull(),any());
    }
    private HealthDocument document(UUID owner){return HealthDocument.create(owner,null,"result.pdf","PRIVATE_STORAGE_KEY","application/pdf",4,"CHECKSUM",person);}
}
