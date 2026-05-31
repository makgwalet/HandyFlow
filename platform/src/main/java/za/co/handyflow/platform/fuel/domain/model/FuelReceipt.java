// fuel/domain/model/FuelReceipt.java

package za.co.handyflow.platform.fuel.domain.model;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.NoArgsConstructor;
import za.co.handyflow.platform.shared.TenantId;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "fuel_receipts")
@Getter
@NoArgsConstructor(access = lombok.AccessLevel.PROTECTED)
public class FuelReceipt {

    @Id
    private UUID id = UUID.randomUUID();

    @Embedded
    @AttributeOverride(name = "value",
            column = @Column(name = "tenant_id", nullable = false))
    private TenantId tenantId;

    @Column(name = "tank_id", nullable = false)
    private UUID tankId;

    @Column(name = "supplier_id")
    private UUID supplierId;

    @Column(name = "litres_received", nullable = false, precision = 12, scale = 2)
    private BigDecimal litresReceived;

    @Column(name = "price_per_litre", nullable = false, precision = 10, scale = 4)
    private BigDecimal pricePerLitre;

    @Column(name = "total_cost", nullable = false, precision = 15, scale = 2)
    private BigDecimal totalCost;

    @Column(name = "received_at", nullable = false)
    private Instant receivedAt;

    @Column(name = "delivery_note")
    private String deliveryNote;

    @Column(name = "invoice_ref")
    private String invoiceRef;

    @Column(name = "level_before", precision = 12, scale = 2)
    private BigDecimal levelBefore;

    @Column(name = "level_after", precision = 12, scale = 2)
    private BigDecimal levelAfter;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;

    @Column(name = "deleted_at")
    private Instant deletedAt;

    @Version
    private Long version;

    public static FuelReceipt create(TenantId tenantId, UUID tankId,
                                     UUID supplierId, BigDecimal litresReceived,
                                     BigDecimal pricePerLitre, Instant receivedAt,
                                     String deliveryNote, String invoiceRef,
                                     BigDecimal levelBefore, BigDecimal levelAfter) {
        FuelReceipt r = new FuelReceipt();
        r.tenantId       = tenantId;
        r.tankId         = tankId;
        r.supplierId     = supplierId;
        r.litresReceived = litresReceived;
        r.pricePerLitre  = pricePerLitre;
        r.totalCost      = litresReceived.multiply(pricePerLitre)
                .setScale(2, java.math.RoundingMode.HALF_UP);
        r.receivedAt     = receivedAt;
        r.deliveryNote   = deliveryNote;
        r.invoiceRef     = invoiceRef;
        r.levelBefore    = levelBefore;
        r.levelAfter     = levelAfter;
        r.createdAt      = Instant.now();
        r.updatedAt      = Instant.now();
        return r;
    }

    public boolean isDeleted() { return deletedAt != null; }

    @PreUpdate
    void onUpdate() { this.updatedAt = Instant.now(); }
}