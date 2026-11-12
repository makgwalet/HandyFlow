package za.co.handyflow.platform.fuel.domain.model;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.NoArgsConstructor;
import za.co.handyflow.platform.shared.TenantId;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

/**
 * Phase 6b: "when this tank falls below this level, draft a requisition for this many litres". The company chooses every number;
 * nothing is assumed. A rule can only cause a DRAFT requisition: submitting, approving and ordering stay with people.
 */
@Entity
@Table(name = "fuel_automation_rules")
@Getter
@NoArgsConstructor(access = lombok.AccessLevel.PROTECTED)
public class FuelAutomationRule {

    @Id private UUID id = UUID.randomUUID();

    @Embedded
    @AttributeOverride(name = "value", column = @Column(name = "tenant_id", nullable = false))
    private TenantId tenantId;

    @Column(name = "tank_id", nullable = false, updatable = false) private UUID tankId;
    @Column(name = "supplier_id") private UUID supplierId;
    @Column(name = "reorder_below_litres", nullable = false, precision = 14, scale = 2) private BigDecimal reorderBelowLitres;
    @Column(name = "order_litres", nullable = false, precision = 14, scale = 2) private BigDecimal orderLitres;
    @Column(name = "estimated_price", precision = 12, scale = 4) private BigDecimal estimatedPrice;
    @Column(nullable = false) private boolean enabled = true;
    @Column(name = "last_checked_at") private Instant lastCheckedAt;
    @Column(name = "last_drafted_at") private Instant lastDraftedAt;
    @Column(name = "created_by", updatable = false) private UUID createdBy;
    @Column(name = "created_at", nullable = false, updatable = false) private Instant createdAt = Instant.now();
    @Column(name = "updated_at", nullable = false) private Instant updatedAt = Instant.now();
    @Column(name = "deleted_at") private Instant deletedAt;
    @Version private Long version;

    public static FuelAutomationRule create(TenantId tenantId, UUID tankId, UUID supplierId, BigDecimal below, BigDecimal orderLitres, BigDecimal price, UUID by) {
        if (tankId == null) throw new IllegalArgumentException("Choose a tank.");
        FuelAutomationRule r = new FuelAutomationRule();
        r.tenantId = tenantId; r.tankId = tankId; r.createdBy = by;
        r.set(supplierId, below, orderLitres, price, true);
        return r;
    }

    public void update(UUID supplierId, BigDecimal below, BigDecimal orderLitres, BigDecimal price, boolean enabled) { set(supplierId, below, orderLitres, price, enabled); }

    private void set(UUID supplierId, BigDecimal below, BigDecimal orderLitres, BigDecimal price, boolean enabled) {
        if (below == null || below.signum() <= 0) throw new IllegalArgumentException("Enter the level, in litres, below which to draft a requisition.");
        if (orderLitres == null || orderLitres.signum() <= 0) throw new IllegalArgumentException("Enter how many litres to ask for.");
        if (price != null && price.signum() < 0) throw new IllegalArgumentException("The estimated price cannot be negative.");
        this.supplierId = supplierId; this.reorderBelowLitres = below; this.orderLitres = orderLitres; this.estimatedPrice = price; this.enabled = enabled;
        this.updatedAt = Instant.now();
    }

    /** Strictly below: a tank sitting exactly at the level is not yet short. */
    public boolean isBelow(BigDecimal currentLitres) { return currentLitres != null && currentLitres.compareTo(reorderBelowLitres) < 0; }

    public void recordChecked() { this.lastCheckedAt = Instant.now(); }
    public void recordDrafted() { this.lastDraftedAt = Instant.now(); }
    public void softDelete() { this.deletedAt = Instant.now(); this.enabled = false; this.updatedAt = Instant.now(); }
}
