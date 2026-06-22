// invoicing/domain/model/RecurringSchedule.java
package za.co.handyflow.platform.invoicing.domain.model;

import jakarta.persistence.*;
import lombok.*;
import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.annotations.UpdateTimestamp;
import za.co.handyflow.platform.shared.TenantId;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

/**
 * A recurring billing schedule — the "template" that spawns Invoice instances
 * automatically at a configured cadence.
 *
 * WHY a separate entity (not just a flag on Invoice)?
 * A schedule has its own lifecycle (ACTIVE / PAUSED / CANCELLED) and its own
 * cadence metadata.  Mixing that into Invoice would pollute every invoice row
 * with nullable scheduling columns that mean nothing for one-off invoices.
 */
@Entity
@Table(name = "recurring_schedules")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class RecurringSchedule {

    @Id
    private UUID id;

    @Embedded
    private TenantId tenantId;

    /** Null = walk-in; matches Invoice.customerId semantics. */
    @Column(name = "customer_id")
    private UUID customerId;

    /** Human label shown in the UI, e.g. "Monthly site security fee" */
    @Column(nullable = false)
    private String title;

    @Column(name = "notes", columnDefinition = "TEXT")
    private String notes;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private RecurringFrequency frequency;

    /** Day-of-month for MONTHLY, day-of-week (1=Mon) for WEEKLY, null for CUSTOM. */
    @Column(name = "frequency_day")
    private Integer frequencyDay;

    /** For CUSTOM frequency: how many days between invoices. */
    @Column(name = "custom_interval_days")
    private Integer customIntervalDays;

    @Column(name = "start_date", nullable = false)
    private Instant startDate;

    /** Null = runs indefinitely. */
    @Column(name = "end_date")
    private Instant endDate;

    @Column(name = "next_run_at", nullable = false)
    private Instant nextRunAt;

    @Column(name = "last_run_at")
    private Instant lastRunAt;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private RecurringScheduleStatus status;

    /** Walk-in fields — mirrored from Quote/Invoice for completeness. */
    @Column(name = "walkin_client_name")
    private String walkinClientName;

    @Column(name = "walkin_client_email")
    private String walkinClientEmail;

    @Column(name = "walkin_client_phone")
    private String walkinClientPhone;

    @Column(nullable = false, length = 3)
    private String currency = "ZAR";

    @OneToMany(mappedBy = "schedule", cascade = CascadeType.ALL, orphanRemoval = true, fetch = FetchType.EAGER)
    @OrderBy("sortOrder ASC")
    private List<RecurringLineItem> lineItems = new ArrayList<>();

    @CreationTimestamp
    @Column(name = "created_at", updatable = false)
    private Instant createdAt;

    @UpdateTimestamp
    @Column(name = "updated_at")
    private Instant updatedAt;

    /**
     * When true, the schedule does NOT auto-fire at 02:45.
     * Instead it waits for a manual "log hours and generate invoice" call.
     * The scheduler still tracks the contract period and sends reminders.
     *
     * WHY a flag rather than a different schedule type?
     * 99% of the schedule logic is identical.  The only difference is whether
     * the quantity comes from the template (fixed) or from an hours log entry
     * (variable).  A single boolean avoids duplicating the entire entity.
     */
    @Column(name = "variable_hours", nullable = false)
    private boolean variableHours = false;

    /** Rate per hour — used when variableHours = true. */
    @Column(name = "rate_per_hour", precision = 12, scale = 2)
    private BigDecimal ratePerHour;

    /** Minimum hours that must be billed per cycle, even if fewer were worked. */
    @Column(name = "minimum_hours_per_cycle", precision = 10, scale = 2)
    private BigDecimal minimumHoursPerCycle;

    /** VAT rate applied to the hours × rate line. */
    @Column(name = "hours_vat_rate", precision = 5, scale = 2)
    private BigDecimal hoursVatRate = new BigDecimal("15.00");

    /** Contract start — for 12-month contracts, used to calculate remaining cycles. */
    @Column(name = "contract_start_date")
    private Instant contractStartDate;

    /** Contract end — schedule auto-completes when this is reached. */
    @Column(name = "contract_end_date")
    private Instant contractEndDate;

    /** Total contracted hours over the full contract term (informational). */
    @Column(name = "contracted_total_hours", precision = 10, scale = 2)
    private BigDecimal contractedTotalHours;

    /** Cumulative hours billed across all cycles so far. */
    @Column(name = "total_hours_billed", precision = 10, scale = 2)
    private BigDecimal totalHoursBilled = BigDecimal.ZERO;

    // ── Factory ───────────────────────────────────────────────────────────────

    public static RecurringSchedule create(
            TenantId tenantId,
            UUID customerId,
            String title,
            String notes,
            RecurringFrequency frequency,
            Integer frequencyDay,
            Integer customIntervalDays,
            Instant startDate,
            Instant endDate,
            String walkinClientName,
            String walkinClientEmail,
            String walkinClientPhone
    ) {
        var s = new RecurringSchedule();
        s.id                  = UUID.randomUUID();
        s.tenantId            = tenantId;
        s.customerId          = customerId;
        s.title               = title;
        s.notes               = notes;
        s.frequency           = frequency;
        s.frequencyDay        = frequencyDay;
        s.customIntervalDays  = customIntervalDays;
        s.startDate           = startDate;
        s.endDate             = endDate;
        s.nextRunAt           = startDate;   // first invoice fires at startDate
        s.status              = RecurringScheduleStatus.ACTIVE;
        s.walkinClientName    = walkinClientName;
        s.walkinClientEmail   = walkinClientEmail;
        s.walkinClientPhone   = walkinClientPhone;
        return s;
    }

    // ── Behaviour ─────────────────────────────────────────────────────────────

    public void addLineItem(RecurringLineItem item) {
        lineItems.add(item);
    }

    public void pause()  { this.status = RecurringScheduleStatus.PAUSED;    }
    public void resume() { this.status = RecurringScheduleStatus.ACTIVE;    }
    public void cancel() { this.status = RecurringScheduleStatus.CANCELLED; }

    /**
     * Called by the scheduler after successfully spawning an invoice.
     * Advances nextRunAt and, if endDate has passed, auto-cancels.
     */
    public void markRan(Instant now) {
        this.lastRunAt = now;
        this.nextRunAt = computeNextRun(now);
        if (endDate != null && nextRunAt.isAfter(endDate)) {
            this.status = RecurringScheduleStatus.COMPLETED;
        }
    }

    private Instant computeNextRun(Instant from) {
        return switch (frequency) {
            case DAILY   -> from.plusSeconds(86_400);
            case WEEKLY  -> from.plusSeconds(86_400 * 7);
            case MONTHLY -> from.plusSeconds(86_400 * 30L);  // approximate; good enough for scheduling
            case CUSTOM  -> from.plusSeconds(86_400L * (customIntervalDays != null ? customIntervalDays : 30));
        };
    }

    public BigDecimal getSubtotal() {
        return lineItems.stream()
                .map(RecurringLineItem::getLineTotal)
                .reduce(BigDecimal.ZERO, BigDecimal::add);
    }

    public BigDecimal getVatTotal() {
        return lineItems.stream()
                .map(RecurringLineItem::getVatAmount)
                .reduce(BigDecimal.ZERO, BigDecimal::add);
    }

    public BigDecimal getTotal() {
        return getSubtotal().add(getVatTotal());
    }

    public static RecurringSchedule createVariableHoursContract(
            TenantId tenantId,
            UUID customerId,
            String title,
            String notes,
            RecurringFrequency frequency,
            BigDecimal ratePerHour,
            BigDecimal minimumHoursPerCycle,
            BigDecimal hoursVatRate,
            Instant contractStartDate,
            Instant contractEndDate,
            BigDecimal contractedTotalHours,
            String walkinClientName,
            String walkinClientEmail,
            String walkinClientPhone
    ) {
        var s = new RecurringSchedule();
        s.id                    = UUID.randomUUID();
        s.tenantId              = tenantId;
        s.customerId            = customerId;
        s.title                 = title;
        s.notes                 = notes;
        s.frequency             = frequency;
        s.variableHours         = true;
        s.ratePerHour           = ratePerHour;
        s.minimumHoursPerCycle  = minimumHoursPerCycle;
        s.hoursVatRate          = hoursVatRate != null ? hoursVatRate : new BigDecimal("15.00");
        s.contractStartDate     = contractStartDate;
        s.contractEndDate       = contractEndDate;
        s.nextRunAt             = contractStartDate;
        s.startDate             = contractStartDate;
        s.endDate               = contractEndDate;
        s.contractedTotalHours  = contractedTotalHours;
        s.totalHoursBilled      = BigDecimal.ZERO;
        s.status                = RecurringScheduleStatus.ACTIVE;
        s.walkinClientName      = walkinClientName;
        s.walkinClientEmail     = walkinClientEmail;
        s.walkinClientPhone     = walkinClientPhone;
        return s;
    }

    /**
     * Called when an operator logs hours for this cycle and triggers invoice generation.
     * Enforces minimum hours — if actualHours < minimumHoursPerCycle, bills the minimum.
     * Returns the hours that will actually be billed.
     */
    public BigDecimal resolveBillableHours(BigDecimal actualHours) {
        if (minimumHoursPerCycle != null
                && actualHours.compareTo(minimumHoursPerCycle) < 0) {
            return minimumHoursPerCycle;  // bill minimum regardless
        }
        return actualHours;
    }

    public void accumulateHours(BigDecimal billed) {
        this.totalHoursBilled = (this.totalHoursBilled == null ? BigDecimal.ZERO : this.totalHoursBilled)
                .add(billed);
    }

    public int remainingCycles() {
        if (contractStartDate == null || contractEndDate == null) return -1;
        long totalDays    = java.time.Duration.between(contractStartDate, contractEndDate).toDays();
        long elapsedDays  = java.time.Duration.between(contractStartDate, java.time.Instant.now()).toDays();
        int  cycleDays    = switch (frequency) {
            case DAILY   -> 1;
            case WEEKLY  -> 7;
            case MONTHLY -> 30;
            case CUSTOM  -> customIntervalDays != null ? customIntervalDays : 30;
        };
        long remaining = (totalDays - elapsedDays) / cycleDays;
        return (int) Math.max(0, remaining);
    }
}
