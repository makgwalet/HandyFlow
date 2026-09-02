package za.co.handyflow.platform.agriculture.domain.model;

import jakarta.persistence.*;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;
import za.co.handyflow.platform.shared.TenantId;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

/**
 * A feed/seed/fertiliser/chemical/veterinary-supply stock item, scoped per
 * farm. {@code supplychain} exposes no public facade ({@code ScmService}
 * is internal-only, confirmed by direct source read) — the same situation
 * {@code facilitiesmanagement} and {@code warehousing} already hit and
 * resolved the same way, by owning their own inventory tables. This
 * module does the same rather than waiting on a platform-level
 * {@code ScmFacade} that doesn't exist yet.
 * <p>
 * {@code currentQuantity} is denormalized and maintained by
 * {@link AgStockMovement} postings at the application-service layer —
 * the same "current state on the parent, trail on the child" shape used
 * throughout this module ({@code AgAnimal.currentWeightKg} against
 * {@code AgWeightRecord}, {@code AgGroup.currentCount} against mortality/
 * movement records).
 */
@Entity
@Table(name = "ag_inventory_items")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class AgInventoryItem {

    @Id
    private UUID id = UUID.randomUUID();

    @Embedded
    @AttributeOverride(name = "value", column = @Column(name = "tenant_id", nullable = false))
    private TenantId tenantId;

    @Column(name = "farm_id", nullable = false)
    private UUID farmId;

    @Column(name = "item_name", nullable = false)
    private String itemName;

    /** FEED | SEED | FERTILISER | CHEMICAL | VETERINARY | OTHER */
    @Column(nullable = false)
    private String category;

    @Column(name = "unit_of_measure", nullable = false)
    private String unitOfMeasure;

    @Column(name = "current_quantity", nullable = false, precision = 14, scale = 3)
    private BigDecimal currentQuantity = BigDecimal.ZERO;

    @Column(name = "reorder_level", precision = 14, scale = 3)
    private BigDecimal reorderLevel;

    @Column(name = "unit_cost", precision = 12, scale = 2)
    private BigDecimal unitCost;

    private String supplier;

    @Column(nullable = false)
    private String status = "ACTIVE"; // ACTIVE | INACTIVE

    @Column(columnDefinition = "TEXT")
    private String notes;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;

    @Column(name = "deleted_at")
    private Instant deletedAt;

    @Version
    private Long version;

    public static AgInventoryItem create(TenantId tenantId, UUID farmId, String itemName, String category,
                                          String unitOfMeasure, BigDecimal reorderLevel, BigDecimal unitCost,
                                          String supplier) {
        if (tenantId == null) throw new IllegalArgumentException("tenantId is required");
        if (farmId == null) throw new IllegalArgumentException("farmId is required");
        if (itemName == null || itemName.isBlank()) throw new IllegalArgumentException("itemName is required");
        if (category == null || category.isBlank()) throw new IllegalArgumentException("category is required");
        if (unitOfMeasure == null || unitOfMeasure.isBlank()) throw new IllegalArgumentException("unitOfMeasure is required");

        AgInventoryItem i = new AgInventoryItem();
        i.tenantId = tenantId;
        i.farmId = farmId;
        i.itemName = itemName;
        i.category = category;
        i.unitOfMeasure = unitOfMeasure;
        i.reorderLevel = reorderLevel;
        i.unitCost = unitCost;
        i.supplier = supplier;
        i.createdAt = Instant.now();
        i.updatedAt = Instant.now();
        return i;
    }

    public void update(String itemName, BigDecimal reorderLevel, BigDecimal unitCost, String supplier, String notes) {
        if (itemName != null && !itemName.isBlank()) this.itemName = itemName;
        this.reorderLevel = reorderLevel;
        this.unitCost = unitCost;
        this.supplier = supplier;
        this.notes = notes;
    }

    public void receive(BigDecimal quantity, BigDecimal newUnitCost) {
        if (quantity == null || quantity.signum() <= 0) throw new IllegalArgumentException("quantity must be positive");
        this.currentQuantity = this.currentQuantity.add(quantity);
        if (newUnitCost != null) this.unitCost = newUnitCost;
    }

    public void issue(BigDecimal quantity) {
        if (quantity == null || quantity.signum() <= 0) throw new IllegalArgumentException("quantity must be positive");
        if (quantity.compareTo(this.currentQuantity) > 0) {
            throw new IllegalStateException("cannot issue " + quantity + " " + unitOfMeasure + " of " + itemName + " — only " + currentQuantity + " on hand");
        }
        this.currentQuantity = this.currentQuantity.subtract(quantity);
    }

    public void adjust(BigDecimal newQuantity) {
        if (newQuantity == null || newQuantity.signum() < 0) throw new IllegalArgumentException("newQuantity must be >= 0");
        this.currentQuantity = newQuantity;
    }

    public boolean isBelowReorderLevel() {
        return reorderLevel != null && currentQuantity.compareTo(reorderLevel) < 0;
    }

    public void deactivate() { this.status = "INACTIVE"; }

    public void reactivate() { this.status = "ACTIVE"; }

    public void softDelete() { this.deletedAt = Instant.now(); this.status = "INACTIVE"; }

    public boolean isDeleted() { return deletedAt != null; }

    @PreUpdate
    void onUpdate() { this.updatedAt = Instant.now(); }
}
