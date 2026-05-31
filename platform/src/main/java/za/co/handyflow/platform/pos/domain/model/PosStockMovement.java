package za.co.handyflow.platform.pos.domain.model;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "pos_stock_movements")
@Getter
@NoArgsConstructor(access = lombok.AccessLevel.PROTECTED)
public class PosStockMovement {

    @Id private UUID id = UUID.randomUUID();

    @Column(name = "tenant_id",     nullable = false) private UUID       tenantId;
    @Column(name = "stock_item_id", nullable = false) private UUID       stockItemId;
    @Column(name = "movement_type", nullable = false) private String     movementType;
    @Column(name = "qty_change",    nullable = false, precision = 12, scale = 3) private BigDecimal qtyChange;
    @Column(name = "qty_before",    nullable = false, precision = 12, scale = 3) private BigDecimal qtyBefore;
    @Column(name = "qty_after",     nullable = false, precision = 12, scale = 3) private BigDecimal qtyAfter;
    @Column(name = "reference_type") private String referenceType;
    @Column(name = "reference_id")   private UUID   referenceId;
    private String notes;
    @Column(name = "created_by")  private UUID    createdBy;
    @Column(name = "created_at")  private Instant createdAt;

    public static PosStockMovement create(UUID tenantId, UUID stockItemId,
                                           String movementType, BigDecimal qtyChange,
                                           BigDecimal qtyBefore, BigDecimal qtyAfter,
                                           String referenceType, UUID referenceId,
                                           String notes, UUID createdBy) {
        PosStockMovement m  = new PosStockMovement();
        m.tenantId          = tenantId;
        m.stockItemId       = stockItemId;
        m.movementType      = movementType;
        m.qtyChange         = qtyChange;
        m.qtyBefore         = qtyBefore;
        m.qtyAfter          = qtyAfter;
        m.referenceType     = referenceType;
        m.referenceId       = referenceId;
        m.notes             = notes;
        m.createdBy         = createdBy;
        m.createdAt         = Instant.now();
        return m;
    }
}
