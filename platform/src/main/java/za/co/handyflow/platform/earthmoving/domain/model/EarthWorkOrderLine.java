package za.co.handyflow.platform.earthmoving.domain.model;

import jakarta.persistence.*;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;
import za.co.handyflow.platform.shared.TenantId;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

/** A part, labour or other cost on a work order. */
@Entity
@Table(name = "earthmoving_work_order_lines")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class EarthWorkOrderLine {

    @Id
    private UUID id = UUID.randomUUID();

    @Embedded
    @AttributeOverride(name = "value", column = @Column(name = "tenant_id", nullable = false))
    private TenantId tenantId;

    @Column(name = "work_order_id", nullable = false)
    private UUID workOrderId;

    @Column(nullable = false)
    private String kind;

    @Column(nullable = false)
    private String description;

    @Column(nullable = false, precision = 12, scale = 2)
    private BigDecimal quantity;

    @Column(name = "unit_cost", nullable = false, precision = 15, scale = 2)
    private BigDecimal unitCost;

    private String supplier;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    public static EarthWorkOrderLine create(TenantId tenantId, UUID workOrderId, String kind, String description,
                                            BigDecimal quantity, BigDecimal unitCost, String supplier) {
        WorkOrderRules.checkLine(kind, description, quantity, unitCost);
        EarthWorkOrderLine l = new EarthWorkOrderLine();
        l.tenantId = tenantId;
        l.workOrderId = workOrderId;
        l.kind = kind.trim().toUpperCase();
        l.description = description.trim();
        l.quantity = quantity;
        l.unitCost = unitCost;
        l.supplier = supplier == null || supplier.isBlank() ? null : supplier.trim();
        l.createdAt = Instant.now();
        return l;
    }

    public BigDecimal total() { return WorkOrderRules.lineTotal(quantity, unitCost); }
}
