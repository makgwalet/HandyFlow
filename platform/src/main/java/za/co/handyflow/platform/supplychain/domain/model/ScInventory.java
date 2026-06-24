package za.co.handyflow.platform.supplychain.domain.model;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "sc_inventory")
@Getter
@NoArgsConstructor
public class ScInventory {

    @Id UUID id;
    @Column(name = "tenant_id",          nullable = false) UUID tenantId;
    @Column(name = "location_id",        nullable = false) UUID locationId;
    @Column(name = "catalogue_item_id",  nullable = false) UUID catalogueItemId;
    @Column(name = "qty_on_hand",    nullable = false, precision = 12, scale = 3) BigDecimal qtyOnHand    = BigDecimal.ZERO;
    @Column(name = "qty_reserved",   nullable = false, precision = 12, scale = 3) BigDecimal qtyReserved  = BigDecimal.ZERO;
    @Column(name = "qty_in_transit", nullable = false, precision = 12, scale = 3) BigDecimal qtyInTransit = BigDecimal.ZERO;
    @Column(name = "reorder_point",  nullable = false, precision = 12, scale = 3) BigDecimal reorderPoint = BigDecimal.ZERO;
    @Column(name = "reorder_qty",    nullable = false, precision = 12, scale = 3) BigDecimal reorderQty   = BigDecimal.ZERO;
    @Column(name = "max_stock_level",                  precision = 12, scale = 3) BigDecimal maxStockLevel;
    @Column(name = "avg_cost",       nullable = false, precision = 15, scale = 2) BigDecimal avgCost      = BigDecimal.ZERO;
    @Column(name = "last_cost",      nullable = false, precision = 15, scale = 2) BigDecimal lastCost     = BigDecimal.ZERO;
    @Column(name = "bin_location",   length = 50) String binLocation;
    @Column(name = "expiry_tracking", nullable = false) boolean expiryTracking = false;
    @Column(name = "lot_tracking",    nullable = false) boolean lotTracking     = false;
    @Column(name = "created_at") Instant createdAt;
    @Column(name = "updated_at") Instant updatedAt;

    public static ScInventory create(UUID tenantId, UUID locationId, UUID catalogueItemId) {
        ScInventory i = new ScInventory();
        i.id = UUID.randomUUID();
        i.tenantId = tenantId;
        i.locationId = locationId;
        i.catalogueItemId = catalogueItemId;
        i.createdAt = Instant.now();
        i.updatedAt = Instant.now();
        return i;
    }

    public void adjustQty(BigDecimal delta, BigDecimal unitCost) {
        if (delta.compareTo(BigDecimal.ZERO) > 0 && unitCost != null
                && unitCost.compareTo(BigDecimal.ZERO) > 0) {
            BigDecimal newQty = this.qtyOnHand.add(delta);
            if (newQty.compareTo(BigDecimal.ZERO) > 0) {
                BigDecimal totalValue = this.qtyOnHand.multiply(this.avgCost)
                        .add(delta.multiply(unitCost));
                this.avgCost = totalValue.divide(newQty, 2, RoundingMode.HALF_UP);
            }
            this.lastCost = unitCost;
        }
        this.qtyOnHand = this.qtyOnHand.add(delta);
        this.updatedAt = Instant.now();
    }

    public void setReorderLevels(BigDecimal reorderPoint, BigDecimal reorderQty) {
        if (reorderPoint != null) this.reorderPoint = reorderPoint;
        if (reorderQty   != null) this.reorderQty   = reorderQty;
        this.updatedAt = Instant.now();
    }

    public void setBinLocation(String binLocation) { this.binLocation = binLocation; }

    public boolean isLowStock() {
        return reorderPoint.compareTo(BigDecimal.ZERO) > 0
                && qtyOnHand.compareTo(reorderPoint) <= 0;
    }
}
