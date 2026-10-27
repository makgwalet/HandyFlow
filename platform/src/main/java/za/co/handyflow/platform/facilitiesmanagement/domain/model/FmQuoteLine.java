package za.co.handyflow.platform.facilitiesmanagement.domain.model;

import jakarta.persistence.*;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;
import za.co.handyflow.platform.shared.TenantId;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.UUID;

/** One priced line of a quote (VAT-exclusive). */
@Entity
@Table(name = "fm_quote_lines")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class FmQuoteLine {

    @Id
    private UUID id = UUID.randomUUID();

    @Embedded
    @AttributeOverride(name = "value", column = @Column(name = "tenant_id", nullable = false))
    private TenantId tenantId;

    @Column(name = "quote_id", nullable = false)
    private UUID quoteId;

    @Column(name = "line_no", nullable = false)
    private int lineNo;

    @Column(nullable = false, length = 500)
    private String description;

    @Column(nullable = false, precision = 12, scale = 3)
    private BigDecimal quantity;

    @Column(name = "unit_price", nullable = false, precision = 15, scale = 2)
    private BigDecimal unitPrice;

    @Column(name = "line_total", nullable = false, precision = 15, scale = 2)
    private BigDecimal lineTotal;

    public static FmQuoteLine create(TenantId tenantId, UUID quoteId, int lineNo, String description, BigDecimal quantity, BigDecimal unitPrice) {
        if (description == null || description.isBlank()) throw new IllegalArgumentException("Every line needs a description");
        if (description.trim().length() > 500) throw new IllegalArgumentException("A line description can be at most 500 characters");
        if (quantity == null || quantity.signum() <= 0) throw new IllegalArgumentException("A line quantity must be above zero");
        if (unitPrice == null || unitPrice.signum() < 0) throw new IllegalArgumentException("A line price cannot be negative");
        FmQuoteLine l = new FmQuoteLine();
        l.tenantId = tenantId;
        l.quoteId = quoteId;
        l.lineNo = lineNo;
        l.description = description.trim();
        l.quantity = quantity;
        l.unitPrice = unitPrice.setScale(2, RoundingMode.HALF_UP);
        l.lineTotal = quantity.multiply(l.unitPrice).setScale(2, RoundingMode.HALF_UP);
        return l;
    }
}
