package za.co.handyflow.platform.complianceservices.domain.model;

import jakarta.persistence.*;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;
import za.co.handyflow.platform.shared.TenantId;

import java.util.UUID;

/** One test on a client that decides whether an obligation version applies. All of a version's conditions must hold. */
@Entity
@Table(name = "compliance_obligation_conditions")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class ComplianceObligationCondition {

    @Id
    private UUID id = UUID.randomUUID();

    @Embedded
    @AttributeOverride(name = "value", column = @Column(name = "tenant_id", nullable = false))
    private TenantId tenantId;

    @Column(name = "version_id", nullable = false)
    private UUID versionId;

    @Column(name = "field_name", nullable = false)
    private String fieldName;

    @Column(nullable = false)
    private String operator;

    private String value;

    @Column(name = "sort_order", nullable = false)
    private int sortOrder;

    public static ComplianceObligationCondition of(TenantId tenantId, UUID versionId, ObligationRules.Condition c, int order) {
        ComplianceObligationCondition x = new ComplianceObligationCondition();
        x.tenantId = tenantId;
        x.versionId = versionId;
        x.fieldName = c.field();
        x.operator = c.operator();
        x.value = c.value();
        x.sortOrder = order;
        return x;
    }
}
