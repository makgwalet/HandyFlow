package za.co.handyflow.platform.facilitiesmanagement.domain.model;

import jakarta.persistence.*;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;
import za.co.handyflow.platform.shared.TenantId;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.Instant;
import java.util.Set;
import java.util.UUID;

/**
 * One itemised cost against a work order. {@code totalCost} is always quantity x unitCost, rounded to cents.
 * {@code billable=false} lines (e.g. absorbed travel) are kept for job-costing but never reach the invoice.
 * Chunk 7: a line has a COST (what it costs the FM company) and a CHARGE (what the client pays); charge defaults to
 * cost, i.e. no markup. Margin = charge - cost on billable lines, minus the cost of non-billable ones.
 */
@Entity
@Table(name = "fm_work_order_cost_lines")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class FmWorkOrderCostLine {

    public static final Set<String> TYPES = Set.of("LABOUR", "PARTS", "TRAVEL", "CONTRACTOR", "OTHER");

    @Id
    private UUID id = UUID.randomUUID();

    @Embedded
    @AttributeOverride(name = "value", column = @Column(name = "tenant_id", nullable = false))
    private TenantId tenantId;

    @Column(name = "work_order_id", nullable = false)
    private UUID workOrderId;

    @Column(name = "cost_type", nullable = false, length = 20)
    private String costType;

    @Column(nullable = false)
    private String description;

    @Column(nullable = false, precision = 12, scale = 3)
    private BigDecimal quantity;

    @Column(name = "unit_cost", nullable = false, precision = 15, scale = 2)
    private BigDecimal unitCost;

    @Column(name = "total_cost", nullable = false, precision = 15, scale = 2)
    private BigDecimal totalCost;

    @Column(name = "unit_charge", nullable = false, precision = 15, scale = 2)
    private BigDecimal unitCharge;

    @Column(name = "total_charge", nullable = false, precision = 15, scale = 2)
    private BigDecimal totalCharge;

    @Column(nullable = false)
    private boolean billable = true;

    @Column(name = "recorded_by_user_id")
    private UUID recordedByUserId;

    @Column(name = "recorded_at", nullable = false, updatable = false)
    private Instant recordedAt;

    public static FmWorkOrderCostLine create(TenantId tenantId, UUID workOrderId, String costType, String description,
                                             BigDecimal quantity, BigDecimal unitCost, BigDecimal unitCharge,
                                             boolean billable, UUID recordedByUserId) {
        String type = costType == null ? "" : costType.toUpperCase();
        if (!TYPES.contains(type))
            throw new IllegalArgumentException("costType must be one of " + TYPES);
        if (description == null || description.isBlank())
            throw new IllegalArgumentException("description is required");
        if (quantity == null || quantity.signum() <= 0)
            throw new IllegalArgumentException("quantity must be positive");
        if (unitCost == null || unitCost.signum() < 0)
            throw new IllegalArgumentException("unitCost must not be negative");
        if (unitCharge != null && unitCharge.signum() < 0)
            throw new IllegalArgumentException("unitCharge must not be negative");

        FmWorkOrderCostLine c = new FmWorkOrderCostLine();
        c.tenantId = tenantId;
        c.workOrderId = workOrderId;
        c.costType = type;
        c.description = description.trim();
        c.quantity = quantity;
        c.unitCost = unitCost.setScale(2, RoundingMode.HALF_UP);
        c.totalCost = quantity.multiply(c.unitCost).setScale(2, RoundingMode.HALF_UP);
        c.unitCharge = (unitCharge != null ? unitCharge : unitCost).setScale(2, RoundingMode.HALF_UP);
        c.totalCharge = quantity.multiply(c.unitCharge).setScale(2, RoundingMode.HALF_UP);
        c.billable = billable;
        c.recordedByUserId = recordedByUserId;
        c.recordedAt = Instant.now();
        return c;
    }
}