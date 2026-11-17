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

    @Column(name = "purchase_order_id")
    private UUID purchaseOrderId;

    @Column(name = "freight_amount", precision = 14, scale = 2)
    private BigDecimal freightAmount;

    @Column(name = "freight_note", length = 200)
    private String freightNote;

    @Column(name = "reversed_at")
    private Instant reversedAt;

    @Column(name = "reversed_by")
    private UUID reversedBy;

    @Column(name = "reversal_reason", length = 500)
    private String reversalReason;

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

    @Column(name = "client_request_id")
    private UUID clientRequestId;

    /** Marks the record with the phone's request id so a replay can be recognised. Call before saving. */
    public FuelReceipt withClientRequestId(UUID id) { this.clientRequestId = id; return this; }

    public void assignPurchaseOrder(UUID purchaseOrderId) { this.purchaseOrderId = purchaseOrderId; }

    /**
     * What the tank's cost should be built from: the supplier's price plus the freight spread over the litres received. With no freight it
     * is exactly the supplier's price. Pure function so the receipt service and the tests use the same arithmetic.
     */
    public static BigDecimal landedPrice(BigDecimal pricePerLitre, BigDecimal litres, BigDecimal freightAmount) {
        if (freightAmount == null || freightAmount.signum() == 0) return pricePerLitre;
        if (freightAmount.signum() < 0) throw new IllegalArgumentException("Freight cannot be negative.");
        return pricePerLitre.add(freightAmount.divide(litres, 4, java.math.RoundingMode.HALF_UP));
    }

    public void recordFreight(BigDecimal freightAmount, String note) {
        if (freightAmount != null && freightAmount.signum() < 0) throw new IllegalArgumentException("Freight cannot be negative.");
        this.freightAmount = freightAmount == null || freightAmount.signum() == 0 ? null : freightAmount.setScale(2, java.math.RoundingMode.HALF_UP);
        this.freightNote = this.freightAmount == null || note == null || note.isBlank() ? null : (note.trim().length() > 200 ? note.trim().substring(0, 200) : note.trim());
    }

    /** Adds freight that arrived after the receipt. The supplier's price and total cost do not change. */
    public void addLateFreight(BigDecimal amount) {
        if (amount == null || amount.signum() <= 0) throw new IllegalArgumentException("The freight must be more than zero.");
        if (isReversed()) throw new IllegalStateException("This receipt has been reversed, so freight cannot be added to it.");
        this.freightAmount = (this.freightAmount == null ? BigDecimal.ZERO : this.freightAmount).add(amount).setScale(2, java.math.RoundingMode.HALF_UP);
        this.updatedAt = Instant.now();
    }

    /** Supplier price plus freight per litre; the supplier price when there is no freight. */
    public BigDecimal landedPricePerLitre() { return landedPrice(pricePerLitre, litresReceived, freightAmount); }

    public boolean isDeleted() { return deletedAt != null; }

    public boolean isReversed() { return reversedAt != null; }

    public void reverse(UUID reversedBy, String reason) {
        if (isReversed()) throw new IllegalStateException("This entry has already been reversed.");
        this.reversedAt     = Instant.now();
        this.reversedBy     = reversedBy;
        this.reversalReason = reason;
        this.updatedAt      = Instant.now();
    }

    @PreUpdate
    void onUpdate() { this.updatedAt = Instant.now(); }
}