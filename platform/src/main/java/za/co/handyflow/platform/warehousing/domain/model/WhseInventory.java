package za.co.handyflow.platform.warehousing.domain.model;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import jakarta.persistence.Version;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

/**
 * One client's stock position for one item at one location — the live
 * balance WhseStockMovement's append-only ledger explains the history of.
 * Unique per (tenantId, clientId, itemId, locationId) — enforced at the
 * DB level (see V258 migration).
 * <p>
 * Reservation model: qtyAllocated tracks stock committed to open outbound
 * orders so two orders can never both draw down the same units before
 * either ships (allocate() at PICKING, fulfillAllocation() at
 * PACKED/SHIPPED, deallocate() on cancellation) — same reason
 * ScInventory.qtyReserved exists in `supplychain`, though this entity
 * doesn't import or depend on that class in any way (see package-info).
 */
@Entity
@Table(name = "whse_inventory")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class WhseInventory {

    @Id
    private UUID id = UUID.randomUUID();

    @Column(name = "tenant_id", nullable = false)
    private UUID tenantId;

    @Column(name = "client_id", nullable = false)
    private UUID clientId;

    @Column(name = "item_id", nullable = false)
    private UUID itemId;

    @Column(name = "location_id", nullable = false)
    private UUID locationId;

    @Column(name = "qty_on_hand", nullable = false, precision = 12, scale = 3)
    private BigDecimal qtyOnHand = BigDecimal.ZERO;

    @Column(name = "qty_allocated", nullable = false, precision = 12, scale = 3)
    private BigDecimal qtyAllocated = BigDecimal.ZERO;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;

    @Version
    private Long version;

    public static WhseInventory create(UUID tenantId, UUID clientId, UUID itemId, UUID locationId) {
        WhseInventory i = new WhseInventory();
        i.tenantId = tenantId;
        i.clientId = clientId;
        i.itemId = itemId;
        i.locationId = locationId;
        i.qtyOnHand = BigDecimal.ZERO;
        i.qtyAllocated = BigDecimal.ZERO;
        i.createdAt = Instant.now();
        i.updatedAt = Instant.now();
        return i;
    }

    public BigDecimal available() {
        return qtyOnHand.subtract(qtyAllocated);
    }

    /** Receipt of goods from an inbound shipment — increases on-hand only, never touches allocation. */
    public void increaseOnHand(BigDecimal qty) {
        requirePositive(qty);
        this.qtyOnHand = this.qtyOnHand.add(qty);
        this.updatedAt = Instant.now();
    }

    /** Manual stock adjustment (count correction, damage write-off, ...). delta may be negative; on-hand may never go below zero. */
    public void adjustOnHand(BigDecimal delta) {
        if (delta == null || delta.signum() == 0) {
            throw new IllegalArgumentException("delta must be non-zero");
        }
        BigDecimal newQty = this.qtyOnHand.add(delta);
        if (newQty.signum() < 0) {
            throw new IllegalStateException("Adjustment would take on-hand quantity below zero (currently "
                    + this.qtyOnHand + ", delta " + delta + ")");
        }
        this.qtyOnHand = newQty;
        this.updatedAt = Instant.now();
    }

    /** Commits stock to an outbound order — cannot allocate more than what's actually available (on-hand minus already allocated). */
    public void allocate(BigDecimal qty) {
        requirePositive(qty);
        if (qty.compareTo(available()) > 0) {
            throw new IllegalStateException(
                    "Cannot allocate " + qty + " — only " + available() + " available at this location");
        }
        this.qtyAllocated = this.qtyAllocated.add(qty);
        this.updatedAt = Instant.now();
    }

    /** Releases a previously allocated quantity without shipping it — order line cancelled or reduced. */
    public void deallocate(BigDecimal qty) {
        requirePositive(qty);
        if (qty.compareTo(this.qtyAllocated) > 0) {
            throw new IllegalStateException("Cannot deallocate " + qty + " — only " + this.qtyAllocated + " is allocated");
        }
        this.qtyAllocated = this.qtyAllocated.subtract(qty);
        this.updatedAt = Instant.now();
    }

    /** Completes a pick/ship — reduces on-hand AND allocation together by the same amount. */
    public void fulfillAllocation(BigDecimal qty) {
        requirePositive(qty);
        if (qty.compareTo(this.qtyAllocated) > 0) {
            throw new IllegalStateException("Cannot fulfil " + qty + " — only " + this.qtyAllocated + " is allocated");
        }
        if (qty.compareTo(this.qtyOnHand) > 0) {
            throw new IllegalStateException("Cannot fulfil " + qty + " — only " + this.qtyOnHand + " is on hand");
        }
        this.qtyOnHand = this.qtyOnHand.subtract(qty);
        this.qtyAllocated = this.qtyAllocated.subtract(qty);
        this.updatedAt = Instant.now();
    }

    private void requirePositive(BigDecimal qty) {
        if (qty == null || qty.signum() <= 0) {
            throw new IllegalArgumentException("qty must be positive");
        }
    }
}
