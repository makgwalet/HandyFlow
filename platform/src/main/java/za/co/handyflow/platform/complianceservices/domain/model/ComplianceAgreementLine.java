package za.co.handyflow.platform.complianceservices.domain.model;

import jakarta.persistence.*;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;
import za.co.handyflow.platform.shared.TenantId;

import java.math.BigDecimal;
import java.util.UUID;

/** One service in the scope of an agreement, copied from the accepted quote so later changes to the quote or catalogue do not alter it. */
@Entity
@Table(name = "compliance_agreement_lines")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class ComplianceAgreementLine {

    @Id
    private UUID id = UUID.randomUUID();

    @Embedded
    @AttributeOverride(name = "value", column = @Column(name = "tenant_id", nullable = false))
    private TenantId tenantId;

    @Column(name = "agreement_id", nullable = false)
    private UUID agreementId;

    @Column(name = "service_code", nullable = false)
    private String serviceCode;

    @Column(name = "service_name")
    private String serviceName;

    @Column(name = "service_version")
    private Integer serviceVersion;

    @Column(name = "pricing_model", nullable = false)
    private String pricingModel;

    private String description;

    @Column(nullable = false, precision = 14, scale = 2)
    private BigDecimal professional = BigDecimal.ZERO;

    @Column(name = "sort_order", nullable = false)
    private int sortOrder;

    public static ComplianceAgreementLine from(TenantId tenantId, UUID agreementId, ComplianceQuoteLine q) {
        ComplianceAgreementLine l = new ComplianceAgreementLine();
        l.tenantId = tenantId;
        l.agreementId = agreementId;
        l.serviceCode = q.getServiceCode();
        l.serviceName = q.getServiceName();
        l.serviceVersion = q.getServiceVersion();
        l.pricingModel = q.getPricingModel();
        l.description = q.getDescription();
        l.professional = q.getProfessional();
        l.sortOrder = q.getSortOrder();
        return l;
    }
}
