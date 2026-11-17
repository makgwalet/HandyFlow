package za.co.handyflow.platform.fuel.domain.model;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.NoArgsConstructor;
import za.co.handyflow.platform.shared.TenantId;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

/** An attendant's shift on the forecourt: opened with meter readings, closed with the takings counted. */
@Entity
@Table(name = "fuel_shifts")
@Getter
@NoArgsConstructor(access = lombok.AccessLevel.PROTECTED)
public class FuelShift {

    public static final BigDecimal VARIANCE_TOLERANCE = new BigDecimal("5.00");

    @Id
    private UUID id = UUID.randomUUID();

    @Embedded
    @AttributeOverride(name = "value", column = @Column(name = "tenant_id", nullable = false))
    private TenantId tenantId;

    @Column(name = "shift_number", nullable = false, updatable = false, length = 30) private String shiftNumber;
    @Column(name = "attendant_name", nullable = false, length = 120) private String attendantName;
    @Column(nullable = false, length = 10) private String status = "OPEN";
    @Column(name = "opened_at", nullable = false, updatable = false) private Instant openedAt;
    @Column(name = "opened_by") private UUID openedBy;
    @Column(name = "closed_at") private Instant closedAt;
    @Column(name = "closed_by") private UUID closedBy;
    @Column(name = "cash_amount", precision = 14, scale = 2) private BigDecimal cashAmount;
    @Column(name = "card_amount", precision = 14, scale = 2) private BigDecimal cardAmount;
    @Column(name = "account_amount", precision = 14, scale = 2) private BigDecimal accountAmount;
    @Column(name = "other_amount", precision = 14, scale = 2) private BigDecimal otherAmount;
    @Column(name = "fleet_card_amount", nullable = false, precision = 14, scale = 2) private BigDecimal fleetCardAmount = BigDecimal.ZERO;
    @Column(name = "adjustments_amount", nullable = false, precision = 14, scale = 2) private BigDecimal adjustmentsAmount = BigDecimal.ZERO;
    @Column(name = "tolerance_applied", precision = 10, scale = 2) private BigDecimal toleranceApplied;
    @Column(name = "review_status", nullable = false, length = 14) private String reviewStatus = "NONE";
    @Column(name = "reviewed_by") private UUID reviewedBy;
    @Column(name = "reviewed_at") private Instant reviewedAt;
    @Column(name = "review_note", length = 300) private String reviewNote;
    @Column(name = "expected_amount", precision = 14, scale = 2) private BigDecimal expectedAmount;
    @Column(name = "litres_sold", precision = 14, scale = 2) private BigDecimal litresSold;
    @Column(name = "variance_amount", precision = 14, scale = 2) private BigDecimal varianceAmount;
    @Column(name = "variance_reason", length = 500) private String varianceReason;
    @Column(length = 500) private String notes;
    @Column(name = "reversed_at") private Instant reversedAt;
    @Column(name = "reversed_by") private UUID reversedBy;
    @Column(name = "reverse_reason", length = 500) private String reverseReason;
    @Column(name = "provider_opened_at") private Instant providerOpenedAt;
    @Column(name = "provider_closed_at") private Instant providerClosedAt;
    @Version private Long version;

    public static FuelShift open(TenantId tenantId, String shiftNumber, String attendantName, UUID openedBy, String notes) {
        if (attendantName == null || attendantName.isBlank()) throw new IllegalArgumentException("Enter the attendant's name.");
        FuelShift s = new FuelShift();
        s.tenantId = tenantId;
        s.shiftNumber = shiftNumber;
        s.attendantName = attendantName.trim();
        s.openedBy = openedBy;
        s.openedAt = Instant.now();
        s.notes = notes == null || notes.isBlank() ? null : notes.trim();
        return s;
    }

    /** What the pump/POS provider said the shift's times were. Recorded beside the system times, which are not changed. */
    public void recordProviderTimes(Instant opened, Instant closed) {
        if (opened != null && closed != null && closed.isBefore(opened)) throw new IllegalArgumentException("The provider's closing time is before its opening time.");
        this.providerOpenedAt = opened; this.providerClosedAt = closed;
    }

    public boolean isOpen() { return "OPEN".equals(status); }
    public boolean isClosed() { return "CLOSED".equals(status); }

    private static BigDecimal amt(BigDecimal v, String what) {
        if (v == null) return BigDecimal.ZERO;
        if (v.signum() < 0) throw new IllegalArgumentException(what + " cannot be negative.");
        return v;
    }

