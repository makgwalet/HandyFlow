package za.co.handyflow.platform.fleet.domain.model;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.NoArgsConstructor;
import za.co.handyflow.platform.shared.TenantId;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

/**
 * Equipment details for a vehicle record that is more than a road vehicle: its class, the hour meters
 * other than the engine's (PTO, hydraulic — the engine hour meter stays on {@link Vehicle}, the single
 * source), hour-based servicing, charge-out rate, site, and — for trailers — what is hauling it.
 * <p>
 * Rules held here: meters only count up; PTO and hydraulic hours cannot exceed engine hours (they only
 * run while the engine does); a coupled trailer cannot change class until uncoupled. Anything that needs
 * to look at other records (is this vehicle already hauling trailers? is the host in the fleet?) is the
 * service's job.
 */
@Entity
@Table(name = "fleet_asset_profiles")
@Getter
@NoArgsConstructor(access = lombok.AccessLevel.PROTECTED)
public class AssetProfile {

    @Id
    private UUID id = UUID.randomUUID();

    @Embedded
    @AttributeOverride(name = "value", column = @Column(name = "tenant_id", nullable = false))
    private TenantId tenantId;

    @Column(name = "vehicle_id", nullable = false, unique = true)
    private UUID vehicleId;

    @Enumerated(EnumType.STRING)
    @Column(name = "asset_class", nullable = false, length = 20)
    private AssetClass assetClass = AssetClass.ROAD_VEHICLE;

    @Column(name = "pto_hours", nullable = false, precision = 10, scale = 1)
    private BigDecimal ptoHours = BigDecimal.ZERO;

    @Column(name = "hydraulic_hours", nullable = false, precision = 10, scale = 1)
    private BigDecimal hydraulicHours = BigDecimal.ZERO;

    /** What an hour of this asset is charged out at (internal recharge or hire rate). */
    @Column(name = "operating_rate_per_hour", precision = 15, scale = 2)
    private BigDecimal operatingRatePerHour;

    /** Hours a working weekday is expected to give; the denominator of utilisation. */
    @Column(name = "target_hours_per_day", nullable = false, precision = 4, scale = 1)
    private BigDecimal targetHoursPerDay = new BigDecimal("8.0");

    @Column(name = "service_interval_hours")
    private Integer serviceIntervalHours;

    @Column(name = "last_service_hours", precision = 10, scale = 1)
    private BigDecimal lastServiceHours;

    @Column(name = "coupled_to_vehicle_id")
    private UUID coupledToVehicleId;

    @Column(name = "site_name", length = 150)
    private String siteName;

    @Column(name = "site_updated_at")
    private Instant siteUpdatedAt;

    @Column(name = "axle_configuration", length = 50)
    private String axleConfiguration;

    /** Informational: the licence or certificate an operator needs (e.g. "Forklift licence"). */
    @Column(name = "operator_competency")
    private String operatorCompetency;

    @Column(columnDefinition = "TEXT")
    private String notes;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;

    @Version
    private Long version;

    public static AssetProfile create(TenantId tenantId, UUID vehicleId) {
        AssetProfile p = new AssetProfile();
        p.tenantId = tenantId;
        p.vehicleId = vehicleId;
        p.createdAt = Instant.now();
        p.updatedAt = Instant.now();
        return p;
    }

    /**
     * Full replace of the editable settings. {@code lastServiceHours} null leaves the existing value
     * unchanged (the service seeds it on first configuration so a new profile isn't instantly "due").
     */
    public void apply(AssetClass assetClass, BigDecimal operatingRatePerHour, BigDecimal targetHoursPerDay,
                      Integer serviceIntervalHours, BigDecimal lastServiceHours, String siteName,
                      String axleConfiguration, String operatorCompetency, String notes) {
        if (this.assetClass == AssetClass.TRAILER && assetClass != AssetClass.TRAILER && coupledToVehicleId != null) {
            throw new IllegalStateException("Uncouple the trailer before changing its asset class");
        }
        if (assetClass == AssetClass.TRAILER && !isFresh() && this.assetClass != AssetClass.TRAILER
                && (ptoHours.signum() > 0 || hydraulicHours.signum() > 0)) {
            throw new IllegalArgumentException("A trailer has no PTO or hydraulic hours — clear them first");
        }
        if (targetHoursPerDay == null || targetHoursPerDay.signum() <= 0 || targetHoursPerDay.compareTo(BigDecimal.valueOf(24)) > 0) {
            throw new IllegalArgumentException("Target hours per day must be between 0 and 24");
        }
        if (serviceIntervalHours != null && serviceIntervalHours <= 0) {
            throw new IllegalArgumentException("Service interval (hours) must be greater than zero");
        }
        if (!java.util.Objects.equals(this.siteName, siteName)) {
            this.siteUpdatedAt = Instant.now();
        }
        this.assetClass = assetClass;
        this.operatingRatePerHour = operatingRatePerHour;
        this.targetHoursPerDay = targetHoursPerDay;
        this.serviceIntervalHours = serviceIntervalHours;
        if (lastServiceHours != null) this.lastServiceHours = lastServiceHours;
        this.siteName = siteName;
        this.axleConfiguration = axleConfiguration;
        this.operatorCompetency = operatorCompetency;
        this.notes = notes;
        touch();
    }

