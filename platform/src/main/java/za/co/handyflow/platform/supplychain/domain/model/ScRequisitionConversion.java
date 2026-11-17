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
}
