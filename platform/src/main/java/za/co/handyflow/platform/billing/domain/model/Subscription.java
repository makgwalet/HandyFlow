package za.co.handyflow.platform.billing.domain.model;

import jakarta.persistence.*;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;
import za.co.handyflow.platform.shared.TenantId;

import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.UUID;

@Entity
@Table(name = "subscriptions")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class Subscription {

    @Id
    private UUID id = UUID.randomUUID();

    @Embedded
    @AttributeOverride(name = "value",
        column = @Column(name = "tenant_id", nullable = false))
    private TenantId tenantId;

    @ManyToOne(fetch = FetchType.EAGER)
    @JoinColumn(name = "plan_id", nullable = false)
    private Plan plan;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private SubscriptionStatus status;

    @Column(name = "pilot_ends_at")
    private Instant pilotEndsAt;

    @Column(name = "current_period_start", nullable = false)
    private Instant currentPeriodStart;

    @Column(name = "current_period_end", nullable = false)
    private Instant currentPeriodEnd;

    @Column(name = "past_due_since")
    private Instant pastDueSince;

    @Column(name = "grace_period_days", nullable = false)
    private int gracePeriodDays = 7;

    @Column(name = "cancelled_at")
    private Instant cancelledAt;

    @Column(name = "cancellation_reason")
    private String cancellationReason;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;

    @Version
    private Long version;

    // ── Factory Methods ───────────────────────────────────────────────────────

    public static Subscription createPilot(TenantId tenantId, Plan plan) {
        Subscription sub = new Subscription();
        sub.tenantId           = tenantId;
        sub.plan               = plan;
        sub.status             = SubscriptionStatus.PILOT;
        sub.gracePeriodDays    = 7;
        sub.createdAt          = Instant.now();
        sub.updatedAt          = Instant.now();
        sub.currentPeriodStart = Instant.now();
        sub.pilotEndsAt        = Instant.now().plus(60, ChronoUnit.DAYS);
        sub.currentPeriodEnd   = sub.pilotEndsAt;
        return sub;
    }

    // ── Business Logic ────────────────────────────────────────────────────────

    public void activate() {
        validateTransition(SubscriptionStatus.ACTIVE);
        this.status            = SubscriptionStatus.ACTIVE;
        this.pilotEndsAt       = null;
        this.pastDueSince      = null;          // clear any past-due state
        this.currentPeriodStart = Instant.now();
        this.currentPeriodEnd   = Instant.now().plus(30, ChronoUnit.DAYS);
        this.updatedAt         = Instant.now();
    }

    /**
     * Mark as past due — starts the grace period clock.
     * Called when a monthly invoice is not paid by the due date.
     */
    public void markPastDue() {
        validateTransition(SubscriptionStatus.PAST_DUE);
        this.status       = SubscriptionStatus.PAST_DUE;
        this.pastDueSince = Instant.now();      // grace period starts NOW
        this.updatedAt    = Instant.now();
    }

    /**
     * Suspend — called after grace period expires with no payment.
     * All module access is blocked. Tenant can still log in and pay.
     */
    public void suspend() {
        validateTransition(SubscriptionStatus.SUSPENDED);
        this.status    = SubscriptionStatus.SUSPENDED;
        this.updatedAt = Instant.now();
    }

    /**
     * Reinstate after payment received.
     * Resets the period clock for the next billing cycle.
     */
    public void reinstate() {
        this.status             = SubscriptionStatus.ACTIVE;
        this.pastDueSince       = null;
        this.currentPeriodStart = Instant.now();
        this.currentPeriodEnd   = Instant.now().plus(30, ChronoUnit.DAYS);
        this.updatedAt          = Instant.now();
    }

    public void cancel(String reason) {
        this.status              = SubscriptionStatus.CANCELLED;
        this.cancelledAt         = Instant.now();
        this.cancellationReason  = reason;
        this.updatedAt           = Instant.now();
    }

    // ── Query helpers ─────────────────────────────────────────────────────────

    public boolean isAccessible() {
        return status == SubscriptionStatus.PILOT
                || status == SubscriptionStatus.ACTIVE
                || status == SubscriptionStatus.PAST_DUE; // grace period = still accessible
    }

    public boolean isPilotExpired() {
        return status == SubscriptionStatus.PILOT
                && pilotEndsAt != null
                && Instant.now().isAfter(pilotEndsAt);
    }

    /** True when grace period has elapsed and tenant should be suspended. */
    public boolean isGraceExpired() {
        if (pastDueSince == null) return false;
        Instant graceEnds = pastDueSince.plus(gracePeriodDays, ChronoUnit.DAYS);
        return Instant.now().isAfter(graceEnds);
    }

    /** Days remaining in grace period, 0 if expired or not past due. */
    public long graceDaysRemaining() {
        if (pastDueSince == null) return 0;
        Instant graceEnds = pastDueSince.plus(gracePeriodDays, ChronoUnit.DAYS);
        long days = ChronoUnit.DAYS.between(Instant.now(), graceEnds);
        return Math.max(0, days);
    }

    public long pilotDaysRemaining() {
        if (pilotEndsAt == null) return 0;
        long days = ChronoUnit.DAYS.between(Instant.now(), pilotEndsAt);
        return Math.max(0, days);
    }

    private void validateTransition(SubscriptionStatus target) {
        if (!status.canTransitionTo(target)) {
            throw new IllegalStateException(
                    "Cannot transition from %s to %s".formatted(status, target));
        }
    }

    @PreUpdate
    void onUpdate() { this.updatedAt = Instant.now(); }
}
