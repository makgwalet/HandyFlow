package za.co.handyflow.platform.fuel.domain.model;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.NoArgsConstructor;
import za.co.handyflow.platform.shared.TenantId;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.Instant;
import java.util.UUID;

/** The company's own overnight parking settings: the nightly rate and the number of bays. Nothing is assumed; both start empty. */
@Entity
@Table(name = "fuel_truck_stop_parking_settings")
@Getter
@NoArgsConstructor(access = lombok.AccessLevel.PROTECTED)
public class FuelTruckStopParking {

    @Id
    private UUID id = UUID.randomUUID();

    @Embedded
    @AttributeOverride(name = "value", column = @Column(name = "tenant_id", nullable = false))
    private TenantId tenantId;

    @Column(name = "nightly_rate")
    private BigDecimal nightlyRate;

    /** Bays available for overnight parking; empty means no limit is enforced. */
    private Integer bays;

    @Column(name = "updated_by")
    private UUID updatedBy;

    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt = Instant.now();

    @Version
    private Long version;

    public static FuelTruckStopParking forTenant(TenantId tenantId) {
        FuelTruckStopParking p = new FuelTruckStopParking();
        p.tenantId = tenantId;
        return p;
    }

    public void update(BigDecimal nightlyRate, Integer bays, UUID by) {
        if (nightlyRate != null && nightlyRate.signum() < 0) throw new IllegalArgumentException("The nightly rate cannot be negative.");
        if (bays != null && bays <= 0) throw new IllegalArgumentException("The number of bays must be more than zero, or left empty for no limit.");
        this.nightlyRate = nightlyRate == null ? null : nightlyRate.setScale(2, RoundingMode.HALF_UP);
        this.bays = bays; this.updatedBy = by; this.updatedAt = Instant.now();
    }
}
