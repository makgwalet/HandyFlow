package za.co.handyflow.platform.fleet.domain.model;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.NoArgsConstructor;
import za.co.handyflow.platform.shared.TenantId;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.UUID;

/** A completed retread: who did it, what it cost, and the new tread depth. Part of the tyre's lifetime cost. */
@Entity
@Table(name = "fleet_tyre_retreads")
@Getter
@NoArgsConstructor(access = lombok.AccessLevel.PROTECTED)
public class TyreRetread {

    @Id
    private UUID id = UUID.randomUUID();

    @Embedded
    @AttributeOverride(name = "value", column = @Column(name = "tenant_id", nullable = false))
    private TenantId tenantId;

    @Column(name = "tyre_id", nullable = false)
    private UUID tyreId;

    @Column(name = "retreaded_at", nullable = false)
    private LocalDate retreadedAt;

    private String supplier;

    @Column(precision = 15, scale = 2)
    private BigDecimal cost;

    @Column(name = "new_tread_depth_mm", nullable = false, precision = 4, scale = 1)
    private BigDecimal newTreadDepthMm;

    @Column(length = 500)
    private String notes;

    @Column(name = "recorded_by")
    private UUID recordedBy;

    public static TyreRetread record(TenantId tenantId, UUID tyreId, LocalDate retreadedAt, String supplier,
                                     BigDecimal cost, BigDecimal newTreadDepthMm, String notes, UUID recordedBy) {
        TyreRetread r = new TyreRetread();
        r.tenantId = tenantId;
        r.tyreId = tyreId;
        r.retreadedAt = retreadedAt;
        r.supplier = supplier;
        r.cost = cost;
        r.newTreadDepthMm = newTreadDepthMm;
        r.notes = notes;
        r.recordedBy = recordedBy;
        return r;
    }
}