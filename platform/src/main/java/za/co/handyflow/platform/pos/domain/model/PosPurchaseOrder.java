package za.co.handyflow.platform.pos.domain.model;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.NoArgsConstructor;
import za.co.handyflow.platform.shared.TenantId;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.util.UUID;

@Entity
@Table(name = "pos_purchase_orders")
@Getter
@NoArgsConstructor(access = lombok.AccessLevel.PROTECTED)
public class PosPurchaseOrder {

    @Id private UUID id = UUID.randomUUID();

    @Embedded
    @AttributeOverride(name = "value", column = @Column(name = "tenant_id", nullable = false))
    private TenantId tenantId;

    @Column(name = "order_number",  nullable = false) private String    orderNumber;
    @Column(name = "supplier_id")                      private UUID      supplierId;
    @Column(name = "supplier_name", nullable = false)  private String    supplierName;
    @Column(nullable = false)                           private String    status = "DRAFT";
    @Column(name = "order_date",    nullable = false)   private LocalDate orderDate;
    @Column(name = "expected_date")                     private LocalDate expectedDate;
    @Column(name = "received_date")                     private LocalDate receivedDate;
    @Column(nullable = false, precision = 15, scale = 2) private BigDecimal subtotal     = BigDecimal.ZERO;
    @Column(name = "vat_amount",  nullable = false, precision = 15, scale = 2) private BigDecimal vatAmount   = BigDecimal.ZERO;
    @Column(name = "total_amount",nullable = false, precision = 15, scale = 2) private BigDecimal totalAmount = BigDecimal.ZERO;
    private String notes;
    @Column(name = "created_by") private UUID    createdBy;
    @Column(name = "created_at") private Instant createdAt;
    @Column(name = "updated_at") private Instant updatedAt;

    public static PosPurchaseOrder create(TenantId tenantId, String orderNumber,
                                           UUID supplierId, String supplierName,
                                           LocalDate expectedDate, String notes, UUID createdBy) {
        PosPurchaseOrder p  = new PosPurchaseOrder();
        p.tenantId          = tenantId;
        p.orderNumber       = orderNumber;
        p.supplierId        = supplierId;
        p.supplierName      = supplierName;
        p.orderDate         = LocalDate.now();
        p.expectedDate      = expectedDate;
        p.notes             = notes;
        p.createdBy         = createdBy;
        p.status            = "DRAFT";
        p.createdAt         = Instant.now();
        p.updatedAt         = Instant.now();
        return p;
    }

    public void setTotals(BigDecimal subtotal, BigDecimal vatAmount, BigDecimal totalAmount) {
        this.subtotal    = subtotal;
        this.vatAmount   = vatAmount;
        this.totalAmount = totalAmount;
        this.updatedAt   = Instant.now();
    }

    public void markOrdered()             { this.status = "ORDERED";   touch(); }
    public void markPartiallyReceived()   { this.status = "PARTIALLY_RECEIVED"; touch(); }
    public void markReceived()            { this.status = "RECEIVED";  this.receivedDate = LocalDate.now(); touch(); }
    public void cancel()                  { this.status = "CANCELLED"; touch(); }
    private void touch() { this.updatedAt = Instant.now(); }

    public boolean isDraft()    { return "DRAFT".equals(status); }
    public boolean isOrdered()  { return "ORDERED".equals(status) || "PARTIALLY_RECEIVED".equals(status); }
}
