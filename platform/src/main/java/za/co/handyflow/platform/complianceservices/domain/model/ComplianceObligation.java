package za.co.handyflow.platform.complianceservices.domain.model;

import jakarta.persistence.*;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;
import za.co.handyflow.platform.shared.TenantId;

import java.time.Instant;
import java.util.UUID;

/** An obligation in the catalogue. What it says lives in its numbered versions. */
@Entity
@Table(name = "compliance_obligations")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class ComplianceObligation {

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

    public static ComplianceObligation create(TenantId tenantId, String code, UUID by) {
        ComplianceObligation o = new ComplianceObligation();
        o.tenantId = tenantId;
        o.code = ObligationRules.code(code);
        o.createdAt = Instant.now();
        o.createdBy = by;
        return o;
    }
}
