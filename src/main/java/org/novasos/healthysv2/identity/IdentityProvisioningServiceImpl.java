package org.novasos.healthysv2.identity;

import java.util.Locale;
import java.util.UUID;
import org.springframework.stereotype.Service;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.annotation.Propagation;
import org.novasos.healthysv2.identity.api.IdentityProvisioningService;

@Service
@Transactional
class IdentityProvisioningServiceImpl implements IdentityProvisioningService {
    private final PersonRepository repository;
    private final JdbcTemplate jdbc;

    IdentityProvisioningServiceImpl(PersonRepository repository, JdbcTemplate jdbc) {
        this.repository = repository;
        this.jdbc = jdbc;
    }

    @Override
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public ProvisionedPerson provisionIdentity(
            UUID keycloakUserId,
            String firstName,
            String lastName,
            String email,
            boolean emailVerified) {
        return provisionIdentity(keycloakUserId, firstName, lastName, email,
                emailVerified, RegistrationProfile.empty());
    }

    @Override
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public ProvisionedPerson provisionIdentity(UUID keycloakUserId, String firstName,
            String lastName, String email, boolean emailVerified, RegistrationProfile profile) {
        String given = bounded(firstName, 120);
        String family = bounded(lastName, 120);
        String middle = bounded(profile.middleName(), 120);
        String gender = bounded(profile.gender(), 30);
        String phone = bounded(profile.phoneNumber(), 50);
        String mail = bounded(email, 255);
        java.time.LocalDate birthDate = birthDate(profile.birthdate());
        long lockKey = keycloakUserId.getMostSignificantBits() ^ keycloakUserId.getLeastSignificantBits();
        jdbc.queryForObject("select pg_advisory_xact_lock(?)", Object.class, lockKey);
        var existing = repository.findByKeycloakUserId(keycloakUserId);
        Person person = existing.orElseGet(() -> Person.create(number("PER", keycloakUserId),
                keycloakUserId, nameOrPending(given), null, nameOrPending(family), null, null, null));
        person.completeRegistrationProfile(given, family, middle, gender, birthDate);
        addMissingContact(person, "EMAIL", mail, emailVerified);
        // A supplied phone number is not proof of ownership.
        addMissingContact(person, "PHONE", phone, false);
        completeLanguage(person, profile.locale());
        completeAddress(person, profile.address());
        if (existing.isEmpty()) repository.saveAndFlush(person);
        return new ProvisionedPerson(person.getId(), existing.isEmpty());
    }

    private void completeLanguage(Person person, String locale) {
        if (person.getPreferredLanguageId() != null) return;
        String code = bounded(locale, 35);
        if (code == null) return;
        code = code.replace('_', '-').split("-", 2)[0].toLowerCase(Locale.ROOT);
        var languages = jdbc.query("select id from shared.language where code = ?",
                (row, index) -> row.getObject("id", UUID.class), code);
        if (!languages.isEmpty()) person.completePreferredLanguage(languages.getFirst());
    }

    private void completeAddress(Person person, RegistrationAddress address) {
        if (address == null || !person.getAddresses().isEmpty()) return;
        String line1 = bounded(address.line1(), 255);
        String line2 = bounded(address.line2(), 255);
        String city = bounded(address.city(), 150);
        String province = bounded(address.province(), 150);
        String postalCode = bounded(address.postalCode(), 30);
        String country = bounded(address.country(), 2);
        if (line1 == null || city == null) return;
        UUID countryId = countryId(country);
        UUID addressId = UUID.randomUUID();
        jdbc.update("""
                insert into shared.address (id, line1, line2, city, province, postal_code, country_id)
                values (?, ?, ?, ?, ?, ?, ?)
                """, addressId, line1, line2, city, province, postalCode, countryId);
        person.addAddress(addressId, "HOME", true);
    }

    private UUID countryId(String country) {
        if (country == null) return null;
        var countries = jdbc.query("select id from shared.country where iso2 = ?",
                (row, index) -> row.getObject("id", UUID.class), country.toUpperCase(Locale.ROOT));
        if (countries.isEmpty()) {
            throw new org.novasos.healthysv2.shared.api.error.BusinessRuleException(
                    "REGISTRATION_COUNTRY_NOT_FOUND", "error.registration.country.not-found");
        }
        return countries.getFirst();
    }

    private void addMissingContact(Person person, String type, String value, boolean verified) {
        if (value != null && person.getContacts().stream().noneMatch(c -> type.equals(c.getType()))) {
            person.addContact(type, value, true, verified);
        }
    }

    private String bounded(String value, int maximum) {
        if (value == null || value.isBlank()) return null;
        String normalized = value.trim();
        if (normalized.length() > maximum) {
            throw new org.novasos.healthysv2.shared.api.error.BusinessRuleException(
                    "INVALID_REGISTRATION_PROFILE", "error.registration.profile.invalid");
        }
        return normalized;
    }

    private java.time.LocalDate birthDate(String value) {
        if (value == null || value.isBlank()) return null;
        try {
            java.time.LocalDate date = java.time.LocalDate.parse(value.trim());
            if (date.isAfter(java.time.LocalDate.now()) || date.getYear() < 1) {
                throw new java.time.DateTimeException("Invalid birth date");
            }
            return date;
        } catch (java.time.DateTimeException exception) {
            throw new org.novasos.healthysv2.shared.api.error.BusinessRuleException(
                    "INVALID_BIRTH_DATE", "error.registration.birthdate.invalid");
        }
    }

    private String nameOrPending(String value) {
        if (value == null || value.isBlank()) {
            return "À compléter";
        }
        return value.trim().substring(0, Math.min(value.trim().length(), 120));
    }

    private String number(String prefix, UUID id) {
        return prefix + "-" + id.toString()
                .replace("-", "")
                .substring(0, 20)
                .toUpperCase(Locale.ROOT);
    }
}
