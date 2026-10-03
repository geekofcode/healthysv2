package org.novasos.healthysv2.document;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.Mockito.*;
import static org.mockito.ArgumentMatchers.*;
import java.util.*;
import org.junit.jupiter.api.*;
import org.novasos.healthysv2.patient.PatientAccessService;
import org.novasos.healthysv2.shared.api.error.ResourceNotFoundException;
import org.springframework.data.domain.PageRequest;
import org.springframework.jdbc.core.*;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationToken;

class DocumentPatientVisibilityRegressionTests {
    final UUID subject=UUID.randomUUID(),person=UUID.randomUUID(),patient=UUID.randomUUID();
    final HealthDocumentRepository repository=mock(HealthDocumentRepository.class);
    final DocumentStorage storage=mock(DocumentStorage.class);
    final PatientAccessService access=mock(PatientAccessService.class);
    final JdbcTemplate jdbc=mock(JdbcTemplate.class);
    final DocumentService service=new DocumentService(repository,storage,access,jdbc);
    @BeforeEach void login(){var jwt=Jwt.withTokenValue("patient").header("alg","RS256").subject(subject.toString()).build();SecurityContextHolder.getContext().setAuthentication(new JwtAuthenticationToken(jwt,List.of(new SimpleGrantedAuthority("ROLE_PATIENT"))));when(jdbc.query(eq("select id from identity.person where keycloak_user_id=? limit 1"),org.mockito.ArgumentMatchers.<ResultSetExtractor<UUID>>any(),eq(subject))).thenReturn(person);when(jdbc.queryForObject(anyString(),eq(Integer.class),any(Object[].class))).thenReturn(1);}
    @AfterEach void logout(){SecurityContextHolder.clearContext();}

    @Test void legacyFindAndDownloadCannotExposePrivateOrArchivedDocument(){
        var privateDocument=document();var archived=document();archived.setPatientVisible(true);archived.archive();
        for(var document:List.of(privateDocument,archived)){when(repository.findById(document.id())).thenReturn(Optional.of(document));assertThatThrownBy(()->service.find(document.id())).isInstanceOf(ResourceNotFoundException.class);assertThatThrownBy(()->service.download(document.id())).isInstanceOf(ResourceNotFoundException.class);}
        verifyNoInteractions(storage,access);
    }
    @Test void legacySearchForcesVisibleActiveScopeAndIgnoresCallerArchivedFilter(){
        when(repository.searchVisible(eq(patient),isNull(),isNull(),any())).thenAnswer(invocation->org.springframework.data.domain.Page.empty(invocation.getArgument(3)));
        assertThat(service.search(patient,null,"ARCHIVED",PageRequest.of(0,20)).content()).isEmpty();
        verify(repository).searchVisible(eq(patient),isNull(),isNull(),any());verify(repository,never()).search(any(),any(),any(),any());
    }
    @Test void strictKeycloakLinkAndOwnershipAreRequiredEvenIfGenericAccessServiceWouldAllow(){
        when(jdbc.query(eq("select id from identity.person where keycloak_user_id=? limit 1"),org.mockito.ArgumentMatchers.<ResultSetExtractor<UUID>>any(),eq(subject))).thenReturn(null);
        var document=document();document.setPatientVisible(true);when(repository.findById(document.id())).thenReturn(Optional.of(document));
        assertThatThrownBy(()->service.download(document.id())).isInstanceOf(AccessDeniedException.class);verifyNoInteractions(storage,access);
        verify(jdbc,never()).query(contains("or id="),org.mockito.ArgumentMatchers.<ResultSetExtractor<UUID>>any(),any(Object[].class));
    }
    private HealthDocument document(){return HealthDocument.create(patient,null,"result.pdf","private-storage-key","application/pdf",4,"checksum",person);}
}
