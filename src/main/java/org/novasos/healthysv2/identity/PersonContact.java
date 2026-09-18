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
@Table(name = "person_contact", schema = "identity")
public class PersonContact {

    @Id
    private UUID id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "person_id", nullable = false)
    private Person person;

    @Column(nullable = false, length = 20)
    private String type;

    @Column(nullable = false)
    private String value;

    @Column(name = "is_primary", nullable = false)
    private boolean primary;

    @Column(nullable = false)
    private boolean verified;

    protected PersonContact() {
    }

    private PersonContact(
            Person person,
            String type,
            String value,
            boolean primary,
            boolean verified) {
        this.id = UUID.randomUUID();
        this.person = Objects.requireNonNull(person);
        this.type = requireText(type, "type");
        this.value = requireText(value, "value");
        this.primary = primary;
        this.verified = verified;
    }

    static PersonContact create(
            Person person,
            String type,
            String value,
            boolean primary,
            boolean verified) {
        return new PersonContact(person, type, value, primary, verified);
    }

    public UUID getId() {
        return id;
    }

    public String getType() {
        return type;
    }

    public String getValue() {
        return value;
    }

    public boolean isPrimary() {
        return primary;
    }

    public boolean isVerified() {
        return verified;
    }

    private static String requireText(String value, String field) {
        if (value == null || value.isBlank()) {
            throw new IllegalArgumentException(field + " must not be blank");
        }
        return value.trim();
    }
}
