package za.co.handyflow.platform.complianceservices.domain.model;

import jakarta.persistence.*;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;
import za.co.handyflow.platform.shared.TenantId;

import java.math.BigDecimal;
import java.util.UUID;

/** An extra priced on top of a quote line, or a disbursement added to it at cost. */
@Entity
@Table(name = "compliance_quote_adjustments")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class ComplianceQuoteAdjustment {

    public static final String EXTRA = "EXTRA";
    public static final String DISBURSEMENT = "DISBURSEMENT";

    @Id
    private UUID id = UUID.randomUUID();

    @Embedded
    @AttributeOverride(name = "value", column = @Column(name = "tenant_id", nullable = false))
    private TenantId tenantId;

    @Column(name = "line_id", nullable = false)
    private UUID lineId;

    @Column(nullable = false)
    private String kind;

    @Column(nullable = false)
    private String description;

    @Column(precision = 10, scale = 2)
    private BigDecimal quantity;

    @Column(nullable = false, precision = 14, scale = 2)
    private BigDecimal amount;

    @Column(nullable = false)
    private boolean vatable;

    @Column(name = "sort_order", nullable = false)
    private int sortOrder;

    public static ComplianceQuoteAdjustment extra(TenantId tenantId, UUID lineId, String description, BigDecimal quantity, BigDecimal unitPrice, int order) {
        if (quantity == null || quantity.signum() <= 0) throw new IllegalArgumentException("An extra needs a quantity above 0");
        BigDecimal price = PricingRules.money(unitPrice, "An extra's price");
        if (price == null) throw new IllegalArgumentException("An extra needs a price");
        return build(tenantId, lineId, EXTRA, description, quantity, price, false, order);
    }

    public static ComplianceQuoteAdjustment disbursement(TenantId tenantId, UUID lineId, String description, BigDecimal amount, boolean vatable, int order) {
        BigDecimal a = PricingRules.money(amount, "A disbursement");
        if (a == null) throw new IllegalArgumentException("A disbursement needs an amount");
        return build(tenantId, lineId, DISBURSEMENT, description, null, a, vatable, order);
    }

    private static ComplianceQuoteAdjustment build(TenantId tenantId, UUID lineId, String kind, String description, BigDecimal quantity, BigDecimal amount, boolean vatable, int order) {
        String d = ClientProfileRules.clean(description, 200);
        if (d == null || d.isBlank()) throw new IllegalArgumentException("Describe each extra and disbursement");
        ComplianceQuoteAdjustment a = new ComplianceQuoteAdjustment();
        a.tenantId = tenantId;
        a.lineId = lineId;
        a.kind = kind;
        a.description = d;
        a.quantity = quantity;
        a.amount = amount;
        a.vatable = vatable;
        a.sortOrder = order;
        return a;
    }
}
