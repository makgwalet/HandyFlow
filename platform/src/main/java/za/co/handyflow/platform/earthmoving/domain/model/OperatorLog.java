// earthmoving/domain/model/OperatorLog.java

package za.co.handyflow.platform.earthmoving.domain.model;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.NoArgsConstructor;
import za.co.handyflow.platform.shared.TenantId;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "earthmoving_operator_logs")
@Getter
@NoArgsConstructor(access = lombok.AccessLevel.PROTECTED)
public class OperatorLog {

    @Id
    private UUID id = UUID.randomUUID();

    @Embedded
    @AttributeOverride(name = "value",
            column = @Column(name = "tenant_id", nullable = false))
    private TenantId tenantId;

    @Column(name = "asset_id", nullable = false)
    private UUID assetId;

    @Column(name = "guard_id")
    private UUID guardId;

    @Column(name = "operator_name")
    private String operatorName;

    @Column(name = "site_name")
    private String siteName;

    @Column(name = "started_at", nullable = false)
    private Instant startedAt;

    @Column(name = "ended_at")
    private Instant endedAt;

    @Column(name = "hours_logged", precision = 10, scale = 2)
    private BigDecimal hoursLogged;

    @Column(name = "fuel_used_litres", precision = 10, scale = 2)
    private BigDecimal fuelUsedLitres;

    @Column(name = "start_hours", precision = 10, scale = 1)
    private BigDecimal startHours;

    @Column(name = "end_hours", precision = 10, scale = 1)
    private BigDecimal endHours;

    private String notes;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    public static OperatorLog create(TenantId tenantId, UUID assetId,
                                     UUID guardId, String operatorName,
                                     String siteName, Instant startedAt,
                                     BigDecimal startHours) {
        OperatorLog l = new OperatorLog();
        l.tenantId     = tenantId;
        l.assetId      = assetId;
        l.guardId      = guardId;
        l.operatorName = operatorName;
        l.siteName     = siteName;
        l.startedAt    = startedAt;
        l.startHours   = startHours;
        l.createdAt    = Instant.now();
        return l;
    }

    public void complete(Instant endedAt, BigDecimal endHours,
                         BigDecimal fuelUsedLitres, String notes) {
        this.endedAt        = endedAt;
        this.endHours       = endHours;
        this.fuelUsedLitres = fuelUsedLitres;
        this.notes          = notes;
        if (endHours != null && startHours != null) {
            this.hoursLogged = endHours.subtract(startHours);
        }
    }
}