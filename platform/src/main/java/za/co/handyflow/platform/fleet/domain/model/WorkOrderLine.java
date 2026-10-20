package za.co.handyflow.platform.fleet.domain.model;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.Instant;
import java.util.UUID;

/** One actual cost line on a work order: labour (hours), a part, sublet work, or other. */
@Entity
@Table(name = "fleet_work_order_lines")
@Getter
@NoArgsConstructor(access = lombok.AccessLevel.PROTECTED)
public class WorkOrderLine {

    @Id
    private UUID id = UUID.randomUUID();

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "work_order_id", nullable = false)
    private WorkOrder workOrder;

    @Enumerated(EnumType.STRING)
    @Column(name = "line_type", nullable = false, length = 10)
    private WorkOrderLineType lineType;

    @Column(nullable = false, length = 500)
    private String description;

    @Column(name = "part_number", length = 100)
    private String partNumber;

    @Column(nullable = false, precision = 10, scale = 2)
    private BigDecimal quantity;

    @Column(name = "unit_cost", nullable = false, precision = 15, scale = 2)
    private BigDecimal unitCost;

    @Column(name = "technician_id")
    private UUID technicianId;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    static WorkOrderLine create(WorkOrder workOrder, WorkOrderLineType type, String description, String partNumber,
                                BigDecimal quantity, BigDecimal unitCost, UUID technicianId) {
        WorkOrderLine l = new WorkOrderLine();
        l.workOrder = workOrder;
        l.createdAt = Instant.now();
        l.update(type, description, partNumber, quantity, unitCost, technicianId);
        return l;
    }

    void update(WorkOrderLineType type, String description, String partNumber,
                BigDecimal quantity, BigDecimal unitCost, UUID technicianId) {
        if (quantity == null || quantity.signum() <= 0) {
            throw new IllegalArgumentException("Quantity must be greater than zero");
        }
        if (unitCost == null || unitCost.signum() < 0) {
            throw new IllegalArgumentException("Unit cost cannot be negative");
        }
        this.lineType = type;
        this.description = description;
        this.partNumber = partNumber;
        this.quantity = quantity;
        this.unitCost = unitCost;
        this.technicianId = technicianId;
    }

    /** Quantity × unit cost, to cents. Derived, not stored. */
    public BigDecimal getLineTotal() {
        return quantity.multiply(unitCost).setScale(2, RoundingMode.HALF_UP);
    }
}