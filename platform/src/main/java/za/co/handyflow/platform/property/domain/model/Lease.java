// property/domain/model/Lease.java

package za.co.handyflow.platform.property.domain.model;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.NoArgsConstructor;
import za.co.handyflow.platform.shared.TenantId;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.util.UUID;

@Entity
@Table(name = "leases")
@Getter
@NoArgsConstructor(access = lombok.AccessLevel.PROTECTED)
public class Lease {

    @Id
    private UUID id = UUID.randomUUID();

    @Embedded
    @AttributeOverride(name = "value",
            column = @Column(name = "tenant_id", nullable = false))
    private TenantId tenantId;

    @Column(name = "unit_id", nullable = false)
    private UUID unitId;

    @Column(name = "customer_id")
    private UUID customerId;

    @Column(name = "lessee_name", nullable = false)
    private String lesseeName;

    @Column(name = "lessee_id_number")
    private String lesseeIdNumber;

    @Column(name = "lessee_email")
    private String lesseeEmail;

    @Column(name = "lessee_phone")
    private String lesseePhone;

    @Column(name = "start_date", nullable = false)
    private LocalDate startDate;

    @Column(name = "end_date")
    private LocalDate endDate;

    @Column(name = "monthly_rent", nullable = false, precision = 15, scale = 2)
    private BigDecimal monthlyRent;

    @Column(name = "deposit_amount", nullable = false, precision = 15, scale = 2)
    private BigDecimal depositAmount;

    @Column(name = "deposit_paid", nullable = false)
    private boolean depositPaid = false;

    @Column(name = "payment_day", nullable = false)
    private Integer paymentDay = 1;

    @Column(name = "escalation_rate", precision = 5, scale = 2)
    private BigDecimal escalationRate = BigDecimal.ZERO;

    @Column(nullable = false)
    private String status = "ACTIVE";

    private String notes;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;

    @Column(name = "deleted_at")
    private Instant deletedAt;

    @Column(name = "deleted_by")
    private UUID deletedBy;

    // NEW: backs the lease-expiry scheduler. Tracks the most urgent
    // threshold (90/60/30 days) a notice has already been sent for, so the
    // scheduler can send a fresh notice only when a lease crosses into a
    // MORE urgent bucket than last notified — not a lower/equal one, and
    // not a plain re-check that would otherwise re-send the same notice
    // every single day once a lease enters the 90-day window.
    @Column(name = "last_expiry_notice_days") private Integer lastExpiryNoticeDays;
    @Column(name = "last_expiry_notice_at")   private Instant lastExpiryNoticeAt;

    @Version
    private Long version;

    public static Lease create(TenantId tenantId, UUID unitId, UUID customerId,
                               String lesseeName, String lesseeIdNumber,
                               String lesseeEmail, String lesseePhone,
                               LocalDate startDate, LocalDate endDate,
                               BigDecimal monthlyRent, BigDecimal depositAmount,
                               Integer paymentDay, BigDecimal escalationRate) {
        Lease l = new Lease();
        l.tenantId       = tenantId;
        l.unitId         = unitId;
        l.customerId     = customerId;
        l.lesseeName     = lesseeName.trim();
        l.lesseeIdNumber = lesseeIdNumber;
        l.lesseeEmail    = lesseeEmail;
        l.lesseePhone    = lesseePhone;
        l.startDate      = startDate;
        l.endDate        = endDate;
        l.monthlyRent    = monthlyRent;
        l.depositAmount  = depositAmount != null ? depositAmount : BigDecimal.ZERO;
        l.paymentDay     = paymentDay != null ? paymentDay : 1;
        l.escalationRate = escalationRate != null ? escalationRate : BigDecimal.ZERO;
        l.status         = "ACTIVE";
        l.createdAt      = Instant.now();
        l.updatedAt      = Instant.now();
        return l;
    }

    public void terminate(String reason) {
        this.status    = "TERMINATED";
        this.notes     = reason;
        this.updatedAt = Instant.now();
    }

    public void expire() {
        this.status    = "EXPIRED";
        this.updatedAt = Instant.now();
    }

    public void confirmDeposit() {
        this.depositPaid = true;
        this.updatedAt   = Instant.now();
    }

    // NEW: called by the expiry scheduler once a notice has actually been
    // sent for a given threshold — records how urgent the last notice sent
    // was, so a later, more urgent threshold still triggers a fresh notice,
    // but the same or a less urgent one doesn't re-send.
    public void recordExpiryNotice(int thresholdDays) {
        this.lastExpiryNoticeDays = thresholdDays;
        this.lastExpiryNoticeAt   = Instant.now();
        this.updatedAt            = Instant.now();
    }

    public boolean isActive()     { return "ACTIVE".equals(status); }
    public boolean isMonthToMonth() { return endDate == null; }

    public boolean isExpiringSoon() {
        // WHY 60 days? Standard notice period for lease non-renewal in SA
        return endDate != null &&
                endDate.isBefore(LocalDate.now().plusDays(60));
    }

    public void softDelete(UUID deletedByUserId) {
        this.deletedAt = Instant.now();
        this.deletedBy = deletedByUserId;
        this.updatedAt = Instant.now();
    }

    public boolean isDeleted() { return deletedAt != null; }

    @PreUpdate
    void onUpdate() { this.updatedAt = Instant.now(); }

    /** Update editable lease terms. Null values are ignored (no change). */
    public void updateTerms(BigDecimal monthlyRent, LocalDate endDate,
                            Integer paymentDay, BigDecimal escalationRate,
                            String notes) {
        if (monthlyRent    != null) this.monthlyRent    = monthlyRent;
        if (endDate        != null) this.endDate        = endDate;
        if (paymentDay     != null) this.paymentDay     = paymentDay;
        if (escalationRate != null) this.escalationRate = escalationRate;
        if (notes          != null) this.notes          = notes;
        this.updatedAt = Instant.now();
    }

    /** Renew the lease — extend end date, optionally update rent and escalation rate. */
    public void renew(LocalDate newEndDate, BigDecimal newMonthlyRent,
                      BigDecimal newEscalationRate) {
        this.status    = "ACTIVE";   // reactivate if expired
        this.endDate   = newEndDate;
        if (newMonthlyRent    != null) this.monthlyRent    = newMonthlyRent;
        if (newEscalationRate != null) this.escalationRate = newEscalationRate;
        this.updatedAt = Instant.now();
    }

    /** Apply a percentage escalation to the monthly rent. */
    public BigDecimal applyEscalation(BigDecimal percentIncrease) {
        // e.g. 8.5% increase: newRent = currentRent * 1.085
        BigDecimal factor  = BigDecimal.ONE.add(
                percentIncrease.divide(new java.math.BigDecimal("100"),
                        6, java.math.RoundingMode.HALF_UP));
        BigDecimal newRent = this.monthlyRent.multiply(factor)
                .setScale(2, java.math.RoundingMode.HALF_UP);
        this.monthlyRent = newRent;
        this.updatedAt   = Instant.now();
        return newRent;
    }

    /** Set a specific new monthly rent directly. */
    public void setMonthlyRent(BigDecimal newRent) {
        this.monthlyRent = newRent;
        this.updatedAt   = Instant.now();
    }
}