package org.novasos.healthysv2.identity;

import java.util.Locale;
import java.util.UUID;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.novasos.healthysv2.identity.api.IdentityProvisioningService;

@Service
@Transactional
class IdentityProvisioningServiceImpl implements IdentityProvisioningService {
    private final PersonRepository repository;

    IdentityProvisioningServiceImpl(PersonRepository repository) {
        this.repository = repository;
    }

    @Override
    public ProvisionedPerson provisionPatientIdentity(
            UUID keycloakUserId,
            String firstName,
            String lastName,
            String email,
            boolean emailVerified) {
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
                firstName,
                null,
                lastName,
                null,
                null,
                null);
        if (email != null && !email.isBlank()) {
            person.addContact("EMAIL", email, true, emailVerified);
        }
        repository.saveAndFlush(person);
        return new ProvisionedPerson(person.getId(), true);
    }

    private String number(String prefix, UUID id) {
        return prefix + "-" + id.toString()
                .replace("-", "")
                .substring(0, 20)
                .toUpperCase(Locale.ROOT);
    }
}
