package za.co.handyflow.platform.complianceservices.domain.model;

import jakarta.persistence.*;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;
import za.co.handyflow.platform.shared.TenantId;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

/** One line of a tender's price schedule: what an item costs the tenant (quantity x unit cost), grouped under a section name (ADR-004, client side). */
@Entity
@Table(name = "client_tender_pricing_lines")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class ClientTenderPricingLine {

    @Id
    private UUID id = UUID.randomUUID();

    @Embedded
    @AttributeOverride(name = "value", column = @Column(name = "tenant_id", nullable = false))
    private TenantId tenantId;

    @Column(name = "client_tender_id", nullable = false)
    private UUID tenderId;

    @Column(nullable = false)
    private String section;

    @Column(name = "item_ref")
    private String itemRef;

    @Column(nullable = false)
    private String description;

    private String unit;

    @Column(nullable = false, precision = 15, scale = 3)
    private BigDecimal quantity;

    @Column(name = "unit_cost", nullable = false, precision = 15, scale = 2)
    private BigDecimal unitCost;

    @Column(name = "sort_order", nullable = false)
    private int sortOrder;

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

    public static ClientTenderPricingLine create(TenantId tenantId, UUID tenderId, String section, String itemRef, String description,
                                           String unit, BigDecimal quantity, BigDecimal unitCost, int sortOrder, UUID createdBy) {
        ClientTenderPricingLine l = new ClientTenderPricingLine();
        l.tenantId = tenantId;
        l.tenderId = tenderId;
        l.sortOrder = sortOrder;
        l.createdAt = Instant.now();
        l.createdBy = createdBy;
        l.apply(section, itemRef, description, unit, quantity, unitCost, createdBy);
        return l;
    }

    public void update(String section, String itemRef, String description, String unit,
                       BigDecimal quantity, BigDecimal unitCost, UUID updatedBy) {
        apply(section, itemRef, description, unit, quantity, unitCost, updatedBy);
    }

    private void apply(String section, String itemRef, String description, String unit,
                       BigDecimal quantity, BigDecimal unitCost, UUID by) {
        if (description == null || description.isBlank()) throw new IllegalArgumentException("description is required");
        if (quantity == null || quantity.signum() < 0) throw new IllegalArgumentException("quantity must be zero or more");
        if (unitCost == null || unitCost.signum() < 0) throw new IllegalArgumentException("unitCost must be zero or more");
        // Refuse rather than round: the database holds 3 decimals of quantity and 2 of cost, and a value silently rounded on save would price differently on the next read.
        if (quantity.stripTrailingZeros().scale() > 3) throw new IllegalArgumentException("quantity can have at most 3 decimal places");
        if (unitCost.stripTrailingZeros().scale() > 2) throw new IllegalArgumentException("unitCost can have at most 2 decimal places");
        this.section = section == null || section.isBlank() ? "General" : section.trim();
        this.itemRef = blankToNull(itemRef);
        this.description = description.trim();
        this.unit = blankToNull(unit);
        this.quantity = quantity;
        this.unitCost = unitCost;
        this.updatedAt = Instant.now();
        this.updatedBy = by;
    }

    private static String blankToNull(String s) {
        return s == null || s.isBlank() ? null : s.trim();
    }
}
