// security/domain/model/PayrollLineItem.java
package za.co.handyflow.platform.security.domain.model;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.NoArgsConstructor;
import za.co.handyflow.platform.shared.TenantId;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

/**
 * PayrollLineItem — one payable unit within a PayrollPeriod.
 *
 * One REGULAR line item per completed shift. When overtime applies
 * (shift_duration > standard_hours_per_day from grade_rates), an additional
 * OVERTIME line item is created for the same shift at 1.5× the regular rate.
 *
 * All monetary values are in ZAR cents to avoid floating-point drift. The
 * shift's start/end times are snapshotted at approval time so that later
 * edits to the shift don't silently change what was paid.
 */
@Entity
@Table(name = "security_payroll_line_items")
@Getter
@NoArgsConstructor(access = lombok.AccessLevel.PROTECTED)
public class PayrollLineItem {

    @Id
    private UUID id = UUID.randomUUID();

    @Embedded
    @AttributeOverride(name = "value", column = @Column(name = "tenant_id", nullable = false))
    private TenantId tenantId;

    @Column(name = "period_id", nullable = false)
    private UUID periodId;

    @Column(name = "guard_id", nullable = false)
    private UUID guardId;

    @Column(name = "shift_id", nullable = false)
    private UUID shiftId;

    @Enumerated(EnumType.STRING)
    @Column(name = "line_type", nullable = false, length = 20)
    private LineType lineType = LineType.REGULAR;

    @Column(name = "shift_start_at", nullable = false)
    private Instant shiftStartAt;

    @Column(name = "shift_end_at", nullable = false)
    private Instant shiftEndAt;

    @Column(name = "hours_worked", nullable = false, precision = 8, scale = 2)
    private BigDecimal hoursWorked;

    @Column(name = "overtime_hours", nullable = false, precision = 8, scale = 2)
    private BigDecimal overtimeHours = BigDecimal.ZERO;

    @Column(name = "hourly_rate_cents", nullable = false)
    private int hourlyRateCents;

    @Column(name = "overtime_rate_cents", nullable = false)
    private int overtimeRateCents = 0;

    @Column(name = "gross_amount_cents", nullable = false)
    private int grossAmountCents;

    @Column
    private String notes;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    // ── Factory ────────────────────────────────────────────────────────────────

    public static PayrollLineItem regular(TenantId tenantId, UUID periodId, UUID guardId,
                                          UUID shiftId, Instant shiftStart, Instant shiftEnd,
                                          BigDecimal hoursWorked, int hourlyRateCents) {
        PayrollLineItem li   = new PayrollLineItem();
        li.tenantId          = tenantId;
        li.periodId          = periodId;
        li.guardId           = guardId;
        li.shiftId           = shiftId;
        li.lineType          = LineType.REGULAR;
        li.shiftStartAt      = shiftStart;
        li.shiftEndAt        = shiftEnd;
        li.hoursWorked       = hoursWorked;
        li.overtimeHours     = BigDecimal.ZERO;
        li.hourlyRateCents   = hourlyRateCents;
        li.overtimeRateCents = 0;
        li.grossAmountCents  = hoursWorked.multiply(
                java.math.BigDecimal.valueOf(hourlyRateCents)).intValue();
        li.createdAt         = Instant.now();
        return li;
    }

    public static PayrollLineItem overtime(TenantId tenantId, UUID periodId, UUID guardId,
                                           UUID shiftId, Instant shiftStart, Instant shiftEnd,
                                           BigDecimal overtimeHours, int regularRateCents) {
        int otRate           = (int)(regularRateCents * 1.5);
        PayrollLineItem li   = new PayrollLineItem();
        li.tenantId          = tenantId;
        li.periodId          = periodId;
        li.guardId           = guardId;
        li.shiftId           = shiftId;
        li.lineType          = LineType.OVERTIME;
        li.shiftStartAt      = shiftStart;
        li.shiftEndAt        = shiftEnd;
        li.hoursWorked       = BigDecimal.ZERO;
        li.overtimeHours     = overtimeHours;
        li.hourlyRateCents   = regularRateCents;
        li.overtimeRateCents = otRate;
        li.grossAmountCents  = overtimeHours.multiply(
                java.math.BigDecimal.valueOf(otRate)).intValue();
        li.createdAt         = Instant.now();
        return li;
    }

    public enum LineType { REGULAR, OVERTIME, ALLOWANCE, DEDUCTION }
}