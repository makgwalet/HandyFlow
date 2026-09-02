package za.co.handyflow.platform.warehousing.domain.model;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;
import java.util.UUID;

@Entity
@Table(name = "whse_outbound_order_lines")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class WhseOutboundOrderLine {

    @Id
    private UUID id = UUID.randomUUID();

    @Column(name = "tenant_id", nullable = false)
    private UUID tenantId;

    @Column(name = "order_id", nullable = false)
    private UUID orderId;

    @Column(name = "item_id", nullable = false)
    private UUID itemId;

    @Column(name = "location_id")
    private UUID locationId; // which location this line was allocated/picked from — set at allocation time

    @Column(name = "qty_ordered", nullable = false, precision = 12, scale = 3)
    private BigDecimal qtyOrdered;

    @Column(name = "qty_picked", nullable = false, precision = 12, scale = 3)
    private BigDecimal qtyPicked = BigDecimal.ZERO;

    @Column(name = "notes")
    private String notes;

    public static WhseOutboundOrderLine create(UUID tenantId, UUID orderId, UUID itemId, BigDecimal qtyOrdered,
                                                String notes) {
        if (qtyOrdered == null || qtyOrdered.signum() <= 0) {
            throw new IllegalArgumentException("qtyOrdered must be positive");
        }
        WhseOutboundOrderLine l = new WhseOutboundOrderLine();
        l.tenantId = tenantId;
        l.orderId = orderId;
        l.itemId = itemId;
        l.qtyOrdered = qtyOrdered;
        l.qtyPicked = BigDecimal.ZERO;
        l.notes = notes;
        return l;
    }

    public void allocate(UUID locationId) {
        this.locationId = locationId;
    }

    public void markPicked(BigDecimal qty) {
        if (qty == null || qty.signum() <= 0) {
            throw new IllegalArgumentException("qty must be positive");
        }
        if (this.qtyPicked.add(qty).compareTo(this.qtyOrdered) > 0) {
            throw new IllegalStateException("Cannot pick more than was ordered on this line ("
                    + this.qtyOrdered + " ordered, " + this.qtyPicked + " already picked)");
        }
        this.qtyPicked = this.qtyPicked.add(qty);
    }

    public boolean isFullyPicked() {
        return qtyPicked.compareTo(qtyOrdered) >= 0;
    }
}
