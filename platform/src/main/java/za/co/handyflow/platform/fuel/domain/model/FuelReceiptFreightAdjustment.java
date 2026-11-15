package za.co.handyflow.platform.fuel.domain.model;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.NoArgsConstructor;
import za.co.handyflow.platform.shared.TenantId;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

/** Freight added to a stock receipt after the fact. Append only: a row is never edited or removed. */
@Entity
@Table(name = "fuel_receipt_freight_adjustments")
@Getter
@NoArgsConstructor(access = lombok.AccessLevel.PROTECTED)
public class FuelReceiptFreightAdjustment {

    @Id
    private UUID id = UUID.randomUUID();

    @Embedded
    @AttributeOverride(name = "value", column = @Column(name = "tenant_id", nullable = false))
    private TenantId tenantId;

    @Column(name = "receipt_id", nullable = false, updatable = false)
    private UUID receiptId;

    @Column(name = "tank_id", nullable = false, updatable = false)
    private UUID tankId;

    @Column(nullable = false, updatable = false, precision = 14, scale = 2)
    private BigDecimal amount;

    @Column(nullable = false, updatable = false, length = 300)
    private String note;

    @Column(name = "litres_in_tank", nullable = false, updatable = false, precision = 12, scale = 2)
    private BigDecimal litresInTank;

    @Column(name = "absorbed_amount", nullable = false, updatable = false, precision = 14, scale = 2)
    private BigDecimal absorbedAmount;

    @Column(name = "unabsorbed_amount", nullable = false, updatable = false, precision = 14, scale = 2)
    private BigDecimal unabsorbedAmount;

    @Column(name = "cost_before", updatable = false, precision = 10, scale = 4)
    private BigDecimal costBefore;

    @Column(name = "cost_after", updatable = false, precision = 10, scale = 4)
    private BigDecimal costAfter;

    @Column(name = "created_by", updatable = false)
    private UUID createdBy;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt = Instant.now();

    public static FuelReceiptFreightAdjustment of(TenantId tenantId, UUID receiptId, UUID tankId, BigDecimal amount, String note, BigDecimal litresInTank,
                                                  BigDecimal absorbed, BigDecimal costBefore, BigDecimal costAfter, UUID by) {
        FuelReceiptFreightAdjustment a = new FuelReceiptFreightAdjustment();
        a.tenantId = tenantId; a.receiptId = receiptId; a.tankId = tankId; a.amount = amount; a.note = note;
        a.litresInTank = litresInTank; a.absorbedAmount = absorbed; a.unabsorbedAmount = amount.subtract(absorbed);
        a.costBefore = costBefore; a.costAfter = costAfter; a.createdBy = by;
        return a;
    }
}
