package za.co.handyflow.platform.earthmoving.domain.model;

import jakarta.persistence.*;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;
import za.co.handyflow.platform.shared.TenantId;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

/** One normalised reading: a position, a meter value, or both, from a provider device or typed in by a person. Never edited. */
@Entity
@Table(name = "earthmoving_telematics_readings")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class EarthTelematicsReading {

    @Id
    private UUID id = UUID.randomUUID();

    @Embedded
    @AttributeOverride(name = "value", column = @Column(name = "tenant_id", nullable = false))
    private TenantId tenantId;

    @Column(name = "asset_id", nullable = false)
    private UUID assetId;

    @Column(name = "device_id")
    private UUID deviceId;

    @Column(nullable = false)
    private String source;

    @Column(name = "provider_code")
    private String providerCode;

    @Column(name = "recorded_at", nullable = false)
    private Instant recordedAt;

    @Column(name = "received_at", nullable = false)
    private Instant receivedAt;

    @Column(precision = 9, scale = 6)
    private BigDecimal latitude;

    @Column(precision = 9, scale = 6)
    private BigDecimal longitude;

    @Column(name = "meter_value", precision = 12, scale = 2)
    private BigDecimal meterValue;

    @Column(name = "meter_unit")
    private String meterUnit;

    @Column(name = "entered_by")
    private UUID enteredBy;

    @Column(columnDefinition = "TEXT")
    private String note;

    public static EarthTelematicsReading fromProvider(TenantId tenantId, EarthTelematicsDevice device, Instant recordedAt, Instant now,
                                                      BigDecimal latitude, BigDecimal longitude, BigDecimal meter, String unit) {
        TelematicsRules.checkTime(recordedAt, now);
        TelematicsRules.checkReading(latitude, longitude, meter, unit);
        EarthTelematicsReading r = base(tenantId, device.getAssetId(), recordedAt, now, latitude, longitude, meter, unit);
        r.source = TelematicsRules.PROVIDER;
        r.deviceId = device.getId();
        r.providerCode = device.getProviderCode();
        return r;
    }

    public static EarthTelematicsReading manual(TenantId tenantId, UUID assetId, Instant recordedAt, Instant now,
                                                BigDecimal latitude, BigDecimal longitude, BigDecimal meter, String unit, String note, UUID actor) {
        TelematicsRules.checkTime(recordedAt, now);
        TelematicsRules.checkReading(latitude, longitude, meter, unit);
        EarthTelematicsReading r = base(tenantId, assetId, recordedAt, now, latitude, longitude, meter, unit);
        r.source = TelematicsRules.MANUAL;
        r.enteredBy = actor;
        String n = note == null ? "" : note.trim();
        if (n.length() > 1000) throw new IllegalArgumentException("Keep the note under 1000 characters.");
        r.note = n.isEmpty() ? null : n;
        return r;
    }

    private static EarthTelematicsReading base(TenantId tenantId, UUID assetId, Instant recordedAt, Instant now,
                                               BigDecimal latitude, BigDecimal longitude, BigDecimal meter, String unit) {
        EarthTelematicsReading r = new EarthTelematicsReading();
        r.tenantId = tenantId;
        r.assetId = assetId;
        r.recordedAt = recordedAt;
        r.receivedAt = now;
        r.latitude = latitude;
        r.longitude = longitude;
        r.meterValue = meter;
        r.meterUnit = meter == null ? null : TelematicsRules.unit(unit);
        return r;
    }
}
