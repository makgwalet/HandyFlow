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

    /** Free-text type chosen by the company (for example RIGID or ARTICULATED), upper case. Sites list the types they accept. Optional. */
    @Column(name = "tanker_type", length = 40)
    private String tankerType;

    @Column(name = "gvm_kg")
    private Integer gvmKg;

    @Column(name = "length_m", precision = 5, scale = 2)
    private java.math.BigDecimal lengthM;

    @Column(name = "width_m", precision = 5, scale = 2)
    private java.math.BigDecimal widthM;

    @Column(name = "height_m", precision = 5, scale = 2)
    private java.math.BigDecimal heightM;

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

    /** Optional specifications that site access rules are checked against. Blank or null clears one. */
    public void updateSpecs(String type, Integer gvmKg, java.math.BigDecimal lengthM, java.math.BigDecimal widthM, java.math.BigDecimal heightM) {
        if (gvmKg != null && gvmKg <= 0) throw new IllegalArgumentException("The gross vehicle mass must be more than zero.");
        for (java.math.BigDecimal d : new java.math.BigDecimal[]{lengthM, widthM, heightM}) {
            if (d != null && d.signum() <= 0) throw new IllegalArgumentException("A dimension must be more than zero.");
        }
        String t = type == null || type.isBlank() ? null : type.trim().toUpperCase();
        if (t != null && t.length() > 40) throw new IllegalArgumentException("The tanker type can be at most 40 characters.");
        this.tankerType = t; this.gvmKg = gvmKg; this.lengthM = lengthM; this.widthM = widthM; this.heightM = heightM;
        this.updatedAt = Instant.now();
    }

    public void deactivate() { this.active = false; this.deletedAt = Instant.now(); this.updatedAt = Instant.now(); }

    @PreUpdate
    void onUpdate() { this.updatedAt = Instant.now(); }
}
