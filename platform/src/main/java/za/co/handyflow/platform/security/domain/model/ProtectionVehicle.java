// security/domain/model/ProtectionVehicle.java

package za.co.handyflow.platform.security.domain.model;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.NoArgsConstructor;
import za.co.handyflow.platform.shared.TenantId;

import java.time.Instant;
import java.util.UUID;

/**
 * ProtectionVehicle — a convoy vehicle the company owns or operates for
 * close protection details.
 *
 * Same asset-registry pattern as Armoury (Phase 3) — this is the vehicle's
 * persistent record (registration, type, armored flag, current status)
 * distinct from any one checkout event. Actual checkout/return for a
 * specific shift goes through the existing security_resource_custody table
 * (Phase 2) with resource_type='VEHICLE' and resourceId pointing here —
 * this entity doesn't duplicate that checkout/witness/condition-notes flow,
 * it's purely the asset record.
 *
 * Three types:
 *   PRINCIPAL_CAR — the vehicle the principal actually rides in
 *   LEAD_CAR      — precedes the principal car, scouts the route ahead
 *   FOLLOW_CAR    — follows behind, covers the rear
 *
 * assignedDriverGuardId is a denormalized "who's driving it right now"
 * convenience, same pattern as Armoury.assignedGuardId — the source of
 * truth for the full driving history is DetailAssignment (role=DRIVER,
 * vehicleId set) plus resource_custody checkout logs, not this field alone.
 */
@Entity
@Table(name = "security_protection_vehicles")
@Getter
@NoArgsConstructor(access = lombok.AccessLevel.PROTECTED)
public class ProtectionVehicle {

    @Id
    private UUID id = UUID.randomUUID();

    @Embedded
    @AttributeOverride(name = "value",
            column = @Column(name = "tenant_id", nullable = false))
    private TenantId tenantId;

    @Enumerated(EnumType.STRING)
    @Column(name = "vehicle_type", nullable = false, length = 20)
    private VehicleType vehicleType;

    @Column(nullable = false, length = 20)
    private String registration;

    @Column(name = "make_model", length = 150)
    private String makeModel;

    @Column(nullable = false)
    private boolean armored = false;

    @Column(name = "assigned_driver_guard_id")
    private UUID assignedDriverGuardId;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private VehicleStatus status = VehicleStatus.AVAILABLE;

    @Column
    private String notes;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;

    // ── Factory ────────────────────────────────────────────────────────────────

    public static ProtectionVehicle register(TenantId tenantId, VehicleType vehicleType,
                                             String registration, String makeModel,
                                             boolean armored, String notes) {
        ProtectionVehicle v   = new ProtectionVehicle();
        v.tenantId            = tenantId;
        v.vehicleType         = vehicleType;
        v.registration        = registration.strip().toUpperCase();
        v.makeModel           = makeModel;
        v.armored             = armored;
        v.status              = VehicleStatus.AVAILABLE;
        v.notes               = notes;
        v.createdAt           = Instant.now();
        v.updatedAt           = Instant.now();
        return v;
    }

    // ── Mutations ──────────────────────────────────────────────────────────────

    public void assignDriver(UUID guardId) {
        if (this.status != VehicleStatus.AVAILABLE) {
            throw new IllegalStateException(
                    "Cannot assign a driver — vehicle is " + this.status);
        }
        this.assignedDriverGuardId = guardId;
        this.status                = VehicleStatus.IN_USE;
        this.updatedAt             = Instant.now();
    }

    public void releaseDriver() {
        this.assignedDriverGuardId = null;
        this.status                = VehicleStatus.AVAILABLE;
        this.updatedAt             = Instant.now();
    }

    public void sendForService(String notes) {
        if (this.status == VehicleStatus.IN_USE) {
            throw new IllegalStateException("Cannot service a vehicle currently IN_USE — release driver first");
        }
        this.status    = VehicleStatus.IN_SERVICE;
        this.notes     = notes;
        this.updatedAt = Instant.now();
    }

    public void returnFromService() {
        if (this.status != VehicleStatus.IN_SERVICE) {
            throw new IllegalStateException("Vehicle is not currently IN_SERVICE");
        }
        this.status    = VehicleStatus.AVAILABLE;
        this.updatedAt = Instant.now();
    }

    public void decommission() {
        if (this.status == VehicleStatus.IN_USE) {
            throw new IllegalStateException("Cannot decommission a vehicle currently IN_USE");
        }
        this.status    = VehicleStatus.DECOMMISSIONED;
        this.updatedAt = Instant.now();
    }

    public boolean isAvailable() { return status == VehicleStatus.AVAILABLE; }

    // ── Enums ──────────────────────────────────────────────────────────────────

    public enum VehicleType {
        PRINCIPAL_CAR, LEAD_CAR, FOLLOW_CAR
    }

    public enum VehicleStatus {
        AVAILABLE, IN_USE, IN_SERVICE, DECOMMISSIONED
    }
}
