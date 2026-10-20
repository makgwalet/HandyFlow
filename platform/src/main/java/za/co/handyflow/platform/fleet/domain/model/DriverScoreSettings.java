package za.co.handyflow.platform.fleet.domain.model;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.NoArgsConstructor;
import za.co.handyflow.platform.shared.TenantId;

import java.time.Instant;
import java.util.UUID;

/**
 * A tenant's scoring configuration: component weights (must total 100), the lookback
 * period, and the cut-offs for the EXCELLENT / GOOD / FAIR / POOR bands.
 * A tenant with no saved row uses {@link #defaults(TenantId)} — an unsaved instance.
 */
@Entity
@Table(name = "fleet_driver_score_settings")
@Getter
@NoArgsConstructor(access = lombok.AccessLevel.PROTECTED)
public class DriverScoreSettings {

    @Id
    private UUID id = UUID.randomUUID();

    @Embedded
    @AttributeOverride(name = "value", column = @Column(name = "tenant_id", nullable = false, unique = true))
    private TenantId tenantId;

    @Column(name = "weight_safety", nullable = false)
    private int weightSafety = 25;

    @Column(name = "weight_inspection", nullable = false)
    private int weightInspection = 20;

    @Column(name = "weight_accidents", nullable = false)
    private int weightAccidents = 25;

    @Column(name = "weight_fuel", nullable = false)
    private int weightFuel = 10;

    @Column(name = "weight_trips", nullable = false)
    private int weightTrips = 10;

    @Column(name = "weight_compliance", nullable = false)
    private int weightCompliance = 10;

    @Column(name = "lookback_days", nullable = false)
    private int lookbackDays = 90;

    @Column(name = "excellent_threshold", nullable = false)
    private int excellentThreshold = 90;

    @Column(name = "good_threshold", nullable = false)
    private int goodThreshold = 75;

    @Column(name = "fair_threshold", nullable = false)
    private int fairThreshold = 60;

    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;

    @Column(name = "updated_by")
    private UUID updatedBy;

    @Version
    private Long version;

    /** Built-in defaults, not persisted. */
    public static DriverScoreSettings defaults(TenantId tenantId) {
        DriverScoreSettings s = new DriverScoreSettings();
        s.tenantId = tenantId;
        s.updatedAt = Instant.now();
        return s;
    }

    public void apply(int safety, int inspection, int accidents, int fuel, int trips, int compliance,
                      int lookbackDays, int excellent, int good, int fair, UUID updatedBy) {
        int[] weights = {safety, inspection, accidents, fuel, trips, compliance};
        int sum = 0;
        for (int w : weights) {
            if (w < 0 || w > 100) throw new IllegalArgumentException("Each weight must be between 0 and 100");
            sum += w;
        }
        if (sum != 100) {
            throw new IllegalArgumentException("Weights must total 100 (they currently total " + sum + ")");
        }
        if (lookbackDays < 7 || lookbackDays > 730) {
            throw new IllegalArgumentException("Lookback must be between 7 and 730 days");
        }
        if (!(excellent > good && good > fair && fair > 0 && excellent <= 100)) {
            throw new IllegalArgumentException(
                    "Band thresholds must descend: excellent > good > fair > 0, and excellent ≤ 100");
        }
        this.weightSafety = safety;
        this.weightInspection = inspection;
        this.weightAccidents = accidents;
        this.weightFuel = fuel;
        this.weightTrips = trips;
        this.weightCompliance = compliance;
        this.lookbackDays = lookbackDays;
        this.excellentThreshold = excellent;
        this.goodThreshold = good;
        this.fairThreshold = fair;
        this.updatedBy = updatedBy;
        this.updatedAt = Instant.now();
    }
}