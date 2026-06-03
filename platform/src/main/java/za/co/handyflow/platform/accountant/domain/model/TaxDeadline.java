package za.co.handyflow.platform.accountant.domain.model;

import jakarta.persistence.*;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.util.UUID;

@Entity(name = "AccountantTaxDeadline")
@Table(name = "acc_tax_deadlines",
        uniqueConstraints = @UniqueConstraint(
                columnNames = {"client_id", "deadline_type", "period_year", "period_month"}))
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class TaxDeadline {

    @Id
    private UUID id = UUID.randomUUID();

    @Column(name = "tenant_id", nullable = false)
    private UUID tenantId;

    @Column(name = "client_id", nullable = false)
    private UUID clientId;

    @Column(name = "deadline_type", nullable = false)
    private String deadlineType;

    @Column(name = "period_year", nullable = false)
    private int periodYear;

    @Column(name = "period_month")
    private Integer periodMonth;        // null for annual deadlines (ITR14, EMP501)

    @Column(name = "statutory_due_date", nullable = false)
    private LocalDate statutoryDueDate; // raw calendar date per SARS rules

    @Column(name = "adjusted_due_date", nullable = false)
    private LocalDate adjustedDueDate;  // after business-day adjustment

    @Column(name = "status", nullable = false)
    private String status = "PENDING";

    @Column(name = "filed_date")
    private LocalDate filedDate;

    @Column(name = "sars_reference")
    private String sarsReference;

    @Column(name = "filing_amount", precision = 15, scale = 2)
    private BigDecimal filingAmount;

    @Column(name = "penalty_amount", precision = 15, scale = 2)
    private BigDecimal penaltyAmount;

    @Column(name = "notes", columnDefinition = "TEXT")
    private String notes;

    @Column(name = "reminder_30_sent", nullable = false)
    private boolean reminder30Sent = false;

    @Column(name = "reminder_7_sent", nullable = false)
    private boolean reminder7Sent = false;

    @Column(name = "reminder_1_sent", nullable = false)
    private boolean reminder1Sent = false;

    @Column(name = "overdue_flagged_at")
    private Instant overdueFlaggedAt;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;

    // ── Factory ───────────────────────────────────────────────────────────────

    public static TaxDeadline create(UUID tenantId, UUID clientId, String deadlineType,
                                     int periodYear, Integer periodMonth,
                                     LocalDate statutoryDue, LocalDate adjustedDue) {
        TaxDeadline d = new TaxDeadline();
        d.tenantId         = tenantId;
        d.clientId         = clientId;
        d.deadlineType     = deadlineType;
        d.periodYear       = periodYear;
        d.periodMonth      = periodMonth;
        d.statutoryDueDate = statutoryDue;
        d.adjustedDueDate  = adjustedDue;
        d.createdAt        = Instant.now();
        d.updatedAt        = Instant.now();
        return d;
    }

    // ── Mutations ─────────────────────────────────────────────────────────────

    public void markFiled(LocalDate filedDate, String sarsReference, BigDecimal amount) {
        this.status        = "FILED";
        this.filedDate     = filedDate;
        this.sarsReference = sarsReference;
        this.filingAmount  = amount;
        this.updatedAt     = Instant.now();
    }

    public void markOverdue() {
        if (!"FILED".equals(this.status)) {
            this.status             = "OVERDUE";
            this.overdueFlaggedAt   = Instant.now();
            this.updatedAt          = Instant.now();
        }
    }

    public void markReminder30Sent() { this.reminder30Sent = true; this.updatedAt = Instant.now(); }
    public void markReminder7Sent()  { this.reminder7Sent  = true; this.updatedAt = Instant.now(); }
    public void markReminder1Sent()  { this.reminder1Sent  = true; this.updatedAt = Instant.now(); }

    public void addPenalty(BigDecimal amount) {
        this.penaltyAmount = amount;
        this.updatedAt     = Instant.now();
    }

    @PreUpdate
    void onUpdate() { this.updatedAt = Instant.now(); }
}
