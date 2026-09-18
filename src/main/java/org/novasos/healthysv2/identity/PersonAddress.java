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
@Table(name = "person_address", schema = "identity")
public class PersonAddress {

    @Id
    private UUID id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "person_id", nullable = false)
    private Person person;

    @Column(name = "address_id", nullable = false)
    private UUID addressId;

    @Column(name = "address_type", nullable = false, length = 30)
    private String addressType;

    @Column(name = "is_primary", nullable = false)
    private boolean primary;

    protected PersonAddress() {
    }

    private PersonAddress(
            Person person,
            UUID addressId,
            String addressType,
            boolean primary) {
        this.id = UUID.randomUUID();
        this.person = Objects.requireNonNull(person);
        this.addressId = Objects.requireNonNull(addressId);
        this.addressType = requireText(addressType, "addressType");
        this.primary = primary;
    }

    static PersonAddress create(
            Person person,
            UUID addressId,
            String addressType,
            boolean primary) {
        return new PersonAddress(person, addressId, addressType, primary);
    }

    public UUID getId() {
        return id;
    }

    public UUID getAddressId() {
        return addressId;
    }

    public String getAddressType() {
        return addressType;
    }

    public boolean isPrimary() {
        return primary;
    }

    private static String requireText(String value, String field) {
        if (value == null || value.isBlank()) {
            throw new IllegalArgumentException(field + " must not be blank");
        }
        return value.trim();
    }
}
