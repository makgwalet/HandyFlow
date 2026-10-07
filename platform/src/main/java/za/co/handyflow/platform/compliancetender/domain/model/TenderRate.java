package za.co.handyflow.platform.compliancetender.domain.model;

import jakarta.persistence.*;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;
import za.co.handyflow.platform.shared.TenantId;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.Set;
import java.util.UUID;

/**
 * One entry in the company's rates library: what something costs the company (a material, a labour or plant rate, a subcontract item), optionally from a named supplier.
 * Adding a rate to a tender's price schedule COPIES it into a line; the line never follows later changes to the rate, so a priced tender does not move under the user.
 * When the cost changes, the old cost is kept as {@code previousUnitCost} so a re-imported supplier list shows what went up or down.
 */
@Entity
@Table(name = "tender_rates")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class TenderRate {

    public static final Set<String> CATEGORIES = Set.of("MATERIAL", "LABOUR", "PLANT", "SUBCONTRACT", "OTHER");

    @Id
    private UUID id = UUID.randomUUID();

    @Embedded
    @AttributeOverride(name = "value", column = @Column(name = "tenant_id", nullable = false))
    private TenantId tenantId;

    @Column(nullable = false)
    private String category;

    @Column(name = "item_ref")
    private String itemRef;

    @Column(nullable = false)
    private String description;

    @Column(nullable = false)
    private String unit = "";

    @Column(name = "unit_cost", nullable = false, precision = 15, scale = 2)
    private BigDecimal unitCost;

    @Column(nullable = false)
    private String supplier = "";

    private String notes;

    @Column(nullable = false)
    private boolean active = true;

    @Column(name = "previous_unit_cost", precision = 15, scale = 2)
    private BigDecimal previousUnitCost;

    @Column(name = "price_changed_at")
    private Instant priceChangedAt;

    @Column(nullable = false)
    private String source = "MANUAL";

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    @Column(name = "created_by")
    private UUID createdBy;

    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;

    @Column(name = "updated_by")
    private UUID updatedBy;

    @Version
    private Long version;

    public static TenderRate create(TenantId tenantId, String category, String itemRef, String description, String unit,
                                    BigDecimal unitCost, String supplier, String notes, String source, UUID by) {
        TenderRate r = new TenderRate();
        r.tenantId = tenantId;
        r.source = source;
        r.createdAt = Instant.now();
        r.createdBy = by;
        r.apply(category, itemRef, description, unit, unitCost, supplier, notes, by);
        return r;
    }

    /** Changes every field; a different cost moves the old one into {@code previousUnitCost}. */
    public void update(String category, String itemRef, String description, String unit, BigDecimal unitCost, String supplier, String notes, UUID by) {
        apply(category, itemRef, description, unit, unitCost, supplier, notes, by);
    }

    public void setActive(boolean active, UUID by) {
        this.active = active;
        this.updatedAt = Instant.now();
        this.updatedBy = by;
    }

    private void apply(String category, String itemRef, String description, String unit, BigDecimal unitCost, String supplier, String notes, UUID by) {
        if (category == null || !CATEGORIES.contains(category)) throw new IllegalArgumentException("category must be one of " + CATEGORIES);
        if (description == null || description.isBlank()) throw new IllegalArgumentException("description is required");
        if (unitCost == null || unitCost.signum() < 0) throw new IllegalArgumentException("unitCost must be zero or more");
        // refuse rather than round: a line copied from this rate has to hold the same cost (the price schedule keeps 2 decimals)
        if (unitCost.stripTrailingZeros().scale() > 2) throw new IllegalArgumentException("unitCost can have at most 2 decimal places");
        if (this.unitCost != null && this.unitCost.compareTo(unitCost) != 0) {
            this.previousUnitCost = this.unitCost;
            this.priceChangedAt = Instant.now();
        }
        this.category = category;
        this.itemRef = blankToNull(itemRef);
        this.description = description.trim();
        this.unit = unit == null ? "" : unit.trim();
        this.unitCost = unitCost;
        this.supplier = supplier == null ? "" : supplier.trim();
        this.notes = blankToNull(notes);
        this.updatedAt = Instant.now();
        this.updatedBy = by;
    }

    private static String blankToNull(String s) {
        return s == null || s.isBlank() ? null : s.trim();
    }
}
