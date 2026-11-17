package za.co.handyflow.platform.fuel.domain.model;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.NoArgsConstructor;
import za.co.handyflow.platform.shared.TenantId;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

/** The percentage of a forecourt's supply attributed to one supplying depot. A depot's rows are replaced as a set and must total 100. */
@Entity
@Table(name = "fuel_depot_supply_shares")
@Getter
@NoArgsConstructor(access = lombok.AccessLevel.PROTECTED)
public class FuelDepotSupplyShare {

    @Id
    private UUID id = UUID.randomUUID();

    @Embedded
    @AttributeOverride(name = "value", column = @Column(name = "tenant_id", nullable = false))
    private TenantId tenantId;

    @Column(name = "depot_id", nullable = false, updatable = false)
    private UUID depotId;

    @Column(name = "supplying_depot_id", nullable = false, updatable = false)
    private UUID supplyingDepotId;

    @Column(nullable = false, updatable = false, precision = 5, scale = 2)
    private BigDecimal percentage;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt = Instant.now();

    public static FuelDepotSupplyShare of(TenantId tenantId, UUID depotId, UUID supplyingDepotId, BigDecimal percentage) {
        if (depotId.equals(supplyingDepotId)) throw new IllegalArgumentException("A depot cannot supply itself.");
        if (percentage == null || percentage.signum() <= 0 || percentage.compareTo(BigDecimal.valueOf(100)) > 0)
            throw new IllegalArgumentException("Each percentage must be above 0 and at most 100.");
        FuelDepotSupplyShare s = new FuelDepotSupplyShare();
        s.tenantId = tenantId; s.depotId = depotId; s.supplyingDepotId = supplyingDepotId; s.percentage = percentage.setScale(2, java.math.RoundingMode.HALF_UP);
        return s;
    }
}
