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
    @Column(name = "expected_amount", precision = 14, scale = 2) private BigDecimal expectedAmount;
    @Column(name = "litres_sold", precision = 14, scale = 2) private BigDecimal litresSold;
    @Column(name = "variance_amount", precision = 14, scale = 2) private BigDecimal varianceAmount;
    @Column(name = "variance_reason", length = 500) private String varianceReason;
    @Column(length = 500) private String notes;
    @Column(name = "reversed_at") private Instant reversedAt;
    @Column(name = "reversed_by") private UUID reversedBy;
    @Column(name = "reverse_reason", length = 500) private String reverseReason;
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

    public boolean isOpen() { return "OPEN".equals(status); }
    public boolean isClosed() { return "CLOSED".equals(status); }

    private static BigDecimal amt(BigDecimal v, String what) {
        if (v == null) return BigDecimal.ZERO;
        if (v.signum() < 0) throw new IllegalArgumentException(what + " cannot be negative.");
        return v;
    }

    /**
     * Closes the shift. The variance is takings minus what the meters say was sold; it is recorded, never written off.
     * Outside the tolerance a reason is required.
     */
    public void close(BigDecimal cash, BigDecimal card, BigDecimal account, BigDecimal other, BigDecimal expected, BigDecimal litres,
                      String varianceReason, UUID closedBy) {
        if (!isOpen()) throw new IllegalStateException("This shift is already " + status.toLowerCase() + ".");
        BigDecimal c = amt(cash, "Cash"), k = amt(card, "Card"), a = amt(account, "Account"), o = amt(other, "Other");
        BigDecimal taken = c.add(k).add(a).add(o);
        BigDecimal variance = taken.subtract(expected);
        String reason = varianceReason == null || varianceReason.isBlank() ? null : varianceReason.trim();
        if (variance.abs().compareTo(VARIANCE_TOLERANCE) > 0 && (reason == null || reason.length() < 5)) {
            throw new IllegalArgumentException("The takings are R " + variance.abs().toPlainString() + (variance.signum() < 0 ? " short" : " over")
                    + ". Give a reason (at least 5 characters) to close the shift.");
        }
        this.cashAmount = c; this.cardAmount = k; this.accountAmount = a; this.otherAmount = o;
        this.expectedAmount = expected; this.litresSold = litres;
        this.varianceAmount = variance; this.varianceReason = reason;
        this.closedAt = Instant.now(); this.closedBy = closedBy;
        this.status = "CLOSED";
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
