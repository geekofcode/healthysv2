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
class PersonService implements org.novasos.healthysv2.identity.api.PersonLookup {

    private final PersonRepository repository;
    private final PersonMapper mapper;
    private final org.springframework.jdbc.core.JdbcTemplate jdbc;

    PersonService(PersonRepository repository, PersonMapper mapper, org.springframework.jdbc.core.JdbcTemplate jdbc) {
        this.repository = repository;
        this.mapper = mapper;
        this.jdbc = jdbc;
    }

    PersonResponse create(CreatePersonRequest request) {
        ensureUnique(request);

        Person person = Person.create(
                request.personNumber() == null || request.personNumber().isBlank()
                        ? "PER-" + UUID.randomUUID().toString().replace("-", "").toUpperCase(java.util.Locale.ROOT)
                        : request.personNumber(),
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

        return mapper.toResponse(repository.saveAndFlush(person));
    }

    @Transactional(readOnly = true)
    PersonResponse findById(UUID id) {
        return mapper.toResponse(repository.findById(id)
                .orElseThrow(() ->
                        new ResourceNotFoundException("Person", id)));
    }

    @Transactional(readOnly = true)
    public PersonResponse findMe(UUID keycloakUserId) {
        return mapper.toResponse(repository.findByKeycloakUserId(keycloakUserId)
                .orElseThrow(() -> new ResourceNotFoundException(
                        "Person for Keycloak user",
                        keycloakUserId)));
    }

    @Transactional(readOnly = true)
    org.novasos.healthysv2.identity.api.PersonProfileResponse profile(UUID subject) {
        Person person = meEntity(subject);
        var homes = jdbc.query("""
                select a.* from shared.address a join identity.person_address pa on pa.address_id = a.id
                where pa.person_id = ? and pa.address_type = 'HOME' order by pa.is_primary desc, pa.id
                """, (row, index) -> new org.novasos.healthysv2.identity.api.UpdatePersonProfileRequest.HomeAddress(
                        row.getString("line1"), row.getString("line2"), row.getString("city"),
                        row.getString("province"), row.getString("postal_code"), row.getObject("country_id", UUID.class)), person.getId());
        var languages = jdbc.query("select id, code, label from shared.language order by label",
                (row, index) -> new org.novasos.healthysv2.identity.api.PersonProfileResponse.LanguageOption(
                        row.getObject("id", UUID.class), row.getString("code"), row.getString("label")));
        var countries = jdbc.query("select id, iso2, name from shared.country order by name",
                (row, index) -> new org.novasos.healthysv2.identity.api.PersonProfileResponse.CountryOption(
                        row.getObject("id", UUID.class), row.getString("iso2"), row.getString("name")));
        return new org.novasos.healthysv2.identity.api.PersonProfileResponse(mapper.toResponse(person),
                homes.isEmpty() ? null : homes.getFirst(), languages, countries);
    }

    org.novasos.healthysv2.identity.api.PersonProfileResponse updateMe(UUID subject,
            org.novasos.healthysv2.identity.api.UpdatePersonProfileRequest request) {
        Person person = meEntity(subject);
        requireReference("shared.language", request.preferredLanguageId());
        if (request.homeAddress() != null) requireReference("shared.country", request.homeAddress().countryId());
        person.updateProfile(request.firstName(), request.middleName(), request.lastName(),
                request.gender(), request.birthDate(), request.preferredLanguageId());
        // Self-service can never assert contact ownership or verified status.
        var previous = person.getContacts().stream().filter(PersonContact::isVerified)
                .map(contact -> contact.getType() + "\u0000" + contact.getValue()).collect(java.util.stream.Collectors.toSet());
        person.replaceProfileContacts();
        request.contacts().forEach(contact -> person.addContact(contact.type(), contact.value(),
                contact.primary(), previous.contains(contact.type() + "\u0000" + contact.value().trim())));
        request.emergencyContacts().forEach(contact -> addEmergencyContact(person, contact));
        person.removeHomeAddresses();
        if (request.homeAddress() != null) {
            var address = request.homeAddress();
            UUID id = UUID.randomUUID();
            jdbc.update("""
                    insert into shared.address(id, line1, line2, city, province, postal_code, country_id)
                    values (?, ?, ?, ?, ?, ?, ?)
                    """, id, address.line1().trim(), address.line2(), address.city().trim(),
                    address.province(), address.postalCode(), address.countryId());
            person.addAddress(id, "HOME", person.getAddresses().stream().noneMatch(PersonAddress::isPrimary));
        }
        repository.flush();
        return profile(subject);
    }

    private Person meEntity(UUID subject) {
        return repository.findByKeycloakUserId(subject).orElseThrow(() ->
                new ResourceNotFoundException("Person for Keycloak user", subject));
    }

    private void requireReference(String table, UUID id) {
        if (id != null && !Boolean.TRUE.equals(jdbc.queryForObject(
                "select exists(select 1 from " + table + " where id = ?)", Boolean.class, id))) {
            throw new ResourceNotFoundException(table, id);
        }
    }

    @Transactional(readOnly = true)
    org.springframework.data.domain.Page<PersonResponse> search(String query,
            org.springframework.data.domain.Pageable pageable) {
        return repository.search(query == null ? "" : query.trim(), pageable).map(mapper::toResponse);
    }

    @Transactional(readOnly = true)
    org.novasos.healthysv2.identity.api.PersonPreferences preferences(UUID subject) {
        UUID personId = meEntity(subject).getId();
        var rows = jdbc.query("select theme, avatar_url from identity.person_preferences where person_id = ?",
                (row, index) -> new org.novasos.healthysv2.identity.api.PersonPreferences(
                        org.novasos.healthysv2.identity.api.PersonPreferences.Theme.valueOf(row.getString("theme")),
                        row.getString("avatar_url")), personId);
        return rows.isEmpty() ? new org.novasos.healthysv2.identity.api.PersonPreferences(
                org.novasos.healthysv2.identity.api.PersonPreferences.Theme.SYSTEM, null) : rows.getFirst();
    }

    org.novasos.healthysv2.identity.api.PersonPreferences updatePreferences(UUID subject,
            org.novasos.healthysv2.identity.api.PersonPreferences request) {
        jdbc.update("""
                insert into identity.person_preferences(person_id, theme, avatar_url) values (?, ?, ?)
                on conflict (person_id) do update set theme = excluded.theme,
                avatar_url = excluded.avatar_url, updated_at = now()
                """, meEntity(subject).getId(), request.theme().name(), request.avatarUrl());
        return request;
    }

    private void ensureUnique(CreatePersonRequest request) {
        if (request.personNumber() != null && !request.personNumber().isBlank()
                && repository.existsByPersonNumber(request.personNumber())) {
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
