package org.novasos.healthysv2.prescription;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.Mockito.*;
import java.util.*;
import org.junit.jupiter.api.Test;
import org.novasos.healthysv2.patient.*;
import org.springframework.data.domain.*;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.ResultSetExtractor;
import org.springframework.security.access.AccessDeniedException;

class PrescriptionScopeTests {
    final PrescriptionRepository repository=mock(PrescriptionRepository.class);
    final JdbcTemplate jdbc=mock(JdbcTemplate.class);
    final PatientAccessService access=mock(PatientAccessService.class);
    final PatientSelfAccess selves=mock(PatientSelfAccess.class);
    final CurrentUserContext users=mock(CurrentUserContext.class);
    final PrescriptionService service=new PrescriptionService(repository,jdbc,access,selves,users);

    @Test void samePatientConsentDoesNotExposePrescriptionFromAnotherOrganization(){
        UUID person=UUID.randomUUID(),selected=UUID.randomUUID();
        var prescription=Prescription.create(UUID.randomUUID(),null,UUID.randomUUID(),UUID.randomUUID(),null);
        when(users.current()).thenReturn(new CurrentUserContext.UserContext(person,selected,Set.of("DOCTOR")));
        when(repository.findById(prescription.getId())).thenReturn(Optional.of(prescription));
        assertThatThrownBy(()->service.find(prescription.getId())).isInstanceOf(AccessDeniedException.class);
        verifyNoInteractions(access);
    }

    @Test void independentSearchCannotFallBackToGlobalWildcard(){
        when(users.current()).thenReturn(new CurrentUserContext.UserContext(UUID.randomUUID(),null,Set.of("DOCTOR")));
        assertThatThrownBy(()->service.search(null,null,null,PageRequest.of(0,20))).isInstanceOf(AccessDeniedException.class);
        verifyNoInteractions(repository);
    }

    @Test void doctorCannotCreatePrescriptionUnderAnotherPrescriberIdentity(){
        UUID person=UUID.randomUUID(),professional=UUID.randomUUID(),organization=UUID.randomUUID();
        when(users.current()).thenReturn(new CurrentUserContext.UserContext(person,organization,Set.of("DOCTOR")));
        when(jdbc.query(org.mockito.ArgumentMatchers.anyString(),org.mockito.ArgumentMatchers.<ResultSetExtractor<UUID>>any(),org.mockito.ArgumentMatchers.eq(person))).thenReturn(professional);
        var request=new org.novasos.healthysv2.prescription.api.PrescriptionDtos.CreatePrescriptionRequest(UUID.randomUUID(),null,UUID.randomUUID(),organization,null,List.of());
        assertThatThrownBy(()->service.create(request)).isInstanceOf(AccessDeniedException.class);
        verifyNoInteractions(repository);
    }

    @Test void hospitalSearchAlwaysPinsSelectedOrganization(){
        UUID organization=UUID.randomUUID();var page=PageRequest.of(0,20);
        when(users.current()).thenReturn(new CurrentUserContext.UserContext(UUID.randomUUID(),organization,Set.of("HOSPITAL_ADMIN")));
        when(repository.searchScoped(null,organization,null,null,page)).thenReturn(Page.empty(page));
        service.search(null,null,null,page);
        verify(repository).searchScoped(null,organization,null,null,page);
        verify(repository,never()).search(any(),any(),any(),any());
    }
}
