package za.co.handyflow.platform.facilitiesmanagement.domain.model;

import jakarta.persistence.*;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;
import za.co.handyflow.platform.shared.TenantId;

import java.time.Instant;
import java.util.UUID;

/**
 * Record that a device operation was applied. The (tenant, user, opId) key is unique, so a retried upload of the
 * same operation is recognised and not applied twice — even if two retries race.
 */
@Entity
@Table(name = "fm_sync_ops")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class FmSyncOp {

    @Id
    private UUID id = UUID.randomUUID();

    @Embedded
    @AttributeOverride(name = "value", column = @Column(name = "tenant_id", nullable = false))
    private TenantId tenantId;

    @Column(name = "user_id", nullable = false)
    private UUID userId;

    @Column(name = "op_id", nullable = false)
    private UUID opId;

    @Column(name = "op_type", nullable = false, length = 40)
    private String opType;

    @Column(name = "target_id", nullable = false)
    private UUID targetId;

    @Column(name = "applied_at", nullable = false)
    private Instant appliedAt;

    public static FmSyncOp of(TenantId tenantId, UUID userId, UUID opId, String opType, UUID targetId) {
        FmSyncOp o = new FmSyncOp();
        o.tenantId = tenantId;
        o.userId = userId;
        o.opId = opId;
        o.opType = opType;
        o.targetId = targetId;
        o.appliedAt = Instant.now();
        return o;
    }
}
