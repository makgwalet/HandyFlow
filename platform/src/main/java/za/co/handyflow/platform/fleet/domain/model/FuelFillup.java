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
}
