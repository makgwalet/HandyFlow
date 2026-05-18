package za.co.handyflow.platform.invoicing.domain.model;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.NoArgsConstructor;
import za.co.handyflow.platform.shared.TenantId;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.UUID;

@Entity
@Table(name = "invoice_line_items")
@Getter
@NoArgsConstructor(access = lombok.AccessLevel.PROTECTED)
public class InvoiceLineItem {

    @Id
    private UUID id = UUID.randomUUID();

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "invoice_id", nullable = false)
    private Invoice invoice;

    @Embedded
    @AttributeOverride(name = "value",
            column = @Column(name = "tenant_id", nullable = false))
    private TenantId tenantId;

    @Column(name = "catalogue_item_id")
    private UUID catalogueItemId;

    @Column(nullable = false)
    private String description;

    @Column(nullable = false)
    private String unit;

    @Column(nullable = false, precision = 15, scale = 2)
    private BigDecimal quantity;

    @Column(name = "unit_price", nullable = false, precision = 15, scale = 2)
    private BigDecimal unitPrice;

    @Column(name = "vat_rate", nullable = false, precision = 5, scale = 2)
    private BigDecimal vatRate;

    @Column(name = "line_total", nullable = false, precision = 15, scale = 2)
    private BigDecimal lineTotal;

    @Column(name = "vat_amount", nullable = false, precision = 15, scale = 2)
    private BigDecimal vatAmount;

    @Column(name = "sort_order")
    private Integer sortOrder = 0;

    public static InvoiceLineItem create(Invoice invoice, TenantId tenantId,
                                         UUID catalogueItemId, String description,
                                         String unit, BigDecimal quantity,
                                         BigDecimal unitPrice, BigDecimal vatRate,
                                         int sortOrder) {
        InvoiceLineItem item = new InvoiceLineItem();
        item.invoice = invoice;
        item.tenantId = tenantId;
        item.catalogueItemId = catalogueItemId;
        item.description = description;
        item.unit = unit;
        item.quantity = quantity;
        item.unitPrice = unitPrice;
        item.vatRate = vatRate;
        item.sortOrder = sortOrder;
        item.lineTotal = quantity.multiply(unitPrice)
                .setScale(2, RoundingMode.HALF_UP);
        item.vatAmount = item.lineTotal
                .multiply(vatRate.divide(new BigDecimal("100"), 4, RoundingMode.HALF_UP))
                .setScale(2, RoundingMode.HALF_UP);
        return item;
    }
}
