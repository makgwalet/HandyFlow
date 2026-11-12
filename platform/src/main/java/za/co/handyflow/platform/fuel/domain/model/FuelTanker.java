package za.co.handyflow.platform.fuel.domain.model;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.NoArgsConstructor;
import za.co.handyflow.platform.shared.TenantId;

import java.time.Instant;
import java.util.UUID;

/** A road tanker. Its compartments are separate rows and cannot change once created (deactivate and recreate instead). */
@Entity
@Table(name = "fuel_tankers")
@Getter
@NoArgsConstructor(access = lombok.AccessLevel.PROTECTED)
public class FuelTanker {

    @Id
    private UUID id = UUID.randomUUID();

    @Embedded
    @AttributeOverride(name = "value", column = @Column(name = "tenant_id", nullable = false))
    private TenantId tenantId;

    @Column(nullable = false, length = 20)
    private String registration;

    @Column(length = 80)
    private String name;

    @Column(nullable = false)
    private boolean active = true;

    /** The vehicle in Fleet this tanker is, when linked. Fleet owns the vehicle; Fuel only points at it. */
    @Column(name = "fleet_vehicle_id")
    private UUID fleetVehicleId;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;

    @Column(name = "deleted_at")
    private Instant deletedAt;

    @Version
    private Long version;

    public static FuelTanker create(TenantId tenantId, String registration, String name) {
        FuelTanker t = new FuelTanker();
        t.tenantId = tenantId; t.createdAt = Instant.now();
        t.update(registration, name);
        return t;
    }

    public void update(String registration, String name) {
        if (registration == null || registration.isBlank()) throw new IllegalArgumentException("Enter the tanker's registration.");
        this.registration = registration.trim().toUpperCase();
        this.name = name == null || name.isBlank() ? null : name.trim();
        this.updatedAt = Instant.now();
    }

    /** Links to (or, with null, unlinks from) a Fleet vehicle. The registration then follows Fleet; the caller supplies it. */
    public void linkFleetVehicle(UUID vehicleId, String fleetRegistration) {
        this.fleetVehicleId = vehicleId;
        if (vehicleId != null) update(fleetRegistration, this.name);
        else this.updatedAt = Instant.now();
    }

    public void deactivate() { this.active = false; this.deletedAt = Instant.now(); this.updatedAt = Instant.now(); }

    @PreUpdate
    void onUpdate() { this.updatedAt = Instant.now(); }
}
