package za.co.handyflow.platform.fleet.domain.model;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.NoArgsConstructor;
import za.co.handyflow.platform.shared.TenantId;

import java.time.Instant;
import java.util.UUID;

/**
 * A tenant's telematics rule thresholds. A tenant with no saved row uses {@link #defaults(TenantId)}.
 * <p>
 * The default speed limit of 120 km/h is the highest legal limit on South African roads, so a
 * single default would let town speeding pass unnoticed. Lower it, or — better — draw geofences
 * with lower limits around depots, towns and other slow zones.
 */
@Entity
@Table(name = "fleet_telematics_settings")
@Getter
@NoArgsConstructor(access = lombok.AccessLevel.PROTECTED)
public class TelematicsSettings {

    @Id
    private UUID id = UUID.randomUUID();

    @Embedded
    @AttributeOverride(name = "value", column = @Column(name = "tenant_id", nullable = false, unique = true))
    private TenantId tenantId;

    @Column(name = "default_speed_limit_kph", nullable = false)
    private int defaultSpeedLimitKph = 120;

    @Column(name = "speeding_tolerance_kph", nullable = false)
    private int speedingToleranceKph = 5;

    @Column(name = "speeding_cooldown_minutes", nullable = false)
    private int speedingCooldownMinutes = 5;

    @Column(name = "alert_overspeed_kph", nullable = false)
    private int alertOverspeedKph = 30;

    @Column(name = "idling_threshold_minutes", nullable = false)
    private int idlingThresholdMinutes = 10;

    @Column(name = "offline_after_minutes", nullable = false)
    private int offlineAfterMinutes = 30;

    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;

    @Column(name = "updated_by")
    private UUID updatedBy;

    @Version
    private Long version;

    public static TelematicsSettings defaults(TenantId tenantId) {
        TelematicsSettings s = new TelematicsSettings();
        s.tenantId = tenantId;
        s.updatedAt = Instant.now();
        return s;
    }

    public void apply(int defaultSpeedLimitKph, int speedingToleranceKph, int speedingCooldownMinutes,
                      int alertOverspeedKph, int idlingThresholdMinutes, int offlineAfterMinutes, UUID updatedBy) {
        require(defaultSpeedLimitKph, 20, 200, "Default speed limit");
        require(speedingToleranceKph, 0, 30, "Speeding tolerance");
        require(speedingCooldownMinutes, 1, 120, "Speeding cooldown");
        require(alertOverspeedKph, 5, 100, "Alert overspeed");
        require(idlingThresholdMinutes, 1, 240, "Idling threshold");
        require(offlineAfterMinutes, 5, 1440, "Offline threshold");
        this.defaultSpeedLimitKph = defaultSpeedLimitKph;
        this.speedingToleranceKph = speedingToleranceKph;
        this.speedingCooldownMinutes = speedingCooldownMinutes;
        this.alertOverspeedKph = alertOverspeedKph;
        this.idlingThresholdMinutes = idlingThresholdMinutes;
        this.offlineAfterMinutes = offlineAfterMinutes;
        this.updatedBy = updatedBy;
        this.updatedAt = Instant.now();
    }

    private static void require(int v, int min, int max, String what) {
        if (v < min || v > max) {
            throw new IllegalArgumentException(what + " must be between " + min + " and " + max);
        }
    }
}