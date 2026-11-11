package za.co.handyflow.platform.fuel.domain.model;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.NoArgsConstructor;
import za.co.handyflow.platform.shared.TenantId;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

/** One immutable line of the stock ledger. Never updated or deleted (the database enforces it). */
@Entity
@Table(name = "fuel_stock_movements")
@Getter
@NoArgsConstructor(access = lombok.AccessLevel.PROTECTED)
public class FuelStockMovement {

    public enum Type { OPENING, RECEIPT, DISPATCH, DELIVERY, ADJUSTMENT, TRANSFER_OUT, TRANSFER_IN, REVERSAL }

    @Id
    private UUID id = UUID.randomUUID();

    @Embedded
    @AttributeOverride(name = "value",
            column = @Column(name = "tenant_id", nullable = false))
    private TenantId tenantId;

    @Column(name = "tank_id", nullable = false, updatable = false)
    private UUID tankId;

    @Enumerated(EnumType.STRING)
    @Column(name = "movement_type", nullable = false, updatable = false)
    private Type movementType;

    @Column(name = "litres_delta", nullable = false, updatable = false, precision = 12, scale = 2)
    private BigDecimal litresDelta;

    @Column(name = "balance_after", nullable = false, updatable = false, precision = 12, scale = 2)
    private BigDecimal balanceAfter;

    @Column(name = "source_type", updatable = false, length = 20)
    private String sourceType;

    @Column(name = "source_id", updatable = false)
    private UUID sourceId;

    @Column(updatable = false, length = 500)
    private String reason;

    @Column(name = "created_by", updatable = false)
    private UUID createdBy;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    public static FuelStockMovement of(TenantId tenantId, UUID tankId, Type type, BigDecimal delta,
                                       BigDecimal balanceAfter, String sourceType, UUID sourceId,
                                       String reason, UUID createdBy) {
        if (delta == null || delta.signum() == 0) throw new IllegalArgumentException("A stock movement cannot be zero litres.");
        FuelStockMovement m = new FuelStockMovement();
        m.tenantId     = tenantId;
        m.tankId       = tankId;
        m.movementType = type;
        m.litresDelta  = delta;
        m.balanceAfter = balanceAfter;
        m.sourceType   = sourceType;
        m.sourceId     = sourceId;
        m.reason       = reason;
        m.createdBy    = createdBy;
        m.createdAt    = Instant.now();
        return m;
    }
}
