package za.co.handyflow.platform.complianceservices.domain.model;

import jakarta.persistence.*;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;
import za.co.handyflow.platform.shared.TenantId;

import java.time.Instant;
import java.time.LocalDate;
import java.util.UUID;

/** A catalogue obligation a change touches. */
@Entity(name = "ComplianceRegChangeObligation")
@Table(name = "compliance_reg_change_obligations")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class ComplianceRegChangeObligation {
    @Id
    private UUID id = UUID.randomUUID();
    @Embedded
    @AttributeOverride(name = "value", column = @Column(name = "tenant_id", nullable = false))
    private TenantId tenantId;
    @Column(name = "change_id", nullable = false)
    private UUID changeId;
    @Column(name = "obligation_id", nullable = false)
    private UUID obligationId;

    public static ComplianceRegChangeObligation link(TenantId tenantId, UUID changeId, UUID obligationId) {
        ComplianceRegChangeObligation l = new ComplianceRegChangeObligation();
        l.tenantId = tenantId;
        l.changeId = changeId;
        l.obligationId = obligationId;
        return l;
    }
}
