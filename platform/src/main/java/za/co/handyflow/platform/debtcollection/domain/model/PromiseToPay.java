package za.co.handyflow.platform.debtcollection.domain.model;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Table;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;
import za.co.handyflow.platform.shared.AggregateRoot;
import za.co.handyflow.platform.shared.TenantId;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.UUID;

/**
 * A structured commitment by the debtor to pay an amount by a date — a first-class
 * object, not a contact-log note (the contact log's PROMISE_TO_PAY outcome records
 * that a promise was made; this tracks whether it was kept).
 * <p>
 * baselineOutstanding is the case's outstanding balance (per invoicing) when the promise
 * became ACTIVE; payment received is detected as baseline − current outstanding, plus any
 * manual receipts (see PtpService). amountReceived is the latest assessed figure.
 * <p>
 * A recurring promise is a series of one-off PTPs: when one is KEPT, the service spawns the
 * next (occurrencesRemaining − 1) anchored to the previous due date. A break ends the series.
 */
@Entity
@Table(name = "debtcollection_ptps")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class PromiseToPay extends AggregateRoot<PromiseToPay> {

    @Column(name = "case_id", nullable = false)
    private UUID caseId;

    @Column(name = "series_id", nullable = false)
    private UUID seriesId;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private PtpStatus status;

    @Column(nullable = false, precision = 15, scale = 2)
    private BigDecimal amount;

    @Column(name = "due_date", nullable = false)
    private LocalDate dueDate;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 15)
    private PtpFrequency frequency;

    /** Further occurrences after this one (0 for one-off or the last in a series). */
    @Column(name = "occurrences_remaining", nullable = false)
    private int occurrencesRemaining;

    /** Channel through which the promise was made. */
    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private ContactMethod source;

    @Column(columnDefinition = "TEXT")
    private String terms;

    @Column(name = "grace_days", nullable = false)
    private int graceDays;

    @Column(name = "baseline_outstanding", precision = 15, scale = 2)
    private BigDecimal baselineOutstanding;

    @Column(name = "amount_received", nullable = false, precision = 15, scale = 2)
    private BigDecimal amountReceived = BigDecimal.ZERO;

    @Column(name = "resolved_date")
    private LocalDate resolvedDate;

    @Column(name = "resolution_note", length = 500)
    private String resolutionNote;

    @Column(name = "created_by")
    private UUID createdBy;

    @Column(name = "created_by_name", length = 255)
    private String createdByName;

    public static PromiseToPay propose(TenantId tenantId, UUID caseId, UUID seriesId, BigDecimal amount,
                                       LocalDate dueDate, PtpFrequency frequency, int additionalOccurrences,
                                       ContactMethod source, String terms, int graceDays,
                                       UUID createdBy, String createdByName) {
        if (caseId == null) throw new IllegalArgumentException("caseId is required");
        if (amount == null || amount.signum() <= 0) throw new IllegalArgumentException("amount must be positive");
        if (dueDate == null) throw new IllegalArgumentException("dueDate is required");
        if (source == null) throw new IllegalArgumentException("source is required");
        PtpFrequency f = frequency != null ? frequency : PtpFrequency.ONE_OFF;
        if (f == PtpFrequency.ONE_OFF && additionalOccurrences > 0) {
            throw new IllegalArgumentException("additionalOccurrences requires a recurring frequency");
        }
        if (additionalOccurrences < 0 || graceDays < 0 || graceDays > 30) {
            throw new IllegalArgumentException("additionalOccurrences must be >= 0 and graceDays between 0 and 30");
        }
        PromiseToPay p = new PromiseToPay();
        p.initTenantId(tenantId);
        p.caseId = caseId;
        p.seriesId = seriesId != null ? seriesId : UUID.randomUUID();
        p.status = PtpStatus.PROPOSED;
        p.amount = amount;
        p.dueDate = dueDate;
        p.frequency = f;
        p.occurrencesRemaining = additionalOccurrences;
        p.source = source;
        p.terms = terms;
        p.graceDays = graceDays;
        p.createdBy = createdBy;
        p.createdByName = createdByName;
        return p;
    }

    public LocalDate graceEnd() {
        return dueDate.plusDays(graceDays);
    }

    public void accept(BigDecimal baselineOutstanding) {
        if (status != PtpStatus.PROPOSED) {
            throw new IllegalStateException("Only a PROPOSED promise can be accepted (status is " + status + ")");
        }
        if (baselineOutstanding == null || baselineOutstanding.signum() < 0) {
            throw new IllegalArgumentException("baselineOutstanding is required");
        }
        this.status = PtpStatus.ACTIVE;
        this.baselineOutstanding = baselineOutstanding;
    }

    public void recordReceived(BigDecimal received) {
        this.amountReceived = received != null ? received : BigDecimal.ZERO;
    }

    /** ACTIVE → terminal outcome; BROKEN may be re-classified upward when late/corrected payment data arrives. */
    public void resolve(PtpStatus outcome, LocalDate today, String note) {
        boolean fromActive = status == PtpStatus.ACTIVE
                && (outcome == PtpStatus.KEPT || outcome == PtpStatus.PARTIALLY_KEPT || outcome == PtpStatus.BROKEN);
        boolean recovery = status == PtpStatus.BROKEN
                && (outcome == PtpStatus.KEPT || outcome == PtpStatus.PARTIALLY_KEPT);
        if (!fromActive && !recovery) {
            throw new IllegalStateException("Cannot move a " + status + " promise to " + outcome);
        }
        this.status = outcome;
        this.resolvedDate = today;
        this.resolutionNote = note;
    }

    public void cancel(String reason) {
        if (!status.isOpen()) {
            throw new IllegalStateException("Cannot cancel a " + status + " promise");
        }
        this.status = PtpStatus.CANCELLED;
        this.resolvedDate = LocalDate.now();
        this.resolutionNote = reason;
    }
}
