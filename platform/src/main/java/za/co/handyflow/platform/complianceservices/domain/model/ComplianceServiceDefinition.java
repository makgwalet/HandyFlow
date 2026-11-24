package za.co.handyflow.platform.complianceservices.domain.model;

import jakarta.persistence.*;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;
import za.co.handyflow.platform.shared.TenantId;

import java.time.Instant;
import java.util.UUID;

/** A service in the practice's catalogue. What it says lives in its numbered versions. */
@Entity
@Table(name = "compliance_service_definitions")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class ComplianceServiceDefinition {

    @Id
    private UUID id = UUID.randomUUID();

    @Embedded
    @AttributeOverride(name = "value", column = @Column(name = "tenant_id", nullable = false))
    private TenantId tenantId;

    @Column(nullable = false)
    private String code;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    @Column(name = "created_by")
    private UUID createdBy;

    @Version
    private Long version;

    public static ComplianceServiceDefinition create(TenantId tenantId, String code, UUID by) {
        ComplianceServiceDefinition o = new ComplianceServiceDefinition();
        o.tenantId = tenantId;
        o.code = ObligationRules.code(code);
        o.createdAt = Instant.now();
        o.createdBy = by;
        return o;
    }
}
