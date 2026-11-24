package za.co.handyflow.platform.complianceservices.domain.model;

import jakarta.persistence.*;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;
import za.co.handyflow.platform.shared.TenantId;

import java.util.UUID;

/** A deliverable, checklist step or required document of a service version. */
@Entity
@Table(name = "compliance_service_items")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class ComplianceServiceItem {

    @Id
    private UUID id = UUID.randomUUID();

    @Embedded
    @AttributeOverride(name = "value", column = @Column(name = "tenant_id", nullable = false))
    private TenantId tenantId;

    @Column(name = "version_id", nullable = false)
    private UUID versionId;

    @Column(nullable = false)
    private String kind;

    @Column(nullable = false)
    private String text;

    @Column(nullable = false)
    private boolean mandatory = true;

    @Column(name = "sort_order", nullable = false)
    private int sortOrder;

    public static ComplianceServiceItem of(TenantId tenantId, UUID versionId, ServiceCatalogueRules.Item e, int order) {
        ComplianceServiceItem x = new ComplianceServiceItem();
        x.tenantId = tenantId;
        x.versionId = versionId;
        x.kind = e.kind();
        x.text = e.text();
        x.mandatory = e.mandatory();
        x.sortOrder = order;
        return x;
    }
}
