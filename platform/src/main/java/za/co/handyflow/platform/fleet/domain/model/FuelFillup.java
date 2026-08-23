package za.co.handyflow.platform.fleet.domain.model;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.NoArgsConstructor;
import za.co.handyflow.platform.shared.TenantId;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.util.UUID;

@Entity
@Table(name = "fleet_fuel_fillups")
@Getter
@NoArgsConstructor
public class FuelFillup {

    @Id
    private UUID id;

    @Column(name = "tenant_id", nullable = false)
    private UUID tenantId;

    @Column(name = "vehicle_id", nullable = false)
    private UUID vehicleId;

    @Column(name = "filled_at", nullable = false)
    private LocalDate filledAt;

    @Column(name = "litres", nullable = false, precision = 10, scale = 2)
    private BigDecimal litres;

    @Column(name = "price_per_litre", precision = 10, scale = 3)
    private BigDecimal pricePerLitre;

    @Column(name = "total_cost", precision = 15, scale = 2)
    private BigDecimal totalCost;

    @Column(name = "odometer_at_fillup")
    private Integer odometerAtFillup;

    @Column(name = "station", length = 255)
    private String station;

    @Column(name = "receipt_ref", length = 100)
    private String receiptRef;

    // FIX: backlog 5.1 — nullable, unique. NULL for every fillup logged
    // manually (unchanged, existing behaviour). Populated only for
    // fillups created from a Fuel-module tank dispatch — see
    // createFromFuelDispatch() below. The DB-level UNIQUE constraint is
    // what makes FleetFuelDispatchEventHandler idempotent against
    // duplicate event delivery.
    @Column(name = "source_fuel_dispatch_id", unique = true)
    private UUID sourceFuelDispatchId;

    @Column(name = "full_tank", nullable = false)
    private boolean fullTank = true;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    public static FuelFillup create(TenantId tenantId, UUID vehicleId,
                                    LocalDate filledAt, BigDecimal litres,
                                    BigDecimal pricePerLitre, BigDecimal totalCost,
                                    Integer odometerAtFillup, String station,
                                    String receiptRef, boolean fullTank) {
        FuelFillup f = new FuelFillup();
        f.id               = UUID.randomUUID();
        f.tenantId         = tenantId.getValue();
        f.vehicleId        = vehicleId;
        f.filledAt         = filledAt;
        f.litres           = litres;
        f.pricePerLitre    = pricePerLitre;
        f.totalCost        = totalCost;
        f.odometerAtFillup = odometerAtFillup;
        f.station          = station;
        f.receiptRef       = receiptRef;
        f.fullTank         = fullTank;
        f.createdAt        = Instant.now();
        return f;
    }

    /**
     * FIX: backlog 5.1 — creates a FuelFillup from a Fuel-module tank
     * dispatch rather than a manual log entry. fullTank defaults to
     * false — a company tank dispatch to a vehicle isn't necessarily a
     * "filled to full" event the way a manual station fill-up is, and
     * false is the safer default for anything that treats fullTank
     * specially in consumption calculations. station is a fixed,
     * descriptive label rather than null, so the fleet fuel log UI
     * shows something meaningful instead of a blank field.
     */
    public static FuelFillup createFromFuelDispatch(TenantId tenantId, UUID vehicleId,
                                                    UUID sourceFuelDispatchId,
                                                    Instant dispatchedAt, BigDecimal litres,
                                                    BigDecimal pricePerLitre, BigDecimal totalCost,
                                                    Integer odometerAtFillup) {
        FuelFillup f            = new FuelFillup();
        f.id                    = UUID.randomUUID();
        f.tenantId              = tenantId.getValue();
        f.vehicleId             = vehicleId;
        f.sourceFuelDispatchId  = sourceFuelDispatchId;
        f.filledAt              = dispatchedAt.atZone(java.time.ZoneOffset.UTC).toLocalDate();
        f.litres                = litres;
        f.pricePerLitre         = pricePerLitre;
        f.totalCost             = totalCost;
        f.odometerAtFillup      = odometerAtFillup;
        f.station               = "Internal tank dispatch";
        f.fullTank              = false;
        f.createdAt             = Instant.now();
        return f;
    }
}
