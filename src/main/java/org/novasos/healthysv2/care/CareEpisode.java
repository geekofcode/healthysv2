package org.novasos.healthysv2.care;

import jakarta.persistence.*;
import java.time.*;
import java.util.Objects;
import java.util.UUID;

@Entity
@Table(name = "care_episode", schema = "care")
public class CareEpisode {
    public enum Status { AWAITING_PAYMENT, READY, ACTIVE, CLOSED }
    @Id private UUID id;
    @Column(name="patient_id", nullable=false) private UUID patientId;
    @Column(name="organization_id", nullable=false) private UUID organizationId;
    @Column(nullable=false, columnDefinition="text") private String reason;
    @Column(name="speciality_id") private UUID specialityId;
    @Enumerated(EnumType.STRING) @Column(nullable=false) private Status status;
    @Column(name="follow_up_seconds", nullable=false) private long followUpSeconds;
    @Column(name="started_at") private Instant startedAt;
    @Column(name="expires_at") private Instant expiresAt;
    @Column(name="created_at", nullable=false) private Instant createdAt;
    @Column(name="retention_expires_at", nullable=false) private Instant retentionExpiresAt;
    @Version private long version;

    protected CareEpisode() {}

    public static CareEpisode create(UUID patientId, UUID organizationId, String reason, CarePolicy policy, Instant now) {
        Objects.requireNonNull(policy);
        Objects.requireNonNull(now);
        if (reason == null || reason.isBlank()) throw new IllegalArgumentException("Reason is required");
        var episode = new CareEpisode();
        episode.id = UUID.randomUUID();
        episode.patientId = Objects.requireNonNull(patientId);
        episode.organizationId = Objects.requireNonNull(organizationId);
        episode.reason = reason.trim();
        episode.status = Status.AWAITING_PAYMENT;
        episode.followUpSeconds = policy.followUpDuration().getSeconds();
        episode.createdAt = now;
        episode.retentionExpiresAt = now.atZone(ZoneOffset.UTC).plusYears(policy.retentionYears()).toInstant();
        return episode;
    }

    /** Called only by the application layer after verified cash settlement. */
    public void confirmPayment() {
        if (status == Status.READY) return;
        if (status != Status.AWAITING_PAYMENT) throw new IllegalStateException("Episode cannot receive payment confirmation");
        status = Status.READY;
    }

    void startVisit(UUID organization, boolean followUp, Instant now) {
        Objects.requireNonNull(now);
        if (now.isBefore(createdAt)) throw new IllegalArgumentException("Visit precedes episode creation");
        if (!organizationId.equals(organization)) throw new IllegalArgumentException("Visit must belong to the episode establishment");
        if (followUp) {
            if (!isActiveAt(now)) throw new IllegalStateException("Follow-up coverage is inactive");
            return;
        }
        if (status != Status.READY) throw new IllegalStateException("Initial visit requires a paid, unstarted episode");
        startedAt = now;
        expiresAt = now.plusSeconds(followUpSeconds);
        status = Status.ACTIVE;
    }

    public boolean isActiveAt(Instant now) {
        Objects.requireNonNull(now);
        return status == Status.ACTIVE && !now.isBefore(startedAt) && now.isBefore(expiresAt);
    }
    public void close() { status = Status.CLOSED; }
    public void specifySpeciality(UUID speciality) { specialityId = Objects.requireNonNull(speciality); }
    public UUID getSpecialityId() { return specialityId; }
    public String getReason() { return reason; }
    public Instant getCreatedAt() { return createdAt; }
    public UUID getId() { return id; }
    public UUID getPatientId() { return patientId; }
    public UUID getOrganizationId() { return organizationId; }
    public Status getStatus() { return status; }
    public Instant getStartedAt() { return startedAt; }
    public Instant getExpiresAt() { return expiresAt; }
    public Instant getRetentionExpiresAt() { return retentionExpiresAt; }
    public long getFollowUpSeconds() { return followUpSeconds; }
}
