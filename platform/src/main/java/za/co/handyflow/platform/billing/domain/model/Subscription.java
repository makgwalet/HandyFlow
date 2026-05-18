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
    private  Instant currentPeriodEnd;

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
        sub.tenantId = tenantId;
        sub.plan = plan;
        sub.status = SubscriptionStatus.PILOT;
        sub.createdAt = Instant.now();
        sub.updatedAt = Instant.now();

        // WHY 60 days? This is the HandyFlow pilot length.
        // Configurable per business decision — not hardcoded in logic.
        sub.currentPeriodStart = Instant.now();
        sub.pilotEndsAt = Instant.now().plus(60, ChronoUnit.DAYS);
        sub.currentPeriodEnd = sub.pilotEndsAt;

        return sub;
    }

    // ── Business Logic ────────────────────────────────────────────────────────

    public void activate() {
        validateTransition(SubscriptionStatus.ACTIVE);
        this.status = SubscriptionStatus.ACTIVE;
        this.pilotEndsAt = null;
        this.currentPeriodStart = Instant.now();
        this.currentPeriodEnd = Instant.now().plus(30, ChronoUnit.DAYS);
        this.updatedAt = Instant.now();
    }

    public void markPastDue() {
        validateTransition(SubscriptionStatus.PAST_DUE);
        this.status = SubscriptionStatus.PAST_DUE;
        this.updatedAt = Instant.now();
    }

    public void suspend() {
        validateTransition(SubscriptionStatus.SUSPENDED);
        this.status = SubscriptionStatus.SUSPENDED;
        this.updatedAt = Instant.now();
    }

    public void cancel(String reason) {
        this.status = SubscriptionStatus.CANCELLED;
        this.cancelledAt = Instant.now();
        this.cancellationReason = reason;
        this.updatedAt = Instant.now();
    }

    public boolean isAccessible() {
        return status == SubscriptionStatus.PILOT
                || status == SubscriptionStatus.ACTIVE
                || status == SubscriptionStatus.PAST_DUE;
    }

    public boolean isPilotExpired() {
        return status == SubscriptionStatus.PILOT
                && pilotEndsAt != null
                && Instant.now().isAfter(pilotEndsAt);
    }

    public long pilotDaysRemaining() {
        if (pilotEndsAt == null) return 0;
        long days = ChronoUnit.DAYS.between(Instant.now(), pilotEndsAt);
        return Math.max(0, days);
    }

    private void validateTransition(SubscriptionStatus target) {
        if (!status.canTransitionTo(target)) {
            throw new IllegalStateException(
                    "Cannot transition from %s to %s".formatted(status, target)
            );
        }
    }

    @PreUpdate
    void onUpdate() { this.updatedAt = Instant.now(); }
}
