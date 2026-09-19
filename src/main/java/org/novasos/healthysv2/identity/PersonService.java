package org.novasos.healthysv2.identity;

import java.util.UUID;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import org.novasos.healthysv2.identity.api.CreatePersonRequest;
import org.novasos.healthysv2.identity.api.EmergencyContactRequest;
import org.novasos.healthysv2.identity.api.PersonAddressRequest;
import org.novasos.healthysv2.identity.api.PersonContactRequest;
import org.novasos.healthysv2.identity.api.PersonResponse;
import org.novasos.healthysv2.shared.api.error.ConflictException;
import org.novasos.healthysv2.shared.api.error.ResourceNotFoundException;

@Service
@Transactional
class PersonService {

    private final PersonRepository repository;
    private final PersonMapper mapper;

    PersonService(PersonRepository repository, PersonMapper mapper) {
        this.repository = repository;
        this.mapper = mapper;
    }

    PersonResponse create(CreatePersonRequest request) {
        ensureUnique(request);

        Person person = Person.create(
                request.personNumber(),
                request.keycloakUserId(),
                request.firstName(),
                request.middleName(),
                request.lastName(),
                request.gender(),
                request.birthDate(),
                request.preferredLanguageId());

        request.addresses().forEach(item -> addAddress(person, item));
        request.contacts().forEach(item -> addContact(person, item));
        request.emergencyContacts().forEach(
                item -> addEmergencyContact(person, item));

        return mapper.toResponse(repository.save(person));
    }

    @Transactional(readOnly = true)
    PersonResponse findById(UUID id) {
        return mapper.toResponse(repository.findById(id)
                .orElseThrow(() ->
                        new ResourceNotFoundException("Person", id)));
    }

    @Transactional(readOnly = true)
    PersonResponse findMe(UUID keycloakUserId) {
        return mapper.toResponse(repository.findByKeycloakUserId(keycloakUserId)
                .orElseThrow(() -> new ResourceNotFoundException(
                        "Person for Keycloak user",
                        keycloakUserId)));
    }

    private void ensureUnique(CreatePersonRequest request) {
        if (repository.existsByPersonNumber(request.personNumber())) {
            throw new ConflictException(
                    "PERSON_NUMBER_ALREADY_EXISTS",
                    "error.person.number.exists");
        }
        if (request.keycloakUserId() != null
                && repository.existsByKeycloakUserId(
                        request.keycloakUserId())) {
            throw new ConflictException(
                    "KEYCLOAK_USER_ALREADY_LINKED",
                    "error.person.keycloak.exists");
        }
    }

    private void addAddress(
            Person person,
            PersonAddressRequest request) {
        person.addAddress(
                request.addressId(),
                request.addressType(),
                request.primary());
    }

    private void addContact(
            Person person,
            PersonContactRequest request) {
        person.addContact(
                request.type(),
                request.value(),
                request.primary(),
                request.verified());
    }

    private void addEmergencyContact(
            Person person,
            EmergencyContactRequest request) {
        person.addEmergencyContact(
                request.firstName(),
                request.lastName(),
                request.relationship(),
                request.phone(),
                request.email());
    }
}
