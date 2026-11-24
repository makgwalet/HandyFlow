package za.co.handyflow.platform.complianceservices.domain.model;

import jakarta.persistence.*;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;
import za.co.handyflow.platform.shared.TenantId;

import java.util.UUID;

/** A kind of document that shows an obligation version is met. */
@Entity
@Table(name = "compliance_obligation_evidence")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class ComplianceObligationEvidence {

    @Id
    private UUID id = UUID.randomUUID();

    @Embedded
    @AttributeOverride(name = "value", column = @Column(name = "tenant_id", nullable = false))
    private TenantId tenantId;

    @Column(name = "version_id", nullable = false)
    private UUID versionId;

    @Column(name = "document_type", nullable = false)
    private String documentType;

    @Column(nullable = false)
    private boolean mandatory = true;

    @Column(name = "sort_order", nullable = false)
    private int sortOrder;

    public static ComplianceObligationEvidence of(TenantId tenantId, UUID versionId, ObligationRules.Evidence e, int order) {
        ComplianceObligationEvidence x = new ComplianceObligationEvidence();
        x.tenantId = tenantId;
        x.versionId = versionId;
        x.documentType = e.documentType();
        x.mandatory = e.mandatory();
        x.sortOrder = order;
        return x;
    }
}
