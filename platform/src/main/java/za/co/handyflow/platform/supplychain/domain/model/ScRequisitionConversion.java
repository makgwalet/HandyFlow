package za.co.handyflow.platform.supplychain.domain.model;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "sc_requisition_conversions")
@Getter
@NoArgsConstructor
public class ScRequisitionConversion {

    @Id UUID id;
    @Column(name = "tenant_id", nullable = false) UUID tenantId;
    @Column(name = "requisition_id", nullable = false) UUID requisitionId;
    @Column(name = "requisition_line_id", nullable = false) UUID requisitionLineId;
    @Column(name = "purchase_order_id", nullable = false) UUID purchaseOrderId;
    @Column(nullable = false, precision = 12, scale = 3) BigDecimal qty;
    @Column(name = "unit_cost", nullable = false, precision = 15, scale = 2) BigDecimal unitCost;
    @Column(name = "converted_by") UUID convertedBy;
    @Column(name = "converted_by_name", length = 255) String convertedByName;
    @Column(name = "created_at", nullable = false) Instant createdAt;
    @Column(name = "requisition_unit_cost", precision = 15, scale = 2) BigDecimal requisitionUnitCost;
    @Column(name = "price_status", nullable = false, length = 20) String priceStatus = "NONE";
    @Column(name = "price_change_reason", length = 40) String priceChangeReason;
    @Column(name = "price_change_note", length = 500) String priceChangeNote;
    @Column(name = "quotation_ref", length = 100) String quotationRef;
    @Column(name = "saving_amount", nullable = false, precision = 15, scale = 2) BigDecimal savingAmount = BigDecimal.ZERO;
    @Column(name = "price_decided_by") UUID priceDecidedBy;
    @Column(name = "price_decided_by_name", length = 255) String priceDecidedByName;
    @Column(name = "price_decided_at") Instant priceDecidedAt;
    @Column(name = "price_decision_note", length = 500) String priceDecisionNote;

    public static ScRequisitionConversion create(UUID tenantId, UUID requisitionId, UUID lineId, UUID poId,
                                                 BigDecimal qty, BigDecimal unitCost, UUID by, String byName) {
        ScRequisitionConversion c = new ScRequisitionConversion();
        c.id = UUID.randomUUID();
        c.tenantId = tenantId;
        c.requisitionId = requisitionId;
        c.requisitionLineId = lineId;
        c.purchaseOrderId = poId;
        c.qty = qty;
        c.unitCost = unitCost;
        c.convertedBy = by;
        c.convertedByName = byName;
        c.createdAt = Instant.now();
        return c;
    }

    /** Records the estimate the buyer started from and, when the price moved, why. */
    public void recordPrice(BigDecimal requisitionUnitCost, String status, String reason, String note, String quotationRef,
                            BigDecimal saving) {
        this.requisitionUnitCost = requisitionUnitCost;
        this.priceStatus = status;
        this.priceChangeReason = reason;
        this.priceChangeNote = note;
        this.quotationRef = quotationRef;
        this.savingAmount = saving != null ? saving : BigDecimal.ZERO;
    }

    public void decidePrice(boolean approve, UUID by, String byName, String note) {
        if (!"PENDING_APPROVAL".equals(priceStatus))
            throw new IllegalStateException("This price change is not waiting for approval (" + priceStatus + ")");
        this.priceStatus = approve ? "APPROVED" : "REJECTED";
        this.priceDecidedBy = by;
        this.priceDecidedByName = byName;
        this.priceDecidedAt = Instant.now();
        this.priceDecisionNote = note;
    }
}
