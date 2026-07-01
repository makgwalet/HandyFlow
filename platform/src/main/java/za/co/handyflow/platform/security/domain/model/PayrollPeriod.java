// security/domain/model/PayrollPeriod.java
package za.co.handyflow.platform.security.domain.model;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.NoArgsConstructor;
import za.co.handyflow.platform.shared.TenantId;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.util.UUID;

/**
 * PayrollPeriod — a supervisor-approved window of shifts to be paid.
 *
 * Lifecycle: DRAFT → APPROVED → EXPORTED → PAID
 *
 * Line items are computed and frozen at APPROVED time — editing a shift
 * after a period is approved does NOT change what was paid. This is
 * intentional: payroll is a point-in-time snapshot, not a live view.
 *
 * WHY a period concept rather than just exporting all completed shifts?
 * Pay runs happen on a schedule (weekly/biweekly/monthly). A period ensures
 * each shift is paid exactly once — overlapping or double-counting is
 * prevented by the UNIQUE constraint on (period_id, shift_id, line_type).
 */
@Entity
@Table(name = "security_payroll_periods")
@Getter
@NoArgsConstructor(access = lombok.AccessLevel.PROTECTED)
public class PayrollPeriod {

    @Id
    private UUID id = UUID.randomUUID();

    @Embedded
    @AttributeOverride(name = "value", column = @Column(name = "tenant_id", nullable = false))
    private TenantId tenantId;

    @Column(name = "branch_id")
    private UUID branchId;

    @Column(nullable = false, length = 100)
    private String name;

    @Enumerated(EnumType.STRING)
    @Column(name = "period_type", nullable = false, length = 20)
    private PeriodType periodType;

    @Column(name = "period_start", nullable = false)
    private LocalDate periodStart;

    @Column(name = "period_end", nullable = false)
    private LocalDate periodEnd;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private PeriodStatus status = PeriodStatus.DRAFT;

    @Column(name = "total_hours", precision = 10, scale = 2)
    private BigDecimal totalHours;

    @Column(name = "total_amount_cents")
    private Long totalAmountCents;

    @Column(name = "approved_by")
    private UUID approvedBy;

    @Column(name = "approved_at")
    private Instant approvedAt;

    @Column(name = "exported_at")
    private Instant exportedAt;

    @Column(name = "export_format", length = 10)
    private String exportFormat;

    @Column
    private String notes;

    @Column(name = "created_by")
    private UUID createdBy;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;

    // ── Factory ────────────────────────────────────────────────────────────────

    public static PayrollPeriod create(TenantId tenantId, UUID branchId, String name,
                                       PeriodType type, LocalDate start, LocalDate end,
                                       UUID createdBy) {
        if (!end.isAfter(start)) throw new IllegalArgumentException("period_end must be after period_start");
        PayrollPeriod p   = new PayrollPeriod();
        p.tenantId        = tenantId;
        p.branchId        = branchId;
        p.name            = name.strip();
        p.periodType      = type;
        p.periodStart     = start;
        p.periodEnd       = end;
        p.status          = PeriodStatus.DRAFT;
        p.createdBy       = createdBy;
        p.createdAt       = Instant.now();
        p.updatedAt       = Instant.now();
        return p;
    }

    // ── Mutations ──────────────────────────────────────────────────────────────

    public void approve(UUID approvedBy, BigDecimal totalHours, long totalAmountCents) {
        if (this.status != PeriodStatus.DRAFT)
            throw new IllegalStateException("Only DRAFT periods can be approved (current: " + status + ")");
        this.status           = PeriodStatus.APPROVED;
        this.approvedBy       = approvedBy;
        this.approvedAt       = Instant.now();
        this.totalHours       = totalHours;
        this.totalAmountCents = totalAmountCents;
        this.updatedAt        = Instant.now();
    }

    public void markExported(String format) {
        if (this.status != PeriodStatus.APPROVED)
            throw new IllegalStateException("Only APPROVED periods can be exported");
        this.status       = PeriodStatus.EXPORTED;
        this.exportedAt   = Instant.now();
        this.exportFormat = format;
        this.updatedAt    = Instant.now();
    }

    public void markPaid() {
        if (this.status != PeriodStatus.EXPORTED)
            throw new IllegalStateException("Only EXPORTED periods can be marked PAID");
        this.status    = PeriodStatus.PAID;
        this.updatedAt = Instant.now();
    }

    public boolean isDraft()    { return status == PeriodStatus.DRAFT; }
    public boolean isApproved() { return status == PeriodStatus.APPROVED; }
    public boolean isExported() { return status == PeriodStatus.EXPORTED; }

    // ── Enums ──────────────────────────────────────────────────────────────────

    public enum PeriodType { WEEKLY, BIWEEKLY, MONTHLY }
    public enum PeriodStatus { DRAFT, APPROVED, EXPORTED, PAID }
}