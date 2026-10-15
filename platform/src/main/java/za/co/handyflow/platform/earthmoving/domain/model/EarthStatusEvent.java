package za.co.handyflow.platform.earthmoving.domain.model;

import jakarta.persistence.*;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;
import za.co.handyflow.platform.shared.TenantId;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

/** One status change on a machine: from, to, why, who, and the hour reading at the time. Append-only. */
@Entity
@Table(name = "earthmoving_status_events")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class EarthStatusEvent {

    @Id
    private UUID id = UUID.randomUUID();

    @Embedded
    @AttributeOverride(name = "value", column = @Column(name = "tenant_id", nullable = false))
    private TenantId tenantId;

    @Column(name = "asset_id", nullable = false)
    private UUID assetId;

    @Column(name = "from_status")
    private String fromStatus;

    @Column(name = "to_status", nullable = false)
    private String toStatus;

    private String reason;

    @Column(name = "actor_user_id")
    private UUID actorUserId;

    @Column(name = "meter_hours", precision = 10, scale = 1)
    private BigDecimal meterHours;

    @Column(name = "happened_at", nullable = false)
    private Instant happenedAt;

    public static EarthStatusEvent of(TenantId tenantId, UUID assetId, AssetStatus from, AssetStatus to,
                                      String reason, UUID actor, BigDecimal meterHours) {
        EarthStatusEvent e = new EarthStatusEvent();
        e.tenantId = tenantId;
        e.assetId = assetId;
        e.fromStatus = from == null ? null : from.name();
        e.toStatus = to.name();
        e.reason = reason;
        e.actorUserId = actor;
        e.meterHours = meterHours;
        e.happenedAt = Instant.now();
        return e;
    }
}
