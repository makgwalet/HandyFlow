package za.co.handyflow.platform.fuel.domain.model;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.NoArgsConstructor;
import za.co.handyflow.platform.shared.TenantId;

import java.util.UUID;

/** Links a supplier invoice to a receipt it covers. A receipt can be on only one live invoice (unique index). */
@Entity
@Table(name = "fuel_supplier_invoice_receipts")
@Getter
@NoArgsConstructor(access = lombok.AccessLevel.PROTECTED)
public class FuelSupplierInvoiceReceipt {

    @Id
    private UUID id = UUID.randomUUID();

    @Embedded
    @AttributeOverride(name = "value", column = @Column(name = "tenant_id", nullable = false))
    private TenantId tenantId;

    @Column(name = "invoice_id", nullable = false, updatable = false) private UUID invoiceId;
    @Column(name = "receipt_id", nullable = false, updatable = false) private UUID receiptId;

    public static FuelSupplierInvoiceReceipt of(TenantId tenantId, UUID invoiceId, UUID receiptId) {
        FuelSupplierInvoiceReceipt l = new FuelSupplierInvoiceReceipt();
        l.tenantId = tenantId; l.invoiceId = invoiceId; l.receiptId = receiptId;
        return l;
    }
}