    /** Seeds the service baseline the first time hour-based servicing is configured. */
    /** Sets only the hourly operating rate (null clears it). Used by the simple equipment screen. */
    public void changeOperatingRate(BigDecimal rate) {
        if (rate != null && rate.signum() < 0) {
            throw new IllegalArgumentException("Operating rate per hour cannot be negative");
        }
        this.operatingRatePerHour = rate;
        touch();
    }

    public void seedLastServiceHours(BigDecimal currentEngineHours) {
        if (serviceIntervalHours != null && lastServiceHours == null) {
            this.lastServiceHours = currentEngineHours;
            touch();
        }
    }

    /**
     * Records PTO / hydraulic meter readings (null = leave as is). Meters only count up, and neither can
     * exceed the engine's hours.
     */
    public void updateMeters(BigDecimal newPtoHours, BigDecimal newHydraulicHours, BigDecimal engineHours) {
        if (newPtoHours != null) {
            if (newPtoHours.compareTo(ptoHours) < 0) {
                throw new IllegalArgumentException("PTO hours (" + newPtoHours + ") cannot be lower than the current reading ("
                        + ptoHours + "). Hour meters only count up.");
            }
            this.ptoHours = newPtoHours;
        }
        if (newHydraulicHours != null) {
            if (newHydraulicHours.compareTo(hydraulicHours) < 0) {
                throw new IllegalArgumentException("Hydraulic hours (" + newHydraulicHours
                        + ") cannot be lower than the current reading (" + hydraulicHours + "). Hour meters only count up.");
            }
            this.hydraulicHours = newHydraulicHours;
        }
        if (ptoHours.compareTo(engineHours) > 0) {
            throw new IllegalArgumentException("PTO hours (" + ptoHours + ") cannot exceed engine hours (" + engineHours + ")");
        }
        if (hydraulicHours.compareTo(engineHours) > 0) {
            throw new IllegalArgumentException("Hydraulic hours (" + hydraulicHours + ") cannot exceed engine hours (" + engineHours + ")");
        }
        touch();
    }

    /** A service was done at this engine-hour reading: the hour-based interval restarts from here. */
    public void recordServiceAt(BigDecimal engineHours) {
        this.lastServiceHours = engineHours;
        touch();
    }

    public BigDecimal hoursSinceService(BigDecimal engineHours) {
        if (serviceIntervalHours == null || lastServiceHours == null) return null;
        return engineHours.subtract(lastServiceHours).max(BigDecimal.ZERO);
    }

    public boolean isServiceDueByHours(BigDecimal engineHours) {
        BigDecimal since = hoursSinceService(engineHours);
        return since != null && since.compareTo(BigDecimal.valueOf(serviceIntervalHours)) >= 0;
    }

    public void couple(UUID hostVehicleId) {
        if (assetClass != AssetClass.TRAILER) {
            throw new IllegalStateException("Only a TRAILER can be coupled to another vehicle");
        }
        if (coupledToVehicleId != null) {
            throw new IllegalStateException("This trailer is already coupled to another vehicle — uncouple it first");
        }
        if (hostVehicleId.equals(vehicleId)) {
            throw new IllegalArgumentException("A trailer cannot be coupled to itself");
        }
        this.coupledToVehicleId = hostVehicleId;
        touch();
    }

    public void uncouple() {
        this.coupledToVehicleId = null;
        touch();
    }

    private boolean isFresh() { return createdAt != null && createdAt.equals(updatedAt); }

    private void touch() { this.updatedAt = Instant.now(); }

    @PreUpdate
    void onUpdate() { this.updatedAt = Instant.now(); }
}