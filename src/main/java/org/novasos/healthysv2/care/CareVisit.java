package org.novasos.healthysv2.care;

import jakarta.persistence.*;
import java.time.*;
import java.util.Objects;
import java.util.UUID;

@Entity
@Table(name="care_visit", schema="care")
public class CareVisit {
    public enum Status { PENDING_PAYMENT, AWAITING_SCHEDULING, SCHEDULED, IN_PROGRESS, COMPLETED, CANCELLED }
    @Id private UUID id;
    @ManyToOne(fetch=FetchType.LAZY, optional=false) @JoinColumn(name="episode_id", nullable=false)
    private CareEpisode episode;
    @Column(name="professional_id", nullable=false) private UUID professionalId;
    @Column(name="scheduled_at", nullable=false) private Instant scheduledAt;
    @Column(name="follow_up", nullable=false) private boolean followUp;
    @Enumerated(EnumType.STRING) @Column(nullable=false) private Status status;
    @Column(name="started_at") private Instant startedAt;
    @Column(name="completed_at") private Instant completedAt;
    @Column(name="created_at", nullable=false) private Instant createdAt;
    @Column(name="retention_expires_at", nullable=false) private Instant retentionExpiresAt;
    @Column(name="slot_minutes", nullable=false) private int slotMinutes = 30;
    @Version private long version;
    protected CareVisit() {}

    public static CareVisit schedule(CareEpisode episode, UUID professional, Instant date, boolean followUp,
                                    CarePolicy policy, Instant now) {
        Objects.requireNonNull(episode);
        Objects.requireNonNull(policy);
        validateDate(date, now);
        if (followUp && (!episode.isActiveAt(now) || !episode.isActiveAt(date))) {
            throw new IllegalStateException("Follow-up must be scheduled within active coverage");
        }
        if (!followUp && episode.getStatus() != CareEpisode.Status.READY) {
            throw new IllegalStateException("Initial scheduling requires a paid episode");
        }
        var visit = new CareVisit();
        visit.id = UUID.randomUUID();
        visit.episode = episode;
        visit.professionalId = Objects.requireNonNull(professional);
        visit.scheduledAt = date;
        visit.followUp = followUp;
        visit.status = Status.SCHEDULED;
        visit.createdAt = now;
        visit.retentionExpiresAt = now.atZone(ZoneOffset.UTC).plusYears(policy.retentionYears()).toInstant();
        return visit;
    }
    /** Reception reserves a visit without settling payment or starting follow-up. */
    public static CareVisit prepare(CareEpisode episode, UUID professional, Instant date,
                                    CarePolicy policy, Instant now) {
        if (episode.getStatus() != CareEpisode.Status.AWAITING_PAYMENT) {
            throw new IllegalStateException("Reception requires an unpaid episode");
        }
        validateDate(date, now);
        var visit = new CareVisit();
        visit.id = UUID.randomUUID();
        visit.episode = Objects.requireNonNull(episode);
        visit.professionalId = Objects.requireNonNull(professional);
        visit.scheduledAt = date;
        visit.status = Status.PENDING_PAYMENT;
        visit.createdAt = now;
        visit.retentionExpiresAt = now.atZone(ZoneOffset.UTC).plusYears(policy.retentionYears()).toInstant();
        return visit;
    }
    public void releaseAfterPayment() {
        if (status != Status.PENDING_PAYMENT || episode.getStatus() != CareEpisode.Status.READY) {
            throw new IllegalStateException("Confirmed payment is required");
        }
        status = Status.SCHEDULED;
    }
    void markPaidForScheduling() {
        if (status == Status.PENDING_PAYMENT) status = Status.AWAITING_SCHEDULING;
    }
    void suspendForOutstandingBalance() {
        if (startedAt != null || status == Status.IN_PROGRESS || status == Status.COMPLETED) {
            throw new IllegalStateException("Started visits cannot lose their payment eligibility");
        }
        if (status != Status.CANCELLED) status = Status.PENDING_PAYMENT;
    }
    void program(UUID professional, Instant date, int durationMinutes, Instant now) {
        if(durationMinutes<1 || durationMinutes>240) throw new IllegalArgumentException("Invalid visit slot duration");
        if (status != Status.SCHEDULED && status != Status.AWAITING_SCHEDULING) {
            throw new IllegalStateException("Payment confirmation is required before programming");
        }
        validateDate(date, now);
        if (status == Status.SCHEDULED && date.isBefore(scheduledAt)) {
            throw new IllegalArgumentException("A scheduled visit can only be postponed");
        }
        if (followUp && (!episode.isActiveAt(now) || !episode.isActiveAt(date))) {
            throw new IllegalStateException("Programming exceeds follow-up coverage");
        }
        if (!followUp && episode.getStatus() != CareEpisode.Status.READY) {
            throw new IllegalStateException("Initial visit requires a paid episode");
        }
        professionalId = Objects.requireNonNull(professional);
        scheduledAt = date;
        slotMinutes = durationMinutes;
        status = Status.SCHEDULED;
    }
    CareEpisode episode() { return episode; }
    public long getVersion() { return version; }
    public int getSlotMinutes() { return slotMinutes; }
    public Instant getCreatedAt() { return createdAt; }
    public boolean isFollowUp() { return followUp; }
    public Instant getStartedAt() { return startedAt; }
    public void reschedule(Instant date, Instant now) {
        ensureScheduled();
        validateDate(date, now);
        if (!date.isAfter(scheduledAt)) throw new IllegalArgumentException("Postponement requires a later date");
        if (followUp && !episode.isActiveAt(date)) throw new IllegalStateException("Postponement exceeds follow-up coverage");
        scheduledAt = date;
    }
    /** Application layer must verify this professional's active establishment assignment. */
    public void reassign(UUID professional) {
        ensureScheduled();
        professionalId = Objects.requireNonNull(professional);
    }
    public void start(UUID organization, Instant now) {
        ensureScheduled();
        episode.startVisit(organization, followUp, now);
        startedAt = now;
        status = Status.IN_PROGRESS;
    }
    public void complete(Instant now) {
        if (status != Status.IN_PROGRESS) throw new IllegalStateException("Visit is not in progress");
        if (Objects.requireNonNull(now).isBefore(startedAt)) throw new IllegalArgumentException("Completion precedes start");
        completedAt = now;
        status = Status.COMPLETED;
    }
    public void cancel() { ensureScheduled(); status = Status.CANCELLED; }
    private void ensureScheduled() {
        if (status != Status.SCHEDULED) throw new IllegalStateException("Only unstarted scheduled visits can be changed");
    }
    private static void validateDate(Instant date, Instant now) {
        if (Objects.requireNonNull(date).isBefore(Objects.requireNonNull(now))) throw new IllegalArgumentException("Scheduled date is in the past");
    }
    public UUID getId() { return id; }
    public UUID getProfessionalId() { return professionalId; }
    public Instant getScheduledAt() { return scheduledAt; }
    public Status getStatus() { return status; }
}