    /** Closes with the original rules: R5.00 tolerance, no fleet-card tender, no adjustments. */
    public void close(BigDecimal cash, BigDecimal card, BigDecimal account, BigDecimal other, BigDecimal expected, BigDecimal litres,
                      String varianceReason, UUID closedBy) {
        close(cash, card, BigDecimal.ZERO, account, other, expected, BigDecimal.ZERO, litres, varianceReason, closedBy, VARIANCE_TOLERANCE, false);
    }

    /**
     * Closes the shift. Takings (cash + card + fleet card + account + other) are compared with what the meters say was sold,
     * less approved refunds, voids and discounts. The variance is recorded, never written off. Outside the tolerance a reason
     * is required and the shift is queued for a supervisor's review.
     */
    public void close(BigDecimal cash, BigDecimal card, BigDecimal fleetCard, BigDecimal account, BigDecimal other, BigDecimal expected,
                      BigDecimal approvedAdjustments, BigDecimal litres, String varianceReason, UUID closedBy,
                      BigDecimal tolerance, boolean requireSeparateCloser) {
        if (!isOpen()) throw new IllegalStateException("This shift is already " + status.toLowerCase() + ".");
        if (requireSeparateCloser && openedBy != null && openedBy.equals(closedBy)) {
            throw new IllegalStateException("The person who opened this shift cannot close it. Ask another authorised person to close it.");
        }
        BigDecimal tol = tolerance == null ? VARIANCE_TOLERANCE : tolerance;
        BigDecimal adj = approvedAdjustments == null ? BigDecimal.ZERO : approvedAdjustments;
        BigDecimal c = amt(cash, "Cash"), k = amt(card, "Card"), f = amt(fleetCard, "Fleet card"), a = amt(account, "Account"), o = amt(other, "Other");
        BigDecimal taken = c.add(k).add(f).add(a).add(o);
        BigDecimal variance = taken.subtract(expected.subtract(adj));
        String reason = varianceReason == null || varianceReason.isBlank() ? null : varianceReason.trim();
        boolean outside = variance.abs().compareTo(tol) > 0;
        if (outside && (reason == null || reason.length() < 5)) {
            throw new IllegalArgumentException("The takings are R " + variance.abs().toPlainString() + (variance.signum() < 0 ? " short" : " over")
                    + ". Give a reason (at least 5 characters) to close the shift.");
        }
        this.cashAmount = c; this.cardAmount = k; this.fleetCardAmount = f; this.accountAmount = a; this.otherAmount = o;
        this.expectedAmount = expected; this.adjustmentsAmount = adj; this.litresSold = litres; this.toleranceApplied = tol;
        this.varianceAmount = variance; this.varianceReason = reason;
        this.reviewStatus = outside ? "PENDING_REVIEW" : "NONE";
        this.closedAt = Instant.now(); this.closedBy = closedBy;
        this.status = "CLOSED";
    }

    /** A supervisor, who is not the person who closed the shift, signs off an out-of-tolerance variance. A record only: nothing is posted. */
    public void review(UUID by, String note) {
        if (!"PENDING_REVIEW".equals(reviewStatus)) throw new IllegalStateException("This shift is not waiting for review.");
        if (by == null || by.equals(closedBy)) throw new IllegalStateException("A supervisor other than the person who closed the shift must review it.");
        if (note == null || note.trim().length() < 5) throw new IllegalArgumentException("Give a review note of at least 5 characters.");
        this.reviewStatus = "REVIEWED"; this.reviewedBy = by; this.reviewedAt = Instant.now();
        this.reviewNote = note.trim().length() > 300 ? note.trim().substring(0, 300) : note.trim();
    }

    /** True when this shift's variance was outside the tolerance in force when it closed. */
    public boolean isOutsideTolerance() {
        return varianceAmount != null && varianceAmount.abs().compareTo(toleranceApplied == null ? VARIANCE_TOLERANCE : toleranceApplied) > 0;
    }

    public void reverse(String reason, UUID by) {
        if (!isClosed()) throw new IllegalStateException("Only a closed shift can be reversed.");
        if (reason == null || reason.trim().length() < 5) throw new IllegalArgumentException("Give a reason of at least 5 characters.");
        this.status = "REVERSED";
        this.reversedAt = Instant.now();
        this.reversedBy = by;
        this.reverseReason = reason.trim();
    }
}
