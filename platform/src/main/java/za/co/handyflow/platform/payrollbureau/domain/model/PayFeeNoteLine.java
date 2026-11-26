package za.co.handyflow.platform.payrollbureau.domain.model;

import jakarta.persistence.*;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;
import java.util.UUID;

@Entity
@Table(name = "pay_fee_note_lines")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class PayFeeNoteLine {

    @Id
    private UUID id = UUID.randomUUID();

    @Column(name = "fee_note_id", nullable = false)
    private UUID feeNoteId;

    @Column(name = "description", nullable = false)
    private String description;

    @Column(name = "quantity", nullable = false, precision = 8, scale = 2)
    private BigDecimal quantity;

    @Column(name = "unit_price", nullable = false, precision = 10, scale = 2)
    private BigDecimal unitPrice;

    @Column(name = "amount", nullable = false, precision = 15, scale = 2)
    private BigDecimal amount;

    @Column(name = "charge_id")
    private UUID chargeId;

    /** The minimum fee for a run, charged when the per-employee total is below it. */
    public static PayFeeNoteLine minimumFee(UUID feeNoteId, String payRunNumber, int employeeCount, BigDecimal minimum) {
        PayFeeNoteLine l = new PayFeeNoteLine();
        l.feeNoteId = feeNoteId;
        l.description = "Payroll processing, minimum fee — " + payRunNumber + " (" + employeeCount + " employees)";
        l.quantity = BigDecimal.ONE; l.unitPrice = minimum; l.amount = minimum;
        return l;
    }

    /** A fixed charge from the client's price list. */
    public static PayFeeNoteLine forCharge(UUID feeNoteId, PayClientCharge charge) {
        PayFeeNoteLine l = new PayFeeNoteLine();
        l.feeNoteId = feeNoteId; l.description = charge.getDescription();
        l.quantity = BigDecimal.ONE; l.unitPrice = charge.getAmount(); l.amount = charge.getAmount(); l.chargeId = charge.getId();
        return l;
    }

    /** Per-employee payroll processing line — the bureau's actual billing model. */
    public static PayFeeNoteLine forPayRun(UUID feeNoteId, String payRunNumber,
                                           int employeeCount, BigDecimal perEmployeeFee) {
        PayFeeNoteLine l = new PayFeeNoteLine();
        l.feeNoteId = feeNoteId;
        l.description = "Payroll processing — " + payRunNumber + " (" + employeeCount + " employees)";
        l.quantity = BigDecimal.valueOf(employeeCount);
        l.unitPrice = perEmployeeFee;
        l.amount = perEmployeeFee.multiply(BigDecimal.valueOf(employeeCount)).setScale(2, java.math.RoundingMode.HALF_UP);
        return l;
    }
}