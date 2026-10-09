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
        // Serialize provisioning of the same subject across application instances.
        long lockKey = keycloakUserId.getMostSignificantBits() ^ keycloakUserId.getLeastSignificantBits();
        jdbc.queryForObject("select pg_advisory_xact_lock(?)", Object.class, lockKey);
        return repository.findByKeycloakUserId(keycloakUserId)
                .map(person -> new ProvisionedPerson(person.getId(), false))
                .orElseGet(() -> create(
                        keycloakUserId,
                        firstName,
                        lastName,
                        email,
                        emailVerified));
    }

    private ProvisionedPerson create(
            UUID keycloakUserId,
            String firstName,
            String lastName,
            String email,
            boolean emailVerified) {
        Person person = Person.create(
                number("PER", keycloakUserId),
                keycloakUserId,
                nameOrPending(firstName),
                null,
                nameOrPending(lastName),
                null,
                null,
                null);
        if (email != null && !email.isBlank()) {
            person.addContact("EMAIL", email, true, emailVerified);
        }
        repository.saveAndFlush(person);
        return new ProvisionedPerson(person.getId(), true);
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
