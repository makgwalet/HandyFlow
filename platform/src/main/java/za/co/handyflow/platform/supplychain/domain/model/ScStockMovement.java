package za.co.handyflow.platform.supplychain.domain.model;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.util.UUID;

@Entity
@Table(name = "sc_stock_movements")
@Getter
@NoArgsConstructor
public class ScStockMovement {

    @Id UUID id;
    @Column(name = "tenant_id",       nullable = false) UUID tenantId;
    @Column(name = "inventory_id",    nullable = false) UUID inventoryId;
    @Column(name = "movement_type",   nullable = false, length = 30) String movementType;
    @Column(name = "qty_change",      nullable = false, precision = 12, scale = 3) BigDecimal qtyChange;
    @Column(name = "qty_before",      nullable = false, precision = 12, scale = 3) BigDecimal qtyBefore;
    @Column(name = "qty_after",       nullable = false, precision = 12, scale = 3) BigDecimal qtyAfter;
    @Column(name = "unit_cost",       precision = 15, scale = 2) BigDecimal unitCost;
    @Column(name = "reference_type",  length = 30) String referenceType;
    @Column(name = "reference_id")    UUID referenceId;
    @Column(name = "reference_number", length = 50) String referenceNumber;
    @Column(name = "lot_number",      length = 50) String lotNumber;
    @Column(name = "expiry_date")     LocalDate expiryDate;
    @Column(name = "serial_numbers")  String serialNumbers;
    String notes;
    @Column(name = "created_by")      UUID createdBy;
    @Column(name = "created_by_name") String createdByName;
    @Column(name = "created_at",      nullable = false) Instant createdAt;

    public static ScStockMovement record(UUID tenantId, ScInventory inv,
                                         String type, BigDecimal delta, BigDecimal unitCost,
                                         String refType, UUID refId, String refNumber,
                                         UUID createdBy, String createdByName) {
        ScStockMovement m = new ScStockMovement();
        m.id = UUID.randomUUID();
        m.tenantId = tenantId;
        m.inventoryId = inv.getId();
        m.movementType = type;
        m.qtyChange = delta;
        m.qtyBefore = inv.getQtyOnHand();
        m.qtyAfter  = inv.getQtyOnHand().add(delta);
        m.unitCost  = unitCost;
        m.referenceType   = refType;
        m.referenceId     = refId;
        m.referenceNumber = refNumber;
        m.createdBy     = createdBy;
        m.createdByName = createdByName;
        m.createdAt = Instant.now();
        return m;
    }
}
