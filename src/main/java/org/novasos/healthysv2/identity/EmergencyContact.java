package org.novasos.healthysv2.identity;

import java.util.Objects;
import java.util.UUID;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;

@Entity
@Table(name = "emergency_contact", schema = "identity")
public class EmergencyContact {

    @Id
    private UUID id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "person_id", nullable = false)
    private Person person;

    @Column(name = "first_name", nullable = false, length = 120)
    private String firstName;

    @Column(name = "last_name", length = 120)
    private String lastName;

    @Column(length = 80)
    private String relationship;

    @Column(nullable = false, length = 50)
    private String phone;

    @Column(length = 255)
    private String email;

    protected EmergencyContact() {
    }

    private EmergencyContact(
            Person person,
            String firstName,
            String lastName,
            String relationship,
            String phone,
            String email) {
        this.id = UUID.randomUUID();
        this.person = Objects.requireNonNull(person);
        this.firstName = requireText(firstName, "firstName");
        this.lastName = optional(lastName);
        this.relationship = optional(relationship);
        this.phone = requireText(phone, "phone");
        this.email = optional(email);
    }

    static EmergencyContact create(
            Person person,
            String firstName,
            String lastName,
            String relationship,
            String phone,
            String email) {
        return new EmergencyContact(
                person,
                firstName,
                lastName,
                relationship,
                phone,
                email);
    }

    public UUID getId() {
        return id;
    }

    public String getFirstName() {
        return firstName;
    }

    public String getLastName() {
        return lastName;
    }

    public String getRelationship() {
        return relationship;
    }

    public String getPhone() {
        return phone;
    }

    public String getEmail() {
        return email;
    }

    private static String requireText(String value, String field) {
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
