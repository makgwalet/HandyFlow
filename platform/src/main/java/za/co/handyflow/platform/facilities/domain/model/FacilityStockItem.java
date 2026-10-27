package za.co.handyflow.platform.facilities.domain.model;

import jakarta.persistence.*;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;
import za.co.handyflow.platform.shared.TenantId;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.Instant;
import java.util.UUID;

/**
 * A stocked consumable or spare part. Quantity on hand never goes negative; every change is also written to the
 * movements ledger by the service. The unit cost is a weighted average, updated only when stock is received.
 */
@Entity
@Table(name = "facility_stock_items")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class FacilityStockItem {

    @Id
    private UUID id = UUID.randomUUID();

    @Embedded
    @AttributeOverride(name = "value", column = @Column(name = "tenant_id", nullable = false))
    private TenantId tenantId;

    @Column(nullable = false, length = 60)
    private String sku;

    @Column(nullable = false, length = 200)
    private String name;

    @Column(nullable = false, length = 20)
    private String unit;

    @Column(length = 100)
    private String location;

    @Column(name = "on_hand", nullable = false, precision = 14, scale = 3)
    private BigDecimal onHand = BigDecimal.ZERO;

    @Column(name = "reorder_level", nullable = false, precision = 14, scale = 3)
    private BigDecimal reorderLevel = BigDecimal.ZERO;

    @Column(name = "reorder_qty", nullable = false, precision = 14, scale = 3)
    private BigDecimal reorderQty = BigDecimal.ZERO;

    @Column(name = "unit_cost", nullable = false, precision = 15, scale = 2)
    private BigDecimal unitCost = BigDecimal.ZERO;

    @Column(nullable = false)
    private boolean active = true;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;

    public static FacilityStockItem create(TenantId tenantId, String sku, String name, String unit, String location,
                                           BigDecimal reorderLevel, BigDecimal reorderQty) {
        FacilityStockItem i = new FacilityStockItem();
        i.tenantId = tenantId;
        i.sku = normaliseSku(sku);
        i.apply(name, unit, location, reorderLevel, reorderQty);
        i.createdAt = Instant.now();
        return i;
    }

    public static String normaliseSku(String sku) {
        if (sku == null || sku.isBlank()) throw new IllegalArgumentException("sku is required");
        String s = sku.trim().toUpperCase();
        if (s.length() > 60) throw new IllegalArgumentException("sku must be 60 characters or fewer");
        return s;
    }

    public void update(String name, String unit, String location, BigDecimal reorderLevel, BigDecimal reorderQty) {
        apply(name, unit, location, reorderLevel, reorderQty);
    }

    private void apply(String name, String unit, String location, BigDecimal reorderLevel, BigDecimal reorderQty) {
        if (name == null || name.isBlank()) throw new IllegalArgumentException("name is required");
        if (unit == null || unit.isBlank()) throw new IllegalArgumentException("unit is required");
        BigDecimal level = reorderLevel == null ? BigDecimal.ZERO : reorderLevel;
        BigDecimal qty = reorderQty == null ? BigDecimal.ZERO : reorderQty;
        if (level.signum() < 0 || qty.signum() < 0)
            throw new IllegalArgumentException("reorder level and quantity must not be negative");
        this.name = name.trim();
        this.unit = unit.trim();
        this.location = location == null || location.isBlank() ? null : location.trim();
        this.reorderLevel = level;
        this.reorderQty = qty;
        this.updatedAt = Instant.now();
    }

    /** Add stock bought or counted in at {@code cost}; the average cost moves to the weighted average. */
    public void receive(BigDecimal quantity, BigDecimal cost) {
        requirePositive(quantity);
        if (cost == null || cost.signum() < 0) throw new IllegalArgumentException("unit cost must not be negative");
        BigDecimal newOnHand = onHand.add(quantity);
        BigDecimal value = onHand.multiply(unitCost).add(quantity.multiply(cost));
        this.unitCost = value.divide(newOnHand, 2, RoundingMode.HALF_UP);
        this.onHand = newOnHand;
        this.updatedAt = Instant.now();
    }

    /** Take stock out. Fails rather than going negative. */
    public void issue(BigDecimal quantity) {
        requirePositive(quantity);
        if (quantity.compareTo(onHand) > 0)
            throw new IllegalArgumentException("Only " + onHand.stripTrailingZeros().toPlainString() + " " + unit + " of " + sku + " on hand");
        this.onHand = onHand.subtract(quantity);
        this.updatedAt = Instant.now();
    }

    /** Put unused stock back, at the current average cost (which therefore does not change). */
    public void putBack(BigDecimal quantity) {
        requirePositive(quantity);
        this.onHand = onHand.add(quantity);
        this.updatedAt = Instant.now();
    }

    /** Stocktake: set on-hand to the counted figure and return the difference (counted - previous). */
    public BigDecimal count(BigDecimal counted) {
        if (counted == null || counted.signum() < 0) throw new IllegalArgumentException("counted quantity must not be negative");
        BigDecimal diff = counted.subtract(onHand);
        this.onHand = counted;
        this.updatedAt = Instant.now();
        return diff;
    }

    public void deactivate() { this.active = false; this.updatedAt = Instant.now(); }
    public void reactivate() { this.active = true; this.updatedAt = Instant.now(); }

    /** At or below a (non-zero) reorder level. */
    public boolean isBelowReorderLevel() {
        return reorderLevel.signum() > 0 && onHand.compareTo(reorderLevel) <= 0;
    }

    public BigDecimal stockValue() {
        return onHand.multiply(unitCost).setScale(2, RoundingMode.HALF_UP);
    }

    private static void requirePositive(BigDecimal q) {
        if (q == null || q.signum() <= 0) throw new IllegalArgumentException("quantity must be positive");
    }
}
