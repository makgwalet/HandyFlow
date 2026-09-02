package za.co.handyflow.platform.billing.domain.model;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.time.Instant;
import java.time.ZoneOffset;
import java.time.ZonedDateTime;
import java.util.UUID;

@Entity
@Table(name = "tenant_modules")
@Getter
@NoArgsConstructor
public class TenantModule {

    @Id UUID id;
    @Column(name = "tenant_id")        UUID tenantId;
    @Column(name = "module_key")       String moduleKey;
    String status = "TRIAL";
    @Column(name = "trial_ends_at")    Instant trialEndsAt;
    @Column(name = "access_until")     Instant accessUntil;
    @Column(name = "activated_at")     Instant activatedAt;
    @Column(name = "cancelled_at")     Instant cancelledAt;
    @Column(name = "activation_count") int activationCount = 1;
    @Column(name = "billing_anchor")   int billingAnchor   = 1;
    @Column(name = "created_at")       Instant createdAt;
    @Column(name = "updated_at")       Instant updatedAt;
    @Column(name = "discount_pct")
    java.math.BigDecimal discountPct;

    @Column(name = "discount_source")
    String discountSource;

    public static TenantModule createTrial(UUID tenantId, String moduleKey,
                                            int trialDays) {
        TenantModule m = new TenantModule();
        m.id               = UUID.randomUUID();
        m.tenantId         = tenantId;
        m.moduleKey        = moduleKey;
        m.status           = "TRIAL";
        m.trialEndsAt      = Instant.now().plusSeconds((long) trialDays * 86400);
        m.activatedAt      = Instant.now();
        m.activationCount  = 1;
        m.billingAnchor    = ZonedDateTime.now(ZoneOffset.UTC).getDayOfMonth();
        m.createdAt        = Instant.now();
        m.updatedAt        = Instant.now();
        return m;
    }

    public static TenantModule createActive(UUID tenantId, String moduleKey) {
        TenantModule m = new TenantModule();
        m.id              = UUID.randomUUID();
        m.tenantId        = tenantId;
        m.moduleKey       = moduleKey;
        m.status          = "ACTIVE";
        m.activatedAt     = Instant.now();
        m.activationCount = 1;
        m.billingAnchor   = ZonedDateTime.now(ZoneOffset.UTC).getDayOfMonth();
        m.createdAt       = Instant.now();
        m.updatedAt       = Instant.now();
        return m;
    }

    public void activate() {
        // WHY ACTIVE not TRIAL on re-activation?
        // Trial is a one-time offer. If a tenant cancels and re-activates,
        // they've already used their trial — they go straight to ACTIVE (paid).
        this.status           = "ACTIVE";
        this.trialEndsAt      = null;
        this.accessUntil      = null;
        this.cancelledAt      = null;
        this.activatedAt      = Instant.now();
        this.activationCount  = this.activationCount + 1;
        this.billingAnchor    = ZonedDateTime.now(ZoneOffset.UTC).getDayOfMonth();
        this.updatedAt        = Instant.now();
    }

    public void suspend() {
        this.status    = "SUSPENDED";
        this.updatedAt = Instant.now();
    }

    public void cancel() {
        // WHY grace period? Tenant paid for the month — they keep access
        // until the end of their billing period, not immediately.
        // billingAnchor = day they first activated (e.g. day 13).
        // Grace = next occurrence of that day after today.
        this.status      = "CANCELLED";
        this.cancelledAt = Instant.now();
        this.accessUntil = calculateEndOfBillingPeriod();
        this.updatedAt   = Instant.now();
    }

    public boolean isAccessible() {
        return switch (status) {
            case "ACTIVE" -> true;
            case "TRIAL"  -> trialEndsAt == null || Instant.now().isBefore(trialEndsAt);
            case "CANCELLED" -> accessUntil != null && Instant.now().isBefore(accessUntil);
            default -> false;  // SUSPENDED
        };
    }

    public boolean isFirstActivation() {
        return activationCount <= 1;
    }

    public void applyDiscount(java.math.BigDecimal pct, String source) {
        this.discountPct    = pct;
        this.discountSource = source;
        this.updatedAt      = Instant.now();
    }

    // WHY calculate billing period end this way?
    // If anchor = 13 and today is May 13, grace runs until June 13.
    // If anchor = 31 and month has 28 days, use last day of month.
    private Instant calculateEndOfBillingPeriod() {
        ZonedDateTime now = ZonedDateTime.now(ZoneOffset.UTC);
        int anchor = this.billingAnchor > 0 ? this.billingAnchor : 1;

        ZonedDateTime endOfPeriod;
        if (now.getDayOfMonth() < anchor) {
            // We're before the anchor this month — period ends this month on anchor day
            int lastDay = now.toLocalDate().lengthOfMonth();
            int day = Math.min(anchor, lastDay);
            endOfPeriod = now.withDayOfMonth(day).withHour(23).withMinute(59).withSecond(59);
        } else {
            // We're on or after anchor — period ends next month on anchor day
            ZonedDateTime nextMonth = now.plusMonths(1);
            int lastDay = nextMonth.toLocalDate().lengthOfMonth();
            int day = Math.min(anchor, lastDay);
            endOfPeriod = nextMonth.withDayOfMonth(day).withHour(23).withMinute(59).withSecond(59);
        }
        return endOfPeriod.toInstant();
    }

    public Instant calculateEndOfBillingPeriodPublic() {
        return calculateEndOfBillingPeriod();
    }
}