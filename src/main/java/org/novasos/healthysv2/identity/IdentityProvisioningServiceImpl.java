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
    public ProvisionedPerson provisionIdentity(UUID keycloakUserId, String firstName,
            String lastName, String email, boolean emailVerified) {
        String given = bounded(firstName, 120);
        String family = bounded(lastName, 120);
        String mail = bounded(email, 255);
        long lockKey = keycloakUserId.getMostSignificantBits() ^ keycloakUserId.getLeastSignificantBits();
        jdbc.queryForObject("select pg_advisory_xact_lock(?)", Object.class, lockKey);
        var existing = repository.findByKeycloakUserId(keycloakUserId);
        Person person = existing.orElseGet(() -> Person.create(number("PER", keycloakUserId),
                keycloakUserId, nameOrPending(given), null, nameOrPending(family), null, null, null));
        // Keycloak only bootstraps account identity. HEALTH'YS owns the business profile.
        if (existing.isEmpty()) {
            addMissingContact(person, "EMAIL", mail, emailVerified);
            repository.saveAndFlush(person);
        }
        return new ProvisionedPerson(person.getId(), existing.isEmpty());
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
