package org.novasos.healthysv2.identity;

import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.util.List;
import java.util.UUID;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.mockito.Mockito;

import org.novasos.healthysv2.identity.api.CreatePersonRequest;
import org.novasos.healthysv2.shared.api.error.ConflictException;
import org.novasos.healthysv2.shared.api.error.ResourceNotFoundException;

class PersonServiceTests {

    private PersonRepository repository;
    private PersonMapper mapper;
    private PersonService service;

    @BeforeEach
    void setUp() {
        repository = Mockito.mock(PersonRepository.class);
        mapper = Mockito.mock(PersonMapper.class);
        service = new PersonService(repository, mapper);
    }

    @Test
    void createsAndPersistsAnAggregate() {
        CreatePersonRequest request = request("PER-1", UUID.randomUUID());
        when(repository.save(any(Person.class)))
                .thenAnswer(invocation -> invocation.getArgument(0));

        service.create(request);

        ArgumentCaptor<Person> captor = ArgumentCaptor.forClass(Person.class);
        verify(repository).save(captor.capture());
        verify(mapper).toResponse(captor.getValue());
    }

    @Test
    void rejectsDuplicatePersonNumberBeforeSaving() {
        CreatePersonRequest request = request("PER-1", UUID.randomUUID());
        when(repository.existsByPersonNumber("PER-1")).thenReturn(true);

        assertThatThrownBy(() -> service.create(request))
                .isInstanceOf(ConflictException.class)
                .hasMessage("error.person.number.exists");

        verify(repository, never()).save(any());
    }

    @Test
    void rejectsAnAlreadyLinkedKeycloakUser() {
        UUID keycloakUser = UUID.randomUUID();
        CreatePersonRequest request = request("PER-1", keycloakUser);
        when(repository.existsByKeycloakUserId(keycloakUser))
                .thenReturn(true);

        assertThatThrownBy(() -> service.create(request))
                .isInstanceOf(ConflictException.class)
                .hasMessage("error.person.keycloak.exists");
    }

    @Test
    void meFailsWhenTheAuthenticatedUserIsNotProvisioned() {
        UUID keycloakUser = UUID.randomUUID();
        when(repository.findByKeycloakUserId(keycloakUser))
                .thenReturn(java.util.Optional.empty());

        assertThatThrownBy(() -> service.findMe(keycloakUser))
                .isInstanceOf(ResourceNotFoundException.class);
    }

    private CreatePersonRequest request(
            String number,
            UUID keycloakUser) {
        return new CreatePersonRequest(
                number,
                keycloakUser,
                "Ada",
                null,
                "Lovelace",
                null,
                null,
                null,
                List.of(),
                List.of(),
                List.of());
    }
}
