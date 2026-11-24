package za.co.handyflow.platform.complianceservices.domain.model;

import jakarta.persistence.*;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;
import za.co.handyflow.platform.shared.TenantId;

import java.math.BigDecimal;
import java.util.UUID;

/** What one service costs a client in a price book version. Amounts are ex VAT. */
@Entity
@Table(name = "compliance_price_book_lines")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class CompliancePriceBookLine {

    @Id
    private UUID id = UUID.randomUUID();

    @Embedded
    @AttributeOverride(name = "value", column = @Column(name = "tenant_id", nullable = false))
    private TenantId tenantId;

    @Column(name = "version_id", nullable = false)
    private UUID versionId;

    @Column(name = "service_code", nullable = false)
    private String serviceCode;

    @Column(name = "pricing_model", nullable = false)
    private String pricingModel;

    @Column(name = "base_fee", precision = 12, scale = 2)
    private BigDecimal baseFee;

    @Column(name = "unit_rate", precision = 12, scale = 2)
    private BigDecimal unitRate;

    @Column(name = "hourly_grade")
    private String hourlyGrade;

    @Column(name = "min_fee", precision = 12, scale = 2)
    private BigDecimal minFee;

    @Column(name = "urgent_surcharge_pct", precision = 5, scale = 2)
    private BigDecimal urgentSurchargePct;

    @Column(name = "tax_treatment", nullable = false)
    private String taxTreatment = "STANDARD";

    private String notes;

    @Column(name = "sort_order", nullable = false)
    private int sortOrder;

    /** The values of a line as typed. {@link #of} checks them. */
    public record Input(String serviceCode, String pricingModel, BigDecimal baseFee, BigDecimal unitRate, String hourlyGrade, BigDecimal minFee,
                        BigDecimal urgentSurchargePct, String taxTreatment, String notes) {}

    public static CompliancePriceBookLine of(TenantId tenantId, UUID versionId, Input in, int order) {
        CompliancePriceBookLine l = new CompliancePriceBookLine();
        l.tenantId = tenantId;
        l.versionId = versionId;
        l.serviceCode = ObligationRules.code(in.serviceCode());
        l.pricingModel = ClientProfileRules.requireOneOf("pricingModel", in.pricingModel(), ServiceCatalogueRules.PRICING_MODELS);
        if (l.pricingModel == null) throw new IllegalArgumentException("pricingModel is required");
        l.baseFee = PricingRules.money(in.baseFee(), "Base fee");
        l.unitRate = PricingRules.money(in.unitRate(), "Rate per unit");
        l.hourlyGrade = in.hourlyGrade() == null || in.hourlyGrade().isBlank() ? null : PricingRules.grade(in.hourlyGrade());
        l.minFee = PricingRules.money(in.minFee(), "Minimum fee");
        l.urgentSurchargePct = PricingRules.percent(in.urgentSurchargePct(), "Urgent surcharge", PricingRules.MAX_PERCENT);
        l.taxTreatment = PricingRules.taxTreatment(in.taxTreatment());
        l.notes = ClientProfileRules.clean(in.notes(), 500);
        l.sortOrder = order;
        return l;
    }

    /** What this line still needs for its model. Empty means it can price a quote. */
    public java.util.List<String> gaps() { return PricingRules.lineGaps(pricingModel, baseFee, unitRate, hourlyGrade); }
}
