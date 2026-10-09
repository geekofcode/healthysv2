package org.novasos.healthysv2.hospital;

import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.*;
import java.util.Set;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.novasos.healthysv2.patient.CurrentUserContext;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.security.access.AccessDeniedException;

class OrganizationViewerTests {
    private final OrganizationRepository repository = mock(OrganizationRepository.class);
    private final CurrentUserContext users = mock(CurrentUserContext.class);
    private final OrganizationService service = new OrganizationService(repository,
            mock(DepartmentRepository.class), mock(CareServiceRepository.class),
            mock(RoomRepository.class), mock(BedRepository.class), users);

    @Test
    void viewerCannotReadWithoutOrganizationContext() {
        when(users.current()).thenReturn(new CurrentUserContext.UserContext(null, null, Set.of("HOSPITAL_VIEWER")));
        assertThatThrownBy(() -> service.findAll("", "", PageRequest.of(0, 10))).isInstanceOf(AccessDeniedException.class);
        verifyNoInteractions(repository);
    }

    @Test
    void viewerListingIsScopedAndOtherOrganizationIsDenied() {
        UUID organization = UUID.randomUUID();
        var pageable = PageRequest.of(0, 10);
        when(users.current()).thenReturn(new CurrentUserContext.UserContext(null, organization, Set.of("HOSPITAL_VIEWER")));
        when(repository.search(organization, "", "", pageable)).thenReturn(Page.empty(pageable));
        service.findAll("", "", pageable);
        verify(repository).search(organization, "", "", pageable);
        verify(repository, never()).findAll(pageable);
        assertThatThrownBy(() -> service.find(UUID.randomUUID())).isInstanceOf(AccessDeniedException.class);
    }
}
