package za.co.handyflow.platform.earthmoving.domain.model;

import jakarta.persistence.*;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;
import za.co.handyflow.platform.shared.TenantId;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

/** One change to a machine's meter reading, with the one before it, where it came from and (for corrections) why. Append-only. */
@Entity
@Table(name = "earthmoving_meter_readings")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class EarthMeterReading {

    @Id
    private UUID id = UUID.randomUUID();

    @Embedded
    @AttributeOverride(name = "value", column = @Column(name = "tenant_id", nullable = false))
    private TenantId tenantId;

    @Column(name = "asset_id", nullable = false)
    private UUID assetId;

    @Column(name = "meter_type", nullable = false)
    private String meterType;

    @Column(nullable = false, precision = 10, scale = 1)
    private BigDecimal reading;

    @Column(name = "previous_reading", precision = 10, scale = 1)
    private BigDecimal previousReading;

    @Column(nullable = false)
    private String source;

    private String reason;

    @Column(name = "actor_user_id")
    private UUID actorUserId;

    @Column(name = "recorded_at", nullable = false)
    private Instant recordedAt;

    /** For a correction: the reading it replaces. A past period then sees this figure at the replaced reading's place in time. */
    @Column(name = "corrects_id")
    private UUID correctsId;

    public static EarthMeterReading of(TenantId tenantId, UUID assetId, String meterType, BigDecimal reading, BigDecimal previous,
                                       String source, String reason, UUID actor) {
        if (!MeterRules.SOURCES.contains(source)) throw new IllegalArgumentException("Unknown reading source " + source);
        EarthMeterReading r = new EarthMeterReading();
        r.tenantId = tenantId;
        r.assetId = assetId;
        r.meterType = meterType;
        r.reading = reading;
        r.previousReading = previous;
        r.source = source;
        r.reason = reason;
        r.actorUserId = actor;
        r.recordedAt = Instant.now();
        return r;
    }

    /** A correction that replaces one reading. */
    public static EarthMeterReading correctionOf(TenantId tenantId, UUID assetId, String meterType, BigDecimal reading, BigDecimal previous,
                                                 String reason, UUID actor, UUID replaces) {
        EarthMeterReading r = of(tenantId, assetId, meterType, reading, previous, "CORRECTION", reason, actor);
        r.correctsId = replaces;
        return r;
    }

    public MeterRules.Point point() {
        return new MeterRules.Point(id, meterType, reading, recordedAt, correctsId);
    }
}
