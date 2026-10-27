package za.co.handyflow.platform.facilities.domain.model;

import jakarta.persistence.*;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;
import za.co.handyflow.platform.shared.TenantId;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

/** How much of one item a store holds. Never negative. Changed only under the item's row lock. */
@Entity
@Table(name = "facility_stock_levels")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class FacilityStockLevel {

    @Id
    private UUID id = UUID.randomUUID();

    @Embedded
    @AttributeOverride(name = "value", column = @Column(name = "tenant_id", nullable = false))
    private TenantId tenantId;

    @Column(name = "store_id", nullable = false)
    private UUID storeId;

    @Column(name = "item_id", nullable = false)
    private UUID itemId;

    @Column(name = "on_hand", nullable = false, precision = 14, scale = 3)
    private BigDecimal onHand = BigDecimal.ZERO;

    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;

    public static FacilityStockLevel create(TenantId tenantId, UUID storeId, UUID itemId) {
        FacilityStockLevel l = new FacilityStockLevel();
        l.tenantId = tenantId;
        l.storeId = storeId;
        l.itemId = itemId;
        l.updatedAt = Instant.now();
        return l;
    }

    public void add(BigDecimal quantity) {
        if (quantity == null || quantity.signum() <= 0) throw new IllegalArgumentException("quantity must be positive");
        this.onHand = onHand.add(quantity);
        this.updatedAt = Instant.now();
    }

    public void remove(BigDecimal quantity) {
        if (quantity == null || quantity.signum() <= 0) throw new IllegalArgumentException("quantity must be positive");
        if (quantity.compareTo(onHand) > 0) throw new IllegalArgumentException("The store holds only " + onHand.stripTrailingZeros().toPlainString());
        this.onHand = onHand.subtract(quantity);
        this.updatedAt = Instant.now();
    }

    /** Stocktake: set to the counted figure and return the difference (counted - previous). */
    public BigDecimal count(BigDecimal counted) {
        if (counted == null || counted.signum() < 0) throw new IllegalArgumentException("counted quantity must not be negative");
        BigDecimal diff = counted.subtract(onHand);
        this.onHand = counted;
        this.updatedAt = Instant.now();
        return diff;
    }
}
