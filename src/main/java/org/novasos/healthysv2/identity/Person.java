package org.novasos.healthysv2.identity;

import java.time.LocalDate;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Objects;
import java.util.UUID;

import jakarta.persistence.CascadeType;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.Id;
import jakarta.persistence.OneToMany;
import jakarta.persistence.Table;

import org.novasos.healthysv2.shared.api.error.BusinessRuleException;
import org.novasos.healthysv2.shared.persistence.AuditableEntity;

@Entity
@Table(name = "person", schema = "identity")
public class Person extends AuditableEntity {

    @Id
    private UUID id;

    @Column(name = "person_number", nullable = false, unique = true, length = 50)
    private String personNumber;

    @Column(name = "keycloak_user_id", unique = true)
    private UUID keycloakUserId;

    @Column(name = "first_name", nullable = false, length = 120)
    private String firstName;

    @Column(name = "middle_name", length = 120)
    private String middleName;

    @Column(name = "last_name", nullable = false, length = 120)
    private String lastName;

    @Column(length = 30)
    private String gender;

    @Column(name = "birth_date")
    private LocalDate birthDate;

    @Column(name = "preferred_language_id")
    private UUID preferredLanguageId;

    @Column(nullable = false, length = 30)
    private String status;

    @OneToMany(
            mappedBy = "person",
            cascade = CascadeType.ALL,
            orphanRemoval = true,
            fetch = FetchType.LAZY)
    private final List<PersonAddress> addresses = new ArrayList<>();

    @OneToMany(
            mappedBy = "person",
            cascade = CascadeType.ALL,
            orphanRemoval = true,
            fetch = FetchType.LAZY)
    private final List<PersonContact> contacts = new ArrayList<>();

    @OneToMany(
            mappedBy = "person",
            cascade = CascadeType.ALL,
            orphanRemoval = true,
            fetch = FetchType.LAZY)
    private final List<EmergencyContact> emergencyContacts = new ArrayList<>();

    protected Person() {
    }

    private Person(
            UUID id,
            String personNumber,
            UUID keycloakUserId,
            String firstName,
            String middleName,
            String lastName,
            String gender,
            LocalDate birthDate,
            UUID preferredLanguageId,
            PersonStatus status) {
        this.id = Objects.requireNonNull(id);
        this.personNumber = required(personNumber, "personNumber");
        this.keycloakUserId = keycloakUserId;
        this.firstName = required(firstName, "firstName");
        this.middleName = optional(middleName);
        this.lastName = required(lastName, "lastName");
        this.gender = optional(gender);
        this.birthDate = birthDate;
        this.preferredLanguageId = preferredLanguageId;
        this.status = Objects.requireNonNull(status).name();
    }

    public static Person create(
            String personNumber,
            UUID keycloakUserId,
            String firstName,
            String middleName,
            String lastName,
            String gender,
            LocalDate birthDate,
            UUID preferredLanguageId) {
        return new Person(
                UUID.randomUUID(),
                personNumber,
                keycloakUserId,
                firstName,
                middleName,
                lastName,
                gender,
                birthDate,
                preferredLanguageId,
                PersonStatus.ACTIVE);
    }

    public PersonAddress addAddress(
            UUID addressId,
            String addressType,
            boolean primary) {
        if (primary && addresses.stream().anyMatch(PersonAddress::isPrimary)) {
            throw new BusinessRuleException(
                    "PRIMARY_ADDRESS_ALREADY_EXISTS",
                    "error.person.primary-address.exists");
        }
        PersonAddress address =
                PersonAddress.create(this, addressId, addressType, primary);
        addresses.add(address);
        return address;
    }

    public PersonContact addContact(
            String type,
            String value,
            boolean primary,
            boolean verified) {
        if (primary && contacts.stream()
                .anyMatch(contact -> contact.isPrimary()
                        && contact.getType().equalsIgnoreCase(type))) {
            throw new BusinessRuleException(
                    "PRIMARY_CONTACT_ALREADY_EXISTS",
                    "error.person.primary-contact.exists");
        }
        PersonContact contact =
                PersonContact.create(this, type, value, primary, verified);
        contacts.add(contact);
        return contact;
    }

    public EmergencyContact addEmergencyContact(
            String firstName,
            String lastName,
            String relationship,
            String phone,
            String email) {
        EmergencyContact contact = EmergencyContact.create(
                this,
                firstName,
                lastName,
                relationship,
                phone,
                email);
        emergencyContacts.add(contact);
        return contact;
    }

    public UUID getId() {
        return id;
    }

    public String getPersonNumber() {
        return personNumber;
    }

    public UUID getKeycloakUserId() {
        return keycloakUserId;
    }

    public String getFirstName() {
        return firstName;
    }

    public String getMiddleName() {
        return middleName;
    }

    public String getLastName() {
        return lastName;
    }

    public String getGender() {
        return gender;
    }

    public LocalDate getBirthDate() {
        return birthDate;
    }

    public UUID getPreferredLanguageId() {
        return preferredLanguageId;
    }

    public PersonStatus getStatus() {
        return PersonStatus.valueOf(status);
    }

    public List<PersonAddress> getAddresses() {
        return Collections.unmodifiableList(addresses);
    }

    public List<PersonContact> getContacts() {
        return Collections.unmodifiableList(contacts);
    }

    public List<EmergencyContact> getEmergencyContacts() {
        return Collections.unmodifiableList(emergencyContacts);
    }

    private static String required(String value, String field) {
        String normalized = optional(value);
        if (normalized == null) {
            throw new IllegalArgumentException(field + " must not be blank");
        }
        return normalized;
    }

    private static String optional(String value) {
        if (value == null) {
            return null;
        }
        String normalized = value.trim();
        return normalized.isEmpty() ? null : normalized;
    }
}
