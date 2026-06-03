package za.co.handyflow.platform.pos.domain.model;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.NoArgsConstructor;
import za.co.handyflow.platform.shared.TenantId;

import java.time.Instant;
import java.util.UUID;

/**
 * Header record for a stock adjustment batch (e.g. a stocktake, damage write-off).
 * Line items are in PosStockAdjustmentItem.
 * Status: DRAFT (editable) → APPLIED (locked, movements created).
 */
@Entity
@Table(name = "pos_stock_adjustments")
@Getter
@NoArgsConstructor(access = lombok.AccessLevel.PROTECTED)
public class PosStockAdjustment {

    @Id private UUID id = UUID.randomUUID();

    @Embedded
    @AttributeOverride(name = "value", column = @Column(name = "tenant_id", nullable = false))
    private TenantId tenantId;

    @Column(name = "adjustment_number", nullable = false) private String  adjustmentNumber;

    /**
     * STOCK_COUNT | DAMAGE | THEFT | EXPIRY | CORRECTION | OTHER
     */
    @Column(nullable = false) private String reason;

    @Column private String notes;

    /** DRAFT | APPLIED */
    @Column(nullable = false) private String status = "DRAFT";

    @Column(name = "created_by")  private UUID    createdBy;
    @Column(name = "applied_by")  private UUID    appliedBy;
    @Column(name = "created_at", nullable = false) private Instant createdAt;
    @Column(name = "applied_at")                   private Instant appliedAt;

    // ── Factory ───────────────────────────────────────────────────────────────

    public static PosStockAdjustment create(TenantId tenantId, String adjustmentNumber,
                                            String reason, String notes, UUID createdBy) {
        PosStockAdjustment a = new PosStockAdjustment();
        a.tenantId           = tenantId;
        a.adjustmentNumber   = adjustmentNumber;
        a.reason             = reason;
        a.notes              = notes;
        a.createdBy          = createdBy;
        a.status             = "DRAFT";
        a.createdAt          = Instant.now();
        return a;
    }

    public void apply(UUID appliedBy) {
        if ("APPLIED".equals(this.status)) {
            throw new IllegalStateException("Adjustment " + adjustmentNumber + " is already applied");
        }
        this.status     = "APPLIED";
        this.appliedBy  = appliedBy;
        this.appliedAt  = Instant.now();
    }

    public boolean isDraft()   { return "DRAFT".equals(status); }
    public boolean isApplied() { return "APPLIED".equals(status); }
}
