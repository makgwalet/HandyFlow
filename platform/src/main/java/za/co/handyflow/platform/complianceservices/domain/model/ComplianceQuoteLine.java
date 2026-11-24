package za.co.handyflow.platform.complianceservices.domain.model;

import jakarta.persistence.*;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;
import za.co.handyflow.platform.shared.TenantId;

import java.math.BigDecimal;
import java.util.UUID;

/** One service on a quote, with the inputs it was priced from and the figures the server worked out. */
@Entity
@Table(name = "compliance_quote_lines")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class ComplianceQuoteLine {

    @Id
    private UUID id = UUID.randomUUID();

    @Embedded
    @AttributeOverride(name = "value", column = @Column(name = "tenant_id", nullable = false))
    private TenantId tenantId;

    @Column(name = "quote_id", nullable = false)
    private UUID quoteId;

    @Column(name = "service_code", nullable = false)
    private String serviceCode;

    @Column(name = "service_name")
    private String serviceName;

    @Column(name = "service_version")
    private Integer serviceVersion;

    @Column(name = "pricing_model", nullable = false)
    private String pricingModel;

    private String description;

    @Column(precision = 10, scale = 2)
    private BigDecimal units;

    @Column(precision = 8, scale = 2)
    private BigDecimal hours;

    @Column(name = "complexity_pct", precision = 5, scale = 2)
    private BigDecimal complexityPct;

    @Column(nullable = false)
    private boolean urgent;

    @Column(name = "discount_pct", nullable = false, precision = 5, scale = 2)
    private BigDecimal discountPct = BigDecimal.ZERO;

    @Column(name = "before_discount", nullable = false, precision = 14, scale = 2)
    private BigDecimal beforeDiscount = BigDecimal.ZERO;

    @Column(nullable = false, precision = 14, scale = 2)
    private BigDecimal discount = BigDecimal.ZERO;

    @Column(nullable = false, precision = 14, scale = 2)
    private BigDecimal professional = BigDecimal.ZERO;

    @Column(nullable = false, precision = 14, scale = 2)
    private BigDecimal disbursements = BigDecimal.ZERO;

    @Column(nullable = false, precision = 14, scale = 2)
    private BigDecimal vat = BigDecimal.ZERO;

    @Column(nullable = false, precision = 14, scale = 2)
    private BigDecimal total = BigDecimal.ZERO;

    @Column(columnDefinition = "TEXT")
    private String breakdown;

    @Column(name = "sort_order", nullable = false)
    private int sortOrder;

    /** What was asked for on the line, before pricing. */
    public record Input(String serviceCode, String serviceName, Integer serviceVersion, String pricingModel, String description, BigDecimal units,
                        BigDecimal hours, BigDecimal complexityPct, boolean urgent, BigDecimal discountPct) {}

    public static ComplianceQuoteLine of(TenantId tenantId, UUID quoteId, Input in, int order) {
        ComplianceQuoteLine l = new ComplianceQuoteLine();
        l.tenantId = tenantId;
        l.quoteId = quoteId;
        l.serviceCode = ObligationRules.code(in.serviceCode());
        l.serviceName = in.serviceName();
        l.serviceVersion = in.serviceVersion();
        l.pricingModel = in.pricingModel();
        l.description = ClientProfileRules.clean(in.description(), 500);
        l.units = in.units();
        l.hours = in.hours();
        l.complexityPct = PricingRules.percent(in.complexityPct(), "Complexity", PricingRules.MAX_PERCENT);
        l.urgent = in.urgent();
        l.discountPct = in.discountPct() == null ? BigDecimal.ZERO : PricingRules.percent(in.discountPct(), "Discount", new BigDecimal("100"));
        l.sortOrder = order;
        return l;
    }

    /** Takes the server's figures for this line. */
    public void priced(BigDecimal beforeDiscount, BigDecimal discount, BigDecimal professional, BigDecimal disbursements, BigDecimal vat, BigDecimal total, String breakdown) {
        this.beforeDiscount = beforeDiscount;
        this.discount = discount;
        this.professional = professional;
        this.disbursements = disbursements;
        this.vat = vat;
        this.total = total;
        this.breakdown = breakdown;
    }
}
