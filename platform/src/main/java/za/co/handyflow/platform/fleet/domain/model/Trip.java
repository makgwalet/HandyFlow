package za.co.handyflow.platform.fleet.domain.model;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.NoArgsConstructor;
import za.co.handyflow.platform.shared.TenantId;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "fleet_trips")
@Getter
@NoArgsConstructor(access = lombok.AccessLevel.PROTECTED)
public class Trip {

    @Id
    private UUID id = UUID.randomUUID();

    @Embedded
    @AttributeOverride(name = "value",
            column = @Column(name = "tenant_id", nullable = false))
    private TenantId tenantId;

    @Column(name = "vehicle_id", nullable = false)
    private UUID vehicleId;

    @Column(name = "guard_id")
    private UUID guardId;

    @Column(name = "driver_name")
    private String driverName;

    private String purpose;

    // NEW: BUSINESS | PRIVATE — for SARS logbook compliance
    @Column(name = "trip_type", nullable = false)
    private String tripType = "BUSINESS";

    @Column(name = "start_location")
    private String startLocation;

    @Column(name = "end_location")
    private String endLocation;

    @Column(name = "start_odometer", nullable = false)
    private Integer startOdometer;

    @Column(name = "end_odometer")
    private Integer endOdometer;

    @Column(name = "start_at", nullable = false)
    private Instant startAt;

    @Column(name = "end_at")
    private Instant endAt;

    @Column(name = "fuel_used_litres", precision = 8, scale = 2)
    private BigDecimal fuelUsedLitres;

    // NEW: ACTIVE | COMPLETED | CANCELLED — needed for filtering in logbook
    @Column(name = "status", nullable = false)
    private String status = "ACTIVE";

    private String notes;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    // ── Factory — 9 args matching FleetService.startTrip ─────────────────────

    public static Trip create(TenantId tenantId, UUID vehicleId,
                              UUID guardId, String driverName,
                              String purpose, String tripType,
                              String startLocation,
                              Integer startOdometer, Instant startAt) {
        Trip t = new Trip();
        t.tenantId      = tenantId;
        t.vehicleId     = vehicleId;
        t.guardId       = guardId;
        t.driverName    = driverName;
        t.purpose       = purpose;
        t.tripType      = tripType != null ? tripType : "BUSINESS";
        t.startLocation = startLocation;
        t.startOdometer = startOdometer;
        t.startAt       = startAt;
        t.status        = "ACTIVE";
        t.createdAt     = Instant.now();
        return t;
    }

    // ── Domain logic ──────────────────────────────────────────────────────────

    public Integer getDistanceKm() {
        if (endOdometer == null) return null;
        return endOdometer - startOdometer;
    }

    public void complete(String endLocation, Integer endOdometer,
                         Instant endAt, BigDecimal fuelUsedLitres, String notes) {
        if (endOdometer != null && endOdometer < this.startOdometer) {
            throw new IllegalArgumentException(
                    "End odometer (" + endOdometer + ") cannot be less than start (" + startOdometer + ")");
        }
        this.endLocation    = endLocation;
        this.endOdometer    = endOdometer;
        this.endAt          = endAt;
        this.fuelUsedLitres = fuelUsedLitres;
        this.notes          = notes;
        this.status         = "COMPLETED";
    }

    public void cancel() {
        this.status = "CANCELLED";
    }
}
