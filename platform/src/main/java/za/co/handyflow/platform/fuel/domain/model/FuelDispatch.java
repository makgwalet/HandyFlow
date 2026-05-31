// fuel/domain/model/FuelDispatch.java

package za.co.handyflow.platform.fuel.domain.model;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.NoArgsConstructor;
import za.co.handyflow.platform.shared.TenantId;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "fuel_dispatches")
@Getter
@NoArgsConstructor(access = lombok.AccessLevel.PROTECTED)
public class FuelDispatch {

    @Id
    private UUID id = UUID.randomUUID();

    @Embedded
    @AttributeOverride(name = "value",
            column = @Column(name = "tenant_id", nullable = false))
    private TenantId tenantId;

    @Column(name = "tank_id", nullable = false)
    private UUID tankId;

    @Column(name = "vehicle_id")
    private UUID vehicleId;

    @Column(name = "asset_id")
    private UUID assetId;

    @Column(name = "customer_id")
    private UUID customerId;

    @Column(name = "recipient_name")
    private String recipientName;

    @Column(name = "litres_dispensed", nullable = false, precision = 12, scale = 2)
    private BigDecimal litresDispensed;

    @Column(name = "price_per_litre", precision = 10, scale = 4)
    private BigDecimal pricePerLitre;

    @Column(name = "dispatched_at", nullable = false)
    private Instant dispatchedAt;

    @Column(name = "odometer_reading")
    private Integer odometerReading;

    @Column(name = "hours_reading", precision = 10, scale = 1)
    private BigDecimal hoursReading;

    @Column(name = "authorised_by")
    private String authorisedBy;

    private String notes;

    @Column(name = "level_before", precision = 12, scale = 2)
    private BigDecimal levelBefore;

    @Column(name = "level_after", precision = 12, scale = 2)
    private BigDecimal levelAfter;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;

    @Column(name = "deleted_at")
    private Instant deletedAt;

    @Version
    private Long version;

    public static FuelDispatch create(TenantId tenantId, UUID tankId,
                                      UUID vehicleId, UUID assetId, UUID customerId,
                                      String recipientName, BigDecimal litresDispensed,
                                      BigDecimal pricePerLitre, Instant dispatchedAt,
                                      Integer odometerReading, BigDecimal hoursReading,
                                      String authorisedBy, String notes,
                                      BigDecimal levelBefore, BigDecimal levelAfter) {
        FuelDispatch d = new FuelDispatch();
        d.tenantId        = tenantId;
        d.tankId          = tankId;
        d.vehicleId       = vehicleId;
        d.assetId         = assetId;
        d.customerId      = customerId;
        d.recipientName   = recipientName;
        d.litresDispensed = litresDispensed;
        d.pricePerLitre   = pricePerLitre;
        d.dispatchedAt    = dispatchedAt;
        d.odometerReading = odometerReading;
        d.hoursReading    = hoursReading;
        d.authorisedBy    = authorisedBy;
        d.notes           = notes;
        d.levelBefore     = levelBefore;
        d.levelAfter      = levelAfter;
        d.createdAt       = Instant.now();
        d.updatedAt       = Instant.now();
        return d;
    }

    public boolean isDeleted() { return deletedAt != null; }

    @PreUpdate
    void onUpdate() { this.updatedAt = Instant.now(); }
}