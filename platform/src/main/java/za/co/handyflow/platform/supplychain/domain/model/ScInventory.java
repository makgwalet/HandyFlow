package za.co.handyflow.platform.supplychain.domain.model;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.extern.slf4j.Slf4j;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.Instant;
import java.util.UUID;

@Slf4j
@Entity
@Table(name = "sc_inventory")
@Getter
@NoArgsConstructor
public class ScInventory {

    @Id UUID id;
    @Column(name = "tenant_id",         nullable = false) UUID       tenantId;
    @Column(name = "location_id",       nullable = false) UUID       locationId;
    @Column(name = "catalogue_item_id", nullable = false) UUID       catalogueItemId;

    @Column(name = "qty_on_hand",    nullable = false, precision = 12, scale = 3) BigDecimal qtyOnHand    = BigDecimal.ZERO;
    @Column(name = "qty_reserved",   nullable = false, precision = 12, scale = 3) BigDecimal qtyReserved  = BigDecimal.ZERO;
    @Column(name = "qty_in_transit", nullable = false, precision = 12, scale = 3) BigDecimal qtyInTransit = BigDecimal.ZERO;
    @Column(name = "reorder_point",  nullable = false, precision = 12, scale = 3) BigDecimal reorderPoint = BigDecimal.ZERO;
    @Column(name = "reorder_qty",    nullable = false, precision = 12, scale = 3) BigDecimal reorderQty   = BigDecimal.ZERO;
    @Column(name = "max_stock_level",               precision = 12, scale = 3)   BigDecimal maxStockLevel;
    @Column(name = "avg_cost",       nullable = false, precision = 15, scale = 2) BigDecimal avgCost      = BigDecimal.ZERO;
    @Column(name = "last_cost",      nullable = false, precision = 15, scale = 2) BigDecimal lastCost     = BigDecimal.ZERO;
    @Column(name = "bin_location",   length = 50) String binLocation;
    @Column(name = "expiry_tracking", nullable = false) boolean expiryTracking = false;
    @Column(name = "lot_tracking",    nullable = false) boolean lotTracking     = false;
    @Column(name = "created_at") Instant createdAt;
    @Column(name = "updated_at") Instant updatedAt;

    // ── Factory ───────────────────────────────────────────────────────────────

    public static ScInventory create(UUID tenantId, UUID locationId, UUID catalogueItemId) {
        ScInventory i = new ScInventory();
        i.id              = UUID.randomUUID();
        i.tenantId        = tenantId;
        i.locationId      = locationId;
        i.catalogueItemId = catalogueItemId;
        i.createdAt       = Instant.now();
        i.updatedAt       = Instant.now();
        return i;
    }

    // ── Domain methods ────────────────────────────────────────────────────────

    /**
     * Adjusts on-hand quantity and recalculates weighted average cost.
     *
     * WHY weighted average cost?
     * ─────────────────────────
     * Each receipt may have a different unit cost (price fluctuates).
     * Weighted average blends all purchases: total value ÷ total qty.
     * This is the most common inventory costing method (FIFO and LIFO
     * are more complex and rarely needed for construction/trades businesses).
     *
     * WHY warn on negative stock instead of throwing?
     * ─────────────────────────────────────────────
     * Throwing would roll back the transaction — the GR post or sale
     * would fail completely. A warning lets the operator know there's
     * a discrepancy (perhaps a missed opening stock entry) without
     * losing the transaction. Strict enforcement can be enabled via
     * a tenant configuration flag once opening stocks are stabilised.
     *
     * @param delta    positive = receipt/adjustment up; negative = issue/sale
     * @param unitCost the cost of the incoming units (only used when delta > 0)
     */
    public void adjustQty(BigDecimal delta, BigDecimal unitCost) {
        BigDecimal newQty = qtyOnHand.add(delta);

        if (newQty.compareTo(BigDecimal.ZERO) < 0) {
            log.warn("[SCM] Inventory {} going negative: current={} delta={} result={}",
                    id, qtyOnHand, delta, newQty);
            // Continue — operator will see the negative balance and investigate
        }

        // Recalculate weighted average cost only when receiving stock
        if (delta.compareTo(BigDecimal.ZERO) > 0
                && unitCost != null
                && unitCost.compareTo(BigDecimal.ZERO) > 0) {
            if (qtyOnHand.compareTo(BigDecimal.ZERO) > 0) {
                BigDecimal totalValue = qtyOnHand.multiply(avgCost).add(delta.multiply(unitCost));
                avgCost = totalValue.divide(newQty, 2, RoundingMode.HALF_UP);
            } else {
                avgCost = unitCost;
            }
            lastCost = unitCost;
        }

        qtyOnHand  = newQty;
        updatedAt  = Instant.now();
    }

    public void setReorderLevels(BigDecimal reorderPoint, BigDecimal reorderQty) {
        if (reorderPoint != null) this.reorderPoint = reorderPoint;
        if (reorderQty   != null) this.reorderQty   = reorderQty;
        updatedAt = Instant.now();
    }

    public void setBinLocation(String binLocation) {
        this.binLocation = binLocation;
        updatedAt = Instant.now();
    }

    /** Available to promise = on-hand minus what's already reserved. */
    public BigDecimal getQtyAvailable() {
        return qtyOnHand.subtract(qtyReserved);
    }

    public boolean isLowStock() {
        return reorderPoint.compareTo(BigDecimal.ZERO) > 0
                && qtyOnHand.compareTo(reorderPoint) <= 0;
    }
}