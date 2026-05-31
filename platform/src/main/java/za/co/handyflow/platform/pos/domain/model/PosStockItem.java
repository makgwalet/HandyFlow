package za.co.handyflow.platform.pos.domain.model;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.NoArgsConstructor;
import za.co.handyflow.platform.shared.TenantId;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "pos_stock_items")
@Getter
@NoArgsConstructor(access = lombok.AccessLevel.PROTECTED)
public class PosStockItem {

    @Id private UUID id = UUID.randomUUID();

    @Embedded
    @AttributeOverride(name = "value", column = @Column(name = "tenant_id", nullable = false))
    private TenantId tenantId;

    @Column(name = "catalogue_item_id", nullable = false) private UUID       catalogueItemId;
    @Column(name = "qty_on_hand",  nullable = false, precision = 12, scale = 3) private BigDecimal qtyOnHand  = BigDecimal.ZERO;
    @Column(name = "qty_reserved", nullable = false, precision = 12, scale = 3) private BigDecimal qtyReserved = BigDecimal.ZERO;
    @Column(name = "reorder_level",nullable = false, precision = 12, scale = 3) private BigDecimal reorderLevel = BigDecimal.ZERO;
    @Column(name = "reorder_qty",  nullable = false, precision = 12, scale = 3) private BigDecimal reorderQty  = BigDecimal.ZERO;
    @Column(name = "cost_price",   nullable = false, precision = 15, scale = 2) private BigDecimal costPrice   = BigDecimal.ZERO;
    private String  location;
    @Column(name = "track_stock", nullable = false) private boolean trackStock = true;
    @Column(name = "created_at") private Instant createdAt;
    @Column(name = "updated_at") private Instant updatedAt;

    public static PosStockItem create(TenantId tenantId, UUID catalogueItemId,
                                       BigDecimal qtyOnHand, BigDecimal reorderLevel,
                                       BigDecimal reorderQty, BigDecimal costPrice,
                                       String location) {
        PosStockItem s     = new PosStockItem();
        s.tenantId         = tenantId;
        s.catalogueItemId  = catalogueItemId;
        s.qtyOnHand        = qtyOnHand != null ? qtyOnHand : BigDecimal.ZERO;
        s.reorderLevel     = reorderLevel != null ? reorderLevel : BigDecimal.ZERO;
        s.reorderQty       = reorderQty != null ? reorderQty : BigDecimal.ZERO;
        s.costPrice        = costPrice != null ? costPrice : BigDecimal.ZERO;
        s.location         = location;
        s.trackStock       = true;
        s.createdAt        = Instant.now();
        s.updatedAt        = Instant.now();
        return s;
    }

    public void adjustQty(BigDecimal change) {
        this.qtyOnHand = this.qtyOnHand.add(change);
        this.updatedAt = Instant.now();
    }

    public void setQty(BigDecimal qty) {
        this.qtyOnHand = qty;
        this.updatedAt = Instant.now();
    }

    public void updateCostPrice(BigDecimal newCost) {
        this.costPrice = newCost;
        this.updatedAt = Instant.now();
    }

    public void update(BigDecimal reorderLevel, BigDecimal reorderQty,
                        BigDecimal costPrice, String location) {
        if (reorderLevel != null) this.reorderLevel = reorderLevel;
        if (reorderQty   != null) this.reorderQty   = reorderQty;
        if (costPrice    != null) this.costPrice     = costPrice;
        if (location     != null) this.location      = location;
        this.updatedAt = Instant.now();
    }

    public boolean isLowStock() {
        return trackStock && qtyOnHand.compareTo(reorderLevel) <= 0;
    }

    public BigDecimal getAvailableQty() {
        return qtyOnHand.subtract(qtyReserved);
    }
}
