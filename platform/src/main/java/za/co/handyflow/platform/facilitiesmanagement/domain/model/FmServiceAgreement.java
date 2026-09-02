package za.co.handyflow.platform.facilitiesmanagement.domain.model;

import jakarta.persistence.*;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;
import za.co.handyflow.platform.shared.TenantId;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.util.UUID;

/**
 * The commercial agreement between the FM company and one client, one per
 * client (a deliberate simplification — not per-site — flagged in the
 * status report), driving {@code FmBillingService}'s per-period billing
 * decision: an ACTIVE {@code RETAINER} agreement covering the period
 * bills the flat {@code monthlyFee}; otherwise (no active agreement, or
 * an active {@code TIME_AND_MATERIALS} one) billing falls through to
 * summing the client's billable work orders for the period at
 * {@code hourlyRate} — well, more precisely at each work order's own
 * recorded {@code cost}, since this module doesn't track hours worked
 * separately from cost (see {@code FmBillingService}'s own Javadoc for
 * the exact resolution logic).
 */
@Entity
@Table(name = "fm_service_agreements")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class FmServiceAgreement {

    @Id
    private UUID id = UUID.randomUUID();

    @Embedded
    @AttributeOverride(name = "value", column = @Column(name = "tenant_id", nullable = false))
    private TenantId tenantId;

    @Column(name = "client_id", nullable = false)
    private UUID clientId;

    @Column(name = "billing_type", nullable = false)
    private String billingType; // RETAINER, TIME_AND_MATERIALS

    @Column(name = "monthly_fee", precision = 15, scale = 2)
    private BigDecimal monthlyFee;

    @Column(name = "hourly_rate", precision = 15, scale = 2)
    private BigDecimal hourlyRate;

    @Column(name = "start_date", nullable = false)
    private LocalDate startDate;

    @Column(name = "end_date")
    private LocalDate endDate;

    @Column(nullable = false)
    private String status = "ACTIVE"; // ACTIVE, ENDED

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;

    @Version
    private Long version;

    public static FmServiceAgreement create(TenantId tenantId, UUID clientId, String billingType,
                                              BigDecimal monthlyFee, BigDecimal hourlyRate,
                                              LocalDate startDate, LocalDate endDate) {
        if (clientId == null) throw new IllegalArgumentException("clientId is required");
        if (startDate == null) throw new IllegalArgumentException("startDate is required");
        String type = billingType != null ? billingType.toUpperCase() : "TIME_AND_MATERIALS";
        if (!"RETAINER".equals(type) && !"TIME_AND_MATERIALS".equals(type))
            throw new IllegalArgumentException("billingType must be RETAINER or TIME_AND_MATERIALS");
        if ("RETAINER".equals(type) && (monthlyFee == null || monthlyFee.signum() <= 0))
            throw new IllegalArgumentException("A RETAINER agreement requires a positive monthlyFee");
        if (endDate != null && !endDate.isAfter(startDate))
            throw new IllegalArgumentException("endDate must be after startDate");

        FmServiceAgreement a = new FmServiceAgreement();
        a.tenantId = tenantId;
        a.clientId = clientId;
        a.billingType = type;
        a.monthlyFee = monthlyFee;
        a.hourlyRate = hourlyRate;
        a.startDate = startDate;
        a.endDate = endDate;
        a.status = "ACTIVE";
        a.createdAt = Instant.now();
        a.updatedAt = Instant.now();
        return a;
    }

    public void update(BigDecimal monthlyFee, BigDecimal hourlyRate, LocalDate endDate) {
        if ("RETAINER".equals(billingType) && monthlyFee != null && monthlyFee.signum() <= 0)
            throw new IllegalArgumentException("monthlyFee must be positive");
        if (endDate != null && !endDate.isAfter(startDate))
            throw new IllegalArgumentException("endDate must be after startDate");
        this.monthlyFee = monthlyFee;
        this.hourlyRate = hourlyRate;
        this.endDate = endDate;
        this.updatedAt = Instant.now();
    }

    public void end() {
        this.status = "ENDED";
        if (this.endDate == null) this.endDate = LocalDate.now();
        this.updatedAt = Instant.now();
    }

    public boolean isActive() { return "ACTIVE".equals(status); }
    public boolean isRetainer() { return "RETAINER".equals(billingType); }

    /** Whether this agreement was in force for the given date. */
    public boolean coversDate(LocalDate date) {
        if (!isActive()) return false;
        if (date.isBefore(startDate)) return false;
        return endDate == null || !date.isAfter(endDate);
    }

    @PreUpdate
    void onUpdate() { this.updatedAt = Instant.now(); }
}
