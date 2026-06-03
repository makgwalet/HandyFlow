package za.co.handyflow.platform.accountant.domain.model;

import jakarta.persistence.*;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.UUID;

@Entity(name = "AccountantFeeNoteLine")
@Table(name = "acc_fee_note_lines")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class FeeNoteLine {

    @Id
    private UUID id = UUID.randomUUID();

    @Column(name = "fee_note_id", nullable = false) private UUID       feeNoteId;
    @Column(name = "description", nullable = false) private String     description;
    @Column(name = "quantity",    nullable = false, precision = 8, scale = 2) private BigDecimal quantity;
    @Column(name = "unit_price",  nullable = false, precision = 10, scale = 2) private BigDecimal unitPrice;
    @Column(name = "vat_rate",    nullable = false, precision = 5, scale = 2) private BigDecimal vatRate;
    @Column(name = "amount",      nullable = false, precision = 15, scale = 2) private BigDecimal amount;
    @Column(name = "time_entry_id") private UUID timeEntryId;
    @Column(name = "line_order",  nullable = false) private int lineOrder;

    // ── Factories ─────────────────────────────────────────────────────────────

    /**
     * Line from a time entry — description, quantity = hours, unit_price = hourly rate.
     */
    public static FeeNoteLine forTimeEntry(UUID feeNoteId, TimeEntry entry,
                                           boolean includeVat, int order) {
        FeeNoteLine l = new FeeNoteLine();
        l.feeNoteId   = feeNoteId;
        l.timeEntryId = entry.getId();
        l.description = entry.getActivityType().replace("_", " ")
                + (entry.getDescription() != null ? " — " + entry.getDescription() : "");
        l.quantity    = entry.getHours();
        l.unitPrice   = entry.getHourlyRate();
        l.vatRate     = includeVat ? new BigDecimal("15.00") : BigDecimal.ZERO;
        l.amount      = entry.lineTotal();
        l.lineOrder   = order;
        return l;
    }

    /**
     * Fixed-fee line — description and amount supplied directly.
     */
    public static FeeNoteLine fixedFee(UUID feeNoteId, String description,
                                       BigDecimal amount, boolean includeVat, int order) {
        FeeNoteLine l = new FeeNoteLine();
        l.feeNoteId   = feeNoteId;
        l.description = description;
        l.quantity    = BigDecimal.ONE;
        l.unitPrice   = amount;
        l.vatRate     = includeVat ? new BigDecimal("15.00") : BigDecimal.ZERO;
        l.amount      = amount;
        l.lineOrder   = order;
        return l;
    }

    /** VAT portion of this line. */
    public BigDecimal vatAmount() {
        return amount.multiply(vatRate.divide(new BigDecimal("100"), 6, RoundingMode.HALF_UP))
                .setScale(2, RoundingMode.HALF_UP);
    }
}
