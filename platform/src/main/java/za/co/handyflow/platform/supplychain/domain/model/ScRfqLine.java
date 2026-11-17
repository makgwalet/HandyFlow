package za.co.handyflow.platform.supplychain.domain.model;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;
import java.util.UUID;

@Entity
@Table(name = "sc_rfq_lines")
@Getter
@NoArgsConstructor
public class ScRfqLine {

    @Id UUID id;
    @Column(name = "tenant_id", nullable = false) UUID tenantId;
    @Column(name = "rfq_id", nullable = false) UUID rfqId;
    @Column(name = "line_number", nullable = false) int lineNumber;
    @Column(name = "requisition_line_id") UUID requisitionLineId;
    @Column(name = "catalogue_item_id") UUID catalogueItemId;
    @Column(name = "item_name", nullable = false, length = 255) String itemName;
    @Column(length = 500) String description;
    @Column(nullable = false, precision = 12, scale = 3) BigDecimal quantity;
    @Column(name = "estimated_unit_cost", precision = 15, scale = 2) BigDecimal estimatedUnitCost;

    public static ScRfqLine create(UUID tenantId, UUID rfqId, int lineNumber, UUID requisitionLineId, UUID catalogueItemId,
                                   String itemName, String description, BigDecimal quantity, BigDecimal estimatedUnitCost) {
        ScRfqLine l = new ScRfqLine();
        l.id = UUID.randomUUID();
        l.tenantId = tenantId;
        l.rfqId = rfqId;
        l.lineNumber = lineNumber;
        l.requisitionLineId = requisitionLineId;
        l.catalogueItemId = catalogueItemId;
        l.itemName = itemName;
        l.description = description;
        l.quantity = quantity;
        l.estimatedUnitCost = estimatedUnitCost;
        return l;
    }
}