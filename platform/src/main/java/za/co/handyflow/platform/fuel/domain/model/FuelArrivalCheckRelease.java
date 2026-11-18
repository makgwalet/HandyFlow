package za.co.handyflow.platform.fuel.domain.model;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.NoArgsConstructor;
import za.co.handyflow.platform.shared.TenantId;

import java.time.Instant;
import java.util.UUID;

/** An authorised person's release of one stale arrival check. The check itself is never changed; this sits beside it. */
@Entity
@Table(name = "fuel_arrival_check_releases")
@Getter
@NoArgsConstructor(access = lombok.AccessLevel.PROTECTED)
public class FuelArrivalCheckRelease {

    @Id
    private UUID id = UUID.randomUUID();

    @Embedded
    @AttributeOverride(name = "value", column = @Column(name = "tenant_id", nullable = false))
    private TenantId tenantId;

    @Column(name = "check_id", nullable = false, updatable = false)
    private UUID checkId;

    @Column(name = "delivery_id", nullable = false, updatable = false)
    private UUID deliveryId;

    @Column(name = "reason_code", nullable = false, updatable = false, length = 40)
    private String reasonCode;

    @Column(nullable = false, updatable = false, length = 1000)
    private String explanation;

    @Column(name = "released_by", nullable = false, updatable = false)
    private UUID releasedBy;

    @Column(name = "released_at", nullable = false, updatable = false)
    private Instant releasedAt;

    public static FuelArrivalCheckRelease record(TenantId tenantId, UUID checkId, UUID deliveryId, String reasonCode, String explanation, UUID by, Instant at) {
        FuelArrivalCheckRelease r = new FuelArrivalCheckRelease();
        r.tenantId = tenantId; r.checkId = checkId; r.deliveryId = deliveryId; r.reasonCode = reasonCode;
        r.explanation = explanation; r.releasedBy = by; r.releasedAt = at;
        return r;
    }
}
