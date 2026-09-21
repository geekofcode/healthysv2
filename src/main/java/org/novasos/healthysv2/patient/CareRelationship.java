package org.novasos.healthysv2.patient;

import java.time.Instant;
import java.util.Objects;
import java.util.UUID;
import jakarta.persistence.*;

@Entity
@Table(name = "care_relationship", schema = "patient")
class CareRelationship {
    @Id @GeneratedValue(strategy = GenerationType.UUID) private UUID id;
    @Column(name = "patient_id", nullable = false) private UUID patientId;
    @Column(name = "professional_id", nullable = false) private UUID professionalId;
    @Column(name = "organization_id", nullable = false) private UUID organizationId;
    @Column(name = "relationship_type", nullable = false, length = 50) private String relationshipType;
    @Column(name = "start_date", nullable = false) private Instant startDate;
    @Column(name = "end_date") private Instant endDate;
    @Column(nullable = false, length = 30) private String status;
    @Column(name = "created_at", nullable = false, updatable = false) private Instant createdAt;

    protected CareRelationship() {}

    static CareRelationship create(UUID patientId, UUID professionalId, UUID organizationId,
                                   String type, Instant startDate, Instant endDate) {
        var relationship = new CareRelationship();
        relationship.patientId = Objects.requireNonNull(patientId);
        relationship.professionalId = Objects.requireNonNull(professionalId);
        relationship.organizationId = Objects.requireNonNull(organizationId);
        relationship.relationshipType = required(type).toUpperCase();
        relationship.startDate = startDate == null ? Instant.now() : startDate;
        relationship.endDate = endDate;
        relationship.status = "ACTIVE";
        relationship.createdAt = Instant.now();
        relationship.validateDates();
        return relationship;
    }

    void end(Instant endedAt) {
        if (!"ACTIVE".equals(status)) throw new IllegalStateException("Care relationship is not active");
        endDate = endedAt == null ? Instant.now() : endedAt;
        validateDates();
        status = "ENDED";
    }

    boolean isActiveAt(Instant instant) {
        return "ACTIVE".equals(status) && !startDate.isAfter(instant)
                && (endDate == null || endDate.isAfter(instant));
    }

    private void validateDates() {
        if (endDate != null && endDate.isBefore(startDate)) throw new IllegalArgumentException("Invalid relationship dates");
    }
    private static String required(String value) { if (value == null || value.isBlank()) throw new IllegalArgumentException("Required value"); return value.trim(); }
    UUID getId(){return id;} UUID getPatientId(){return patientId;} UUID getProfessionalId(){return professionalId;} UUID getOrganizationId(){return organizationId;} String getRelationshipType(){return relationshipType;} Instant getStartDate(){return startDate;} Instant getEndDate(){return endDate;} String getStatus(){return status;} Instant getCreatedAt(){return createdAt;}
}
