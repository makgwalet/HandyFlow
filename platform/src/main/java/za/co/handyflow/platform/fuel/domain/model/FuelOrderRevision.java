package za.co.handyflow.platform.fuel.domain.model;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.NoArgsConstructor;
import za.co.handyflow.platform.shared.TenantId;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.util.UUID;

/** One version of a quotation's commercial terms. Append-only: a revision is never edited or removed. */
@Entity
@Table(name = "fuel_order_revisions")
@Getter
@NoArgsConstructor(access = lombok.AccessLevel.PROTECTED)
public class FuelOrderRevision {

    @Id private UUID id = UUID.randomUUID();

    @Embedded
    @AttributeOverride(name = "value", column = @Column(name = "tenant_id", nullable = false))
    private TenantId tenantId;

    @Column(name = "order_id", nullable = false, updatable = false) private UUID orderId;
    @Column(name = "revision_no", nullable = false, updatable = false) private int revisionNo;
    @Column(name = "site_id", updatable = false) private UUID siteId;
    @Column(name = "fuel_type", nullable = false, updatable = false, length = 30) private String fuelType;
    @Column(nullable = false, updatable = false, precision = 12, scale = 2) private BigDecimal litres;
    @Column(name = "price_per_litre", nullable = false, updatable = false, precision = 10, scale = 4) private BigDecimal pricePerLitre;
    @Column(name = "requested_date", updatable = false) private LocalDate requestedDate;
    @Column(name = "quote_valid_until", updatable = false) private LocalDate quoteValidUntil;
    @Column(name = "customer_reference", updatable = false, length = 60) private String customerReference;
    @Column(updatable = false, length = 500) private String notes;
    @Column(name = "recorded_by", updatable = false) private UUID recordedBy;
    @Column(name = "recorded_at", nullable = false, updatable = false) private Instant recordedAt = Instant.now();

    public static FuelOrderRevision snapshot(FuelSalesOrder o, int revisionNo, UUID by) {
        FuelOrderRevision r = new FuelOrderRevision();
        r.tenantId = o.getTenantId(); r.orderId = o.getId(); r.revisionNo = revisionNo; r.siteId = o.getSiteId();
        r.fuelType = o.getFuelType(); r.litres = o.getLitresOrdered(); r.pricePerLitre = o.getPricePerLitre();
        r.requestedDate = o.getRequestedDate(); r.quoteValidUntil = o.getQuoteValidUntil();
        r.customerReference = o.getCustomerReference(); r.notes = o.getNotes(); r.recordedBy = by;
        return r;
    }
}
