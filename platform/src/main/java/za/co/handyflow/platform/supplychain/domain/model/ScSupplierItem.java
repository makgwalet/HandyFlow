package za.co.handyflow.platform.supplychain.domain.model;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

/**
 * A catalogue item as sold by a specific supplier at a specific price.
 *
 * WHY this entity?
 * ────────────────
 * The catalogue_items table holds what you stock. This table holds
 * what each supplier charges for that item. One item may be supplied
 * by three different vendors at three different prices with different
 * lead times. When creating a PO line, the UI can show "best price"
 * suggestions by querying this table for the cheapest or fastest supplier.
 *
 * This is backed by the sc_supplier_items table created in V86.
 */
@Entity
@Table(name = "sc_supplier_items")
@Getter
@NoArgsConstructor
public class ScSupplierItem {

    @Id UUID id;
    @Column(name = "supplier_id",        nullable = false) UUID       supplierId;
    @Column(name = "tenant_id",          nullable = false) UUID       tenantId;
    @Column(name = "catalogue_item_id")                   UUID       catalogueItemId;
    @Column(name = "item_name",          nullable = false) String     itemName;
    @Column(name = "supplier_sku",       length = 100)    String     supplierSku;
    @Column(name = "unit_cost",          nullable = false, precision = 15, scale = 2) BigDecimal unitCost;
    @Column(length = 3) String currency = "ZAR";
    @Column(name = "lead_time_days")  int  leadTimeDays  = 7;
    @Column(name = "min_order_qty",   nullable = false, precision = 12, scale = 3) BigDecimal minOrderQty = BigDecimal.ONE;
    @Column(name = "is_preferred",    nullable = false) boolean  isPreferred = false;
    @Column(name = "last_ordered_at")                   Instant  lastOrderedAt;
    @Column(name = "last_ordered_price", precision = 15, scale = 2) BigDecimal lastOrderedPrice;
    @Column(name = "created_at") Instant createdAt;
    @Column(name = "updated_at") Instant updatedAt;

    // ── Factory ───────────────────────────────────────────────────────────────

    public static ScSupplierItem create(UUID tenantId, UUID supplierId, UUID catalogueItemId,
                                        String itemName, String supplierSku,
                                        BigDecimal unitCost, int leadTimeDays,
                                        BigDecimal minOrderQty, boolean isPreferred) {
        ScSupplierItem s = new ScSupplierItem();
        s.id               = UUID.randomUUID();
        s.tenantId         = tenantId;
        s.supplierId       = supplierId;
        s.catalogueItemId  = catalogueItemId;
        s.itemName         = itemName;
        s.supplierSku      = supplierSku;
        s.unitCost         = unitCost;
        s.leadTimeDays     = leadTimeDays > 0 ? leadTimeDays : 7;
        s.minOrderQty      = minOrderQty != null ? minOrderQty : BigDecimal.ONE;
        s.isPreferred      = isPreferred;
        s.createdAt        = Instant.now();
        s.updatedAt        = Instant.now();
        return s;
    }

    public void updatePrice(BigDecimal newUnitCost) {
        lastOrderedPrice = unitCost;
        lastOrderedAt    = Instant.now();
        unitCost         = newUnitCost;
        updatedAt        = Instant.now();
    }

    public void setPreferred(boolean preferred) {
        isPreferred = preferred;
        updatedAt   = Instant.now();
    }
}