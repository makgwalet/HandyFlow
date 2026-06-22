package za.co.handyflow.platform.invoicing.domain.model;

import jakarta.persistence.*;
import lombok.*;
import za.co.handyflow.platform.shared.TenantId;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.UUID;

/**
 * Template line item stored on a RecurringSchedule.
 * Copied verbatim into InvoiceLineItem each time the schedule fires.
 */
@Entity
@Table(name = "recurring_line_items")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class RecurringLineItem {

    @Id
    private UUID id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "schedule_id", nullable = false)
    private RecurringSchedule schedule;

    @Embedded
    private TenantId tenantId;

    @Column(name = "catalogue_item_id")
    private UUID catalogueItemId;

    @Column(nullable = false)
    private String description;

    @Column(nullable = false, length = 50)
    private String unit;

    @Column(nullable = false, precision = 12, scale = 4)
    private BigDecimal quantity;

    @Column(name = "unit_price", nullable = false, precision = 12, scale = 2)
    private BigDecimal unitPrice;

    @Column(name = "vat_rate", nullable = false, precision = 5, scale = 2)
    private BigDecimal vatRate;

    @Column(name = "sort_order", nullable = false)
    private int sortOrder;

    public static RecurringLineItem create(
            RecurringSchedule schedule,
            TenantId tenantId,
            UUID catalogueItemId,
            String description,
            String unit,
            BigDecimal quantity,
            BigDecimal unitPrice,
            BigDecimal vatRate,
            int sortOrder
    ) {
        var li = new RecurringLineItem();
        li.id               = UUID.randomUUID();
        li.schedule         = schedule;
        li.tenantId         = tenantId;
        li.catalogueItemId  = catalogueItemId;
        li.description      = description;
        li.unit             = unit;
        li.quantity         = quantity;
        li.unitPrice        = unitPrice;
        li.vatRate          = vatRate;
        li.sortOrder        = sortOrder;
        return li;
    }

    public BigDecimal getLineTotal() {
        return quantity.multiply(unitPrice).setScale(2, RoundingMode.HALF_UP);
    }

    public BigDecimal getVatAmount() {
        return getLineTotal()
                .multiply(vatRate)
                .divide(new BigDecimal("100"), 2, RoundingMode.HALF_UP);
    }
}