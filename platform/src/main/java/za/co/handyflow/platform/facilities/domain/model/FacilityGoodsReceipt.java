package za.co.handyflow.platform.facilities.domain.model;

import jakarta.persistence.*;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;
import za.co.handyflow.platform.shared.TenantId;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.Instant;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

/** The document for one delivery booked against a purchase order (a goods-received note). Immutable once created. */
@Entity
@Table(name = "facility_goods_receipts")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class FacilityGoodsReceipt {

    @Id
    private UUID id = UUID.randomUUID();

    @Embedded
    @AttributeOverride(name = "value", column = @Column(name = "tenant_id", nullable = false))
    private TenantId tenantId;

    @Column(name = "grn_number", nullable = false, length = 40)
    private String grnNumber;

    @Column(name = "purchase_order_id", nullable = false)
    private UUID purchaseOrderId;

    @Column(name = "vendor_id", nullable = false)
    private UUID vendorId;

    @Column(name = "store_id")
    private UUID storeId;

    @Column(name = "delivery_note", length = 300)
    private String deliveryNote;

    @Column(name = "received_on", nullable = false)
    private LocalDate receivedOn;

    @Column(name = "total_value", nullable = false, precision = 15, scale = 2)
    private BigDecimal totalValue = BigDecimal.ZERO;

    @Column(name = "received_by_user_id")
    private UUID receivedByUserId;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    @OneToMany(cascade = CascadeType.ALL, orphanRemoval = true, fetch = FetchType.EAGER)
    @JoinColumn(name = "receipt_id", nullable = false)
    private List<FacilityGoodsReceiptLine> lines = new ArrayList<>();

    public record LineSpec(UUID poLineId, UUID itemId, String description, BigDecimal quantity, BigDecimal unitCost) {}

    public static FacilityGoodsReceipt create(TenantId tenantId, String grnNumber, UUID purchaseOrderId, UUID vendorId,
                                              String deliveryNote, LocalDate receivedOn, UUID receivedBy, List<LineSpec> specs) {
        return create(tenantId, grnNumber, purchaseOrderId, vendorId, null, deliveryNote, receivedOn, receivedBy, specs);
    }

    public static FacilityGoodsReceipt create(TenantId tenantId, String grnNumber, UUID purchaseOrderId, UUID vendorId, UUID storeId,
                                              String deliveryNote, LocalDate receivedOn, UUID receivedBy, List<LineSpec> specs) {
        if (specs == null || specs.isEmpty()) throw new IllegalArgumentException("A goods receipt needs at least one line");
        FacilityGoodsReceipt r = new FacilityGoodsReceipt();
        r.tenantId = tenantId;
        r.grnNumber = grnNumber;
        r.purchaseOrderId = purchaseOrderId;
        r.vendorId = vendorId;
        r.storeId = storeId;
        r.deliveryNote = deliveryNote == null || deliveryNote.isBlank() ? null : deliveryNote.trim();
        r.receivedOn = receivedOn;
        r.receivedByUserId = receivedBy;
        r.createdAt = Instant.now();
        BigDecimal total = BigDecimal.ZERO;
        for (LineSpec s : specs) {
            FacilityGoodsReceiptLine l = FacilityGoodsReceiptLine.of(tenantId, s);
            r.lines.add(l);
            total = total.add(l.lineValue());
        }
        r.totalValue = total.setScale(2, RoundingMode.HALF_UP);
        return r;
    }
}
