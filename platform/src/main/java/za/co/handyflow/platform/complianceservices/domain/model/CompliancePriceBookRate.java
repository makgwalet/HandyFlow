package za.co.handyflow.platform.complianceservices.domain.model;

import jakarta.persistence.*;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;
import za.co.handyflow.platform.shared.TenantId;

import java.math.BigDecimal;
import java.util.UUID;

/** The hourly rate for a grade of person. The cost rate is what an hour costs the practice and is shown only to administrators. */
@Entity
@Table(name = "compliance_price_book_rates")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class CompliancePriceBookRate {

    @Id
    private UUID id = UUID.randomUUID();

    @Embedded
    @AttributeOverride(name = "value", column = @Column(name = "tenant_id", nullable = false))
    private TenantId tenantId;

    @Column(name = "version_id", nullable = false)
    private UUID versionId;

    @Column(nullable = false)
    private String grade;

    @Column(name = "hourly_rate", nullable = false, precision = 12, scale = 2)
    private BigDecimal hourlyRate;

    @Column(name = "cost_rate", precision = 12, scale = 2)
    private BigDecimal costRate;

    @Column(name = "sort_order", nullable = false)
    private int sortOrder;

    public static CompliancePriceBookRate of(TenantId tenantId, UUID versionId, String grade, BigDecimal hourlyRate, BigDecimal costRate, int order) {
        if (hourlyRate == null) throw new IllegalArgumentException("An hourly rate is required for each grade");
        CompliancePriceBookRate r = new CompliancePriceBookRate();
        r.tenantId = tenantId;
        r.versionId = versionId;
        r.grade = PricingRules.grade(grade);
        r.hourlyRate = PricingRules.money(hourlyRate, "Hourly rate");
        r.costRate = PricingRules.money(costRate, "Cost rate");
        r.sortOrder = order;
        return r;
    }
}
