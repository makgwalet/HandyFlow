package za.co.handyflow.platform.fuel.domain.model;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.NoArgsConstructor;
import za.co.handyflow.platform.shared.TenantId;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

/** A forecourt pump. It draws from one tank and has one pump price; last_meter is where the next shift should start. */
@Entity
@Table(name = "fuel_pumps")
@Getter
@NoArgsConstructor(access = lombok.AccessLevel.PROTECTED)
public class FuelPump {

    @Id
    private UUID id = UUID.randomUUID();

    @Embedded
    @AttributeOverride(name = "value", column = @Column(name = "tenant_id", nullable = false))
    private TenantId tenantId;

    @Column(nullable = false, length = 40)
    private String name;

    @Column(name = "tank_id", nullable = false)
    private UUID tankId;

    @Column(name = "price_per_litre", nullable = false, precision = 10, scale = 4)
    private BigDecimal pricePerLitre;

    /** Optional: the physical dispenser this nozzle belongs to, and its number on it. */
    @Column(name = "dispenser_label", length = 40)
    private String dispenserLabel;

    @Column(name = "nozzle_number")
    private Integer nozzleNumber;

    @Column(name = "last_meter", precision = 14, scale = 2)
    private BigDecimal lastMeter;

    @Column(nullable = false)
    private boolean active = true;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;

    @Column(name = "deleted_at")
    private Instant deletedAt;

    @Version
    private Long version;

    public static FuelPump create(TenantId tenantId, String name, UUID tankId, BigDecimal pricePerLitre) {
        requirePrice(pricePerLitre);
        if (name == null || name.isBlank()) throw new IllegalArgumentException("Give the pump a name.");
        FuelPump p = new FuelPump();
        p.tenantId = tenantId;
        p.name = name.trim();
        p.tankId = tankId;
        p.pricePerLitre = pricePerLitre;
        p.createdAt = Instant.now();
        p.updatedAt = Instant.now();
        return p;
    }

    public void update(String name, UUID tankId, BigDecimal pricePerLitre, boolean active) {
        requirePrice(pricePerLitre);
        if (name == null || name.isBlank()) throw new IllegalArgumentException("Give the pump a name.");
        this.name = name.trim();
        this.tankId = tankId;
        this.pricePerLitre = pricePerLitre;
        this.active = active;
        this.updatedAt = Instant.now();
    }

    /** Both blank means the pump is not grouped. A nozzle number needs a dispenser to belong to. */
    public void assignNozzle(String dispenserLabel, Integer nozzleNumber) {
        String label = dispenserLabel == null || dispenserLabel.isBlank() ? null : dispenserLabel.trim();
        if (nozzleNumber != null && (nozzleNumber < 1 || nozzleNumber > 20)) throw new IllegalArgumentException("The nozzle number must be between 1 and 20.");
        if (nozzleNumber != null && label == null) throw new IllegalArgumentException("Name the dispenser this nozzle is on.");
        this.dispenserLabel = label; this.nozzleNumber = label == null ? null : nozzleNumber;
        this.updatedAt = Instant.now();
    }

    public void recordMeter(BigDecimal meter) {
        this.lastMeter = meter;
        this.updatedAt = Instant.now();
    }

    public void softDelete() { this.deletedAt = Instant.now(); this.active = false; this.updatedAt = Instant.now(); }

    private static void requirePrice(BigDecimal p) {
        if (p == null || p.signum() <= 0) throw new IllegalArgumentException("The pump price must be greater than zero.");
    }
}
