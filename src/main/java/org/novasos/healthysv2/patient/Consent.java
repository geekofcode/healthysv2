package org.novasos.healthysv2.patient;

import java.time.Instant;
import java.util.Objects;
import java.util.UUID;
import jakarta.persistence.*;
import org.novasos.healthysv2.shared.persistence.AuditableEntity;

@Entity
@Table(name = "consent", schema = "patient")
class Consent extends AuditableEntity {
    @Id @GeneratedValue(strategy = GenerationType.UUID) private UUID id;
    @Column(name = "patient_id", nullable = false) private UUID patientId;
    @Column(name = "grantee_person_id") private UUID granteePersonId;
    @Column(name = "grantee_organization_id") private UUID granteeOrganizationId;
    @Column(nullable = false, length = 255) private String scope;
    @Column(length = 255) private String purpose;
    @Column(columnDefinition = "text") private String reason;
    @Column(name = "granted_at", nullable = false) private Instant grantedAt;
    @Column(name = "expires_at") private Instant expiresAt;
    @Column(name = "revoked_at") private Instant revokedAt;
    @Column(nullable = false, length = 30) private String status;

    protected Consent() {}

    static Consent grant(UUID patientId, UUID granteePersonId, UUID granteeOrganizationId,
                         String scope, String purpose, String reason, Instant grantedAt, Instant expiresAt) {
        if ((granteePersonId == null) == (granteeOrganizationId == null)) throw new IllegalArgumentException("Exactly one grantee is required");
        var consent = new Consent();
        consent.patientId = Objects.requireNonNull(patientId);
        consent.granteePersonId = granteePersonId;
        consent.granteeOrganizationId = granteeOrganizationId;
        consent.scope = normalize(scope);
        consent.purpose = optional(purpose);
        consent.reason = optional(reason);
        consent.grantedAt = grantedAt == null ? Instant.now() : grantedAt;
        consent.expiresAt = expiresAt;
        consent.status = "ACTIVE";
        consent.validateExpiration();
        return consent;
    }

    void revoke(Instant at, String reason) {
        refreshStatus(at == null ? Instant.now() : at);
        if (!"ACTIVE".equals(status)) throw new IllegalStateException("Consent is not active");
        revokedAt = at == null ? Instant.now() : at;
        this.reason = optional(reason) == null ? this.reason : reason.trim();
        status = "REVOKED";
    }

    boolean refreshStatus(Instant now) {
        if ("ACTIVE".equals(status) && expiresAt != null && !expiresAt.isAfter(now)) { status = "EXPIRED"; return true; }
        return false;
    }

    boolean grants(String requestedScope, UUID personId, UUID organizationId, Instant now) {
        refreshStatus(now);
        boolean grantee = Objects.equals(granteePersonId, personId) || Objects.equals(granteeOrganizationId, organizationId);
        return "ACTIVE".equals(status) && grantee && ("FULL_RECORD".equals(scope) || scope.equals(normalize(requestedScope)));
    }

    private void validateExpiration(){if(expiresAt != null && expiresAt.isBefore(grantedAt))throw new IllegalArgumentException("Invalid consent expiration");}
    private static String normalize(String value){if(value==null||value.isBlank())throw new IllegalArgumentException("Required scope");return value.trim().toUpperCase();}
    private static String optional(String value){return value==null||value.isBlank()?null:value.trim();}
    UUID getId(){return id;} UUID getPatientId(){return patientId;} UUID getGranteePersonId(){return granteePersonId;} UUID getGranteeOrganizationId(){return granteeOrganizationId;} String getScope(){return scope;} String getPurpose(){return purpose;} String getReason(){return reason;} Instant getGrantedAt(){return grantedAt;} Instant getExpiresAt(){return expiresAt;} Instant getRevokedAt(){return revokedAt;} String getStatus(){return status;}
}
