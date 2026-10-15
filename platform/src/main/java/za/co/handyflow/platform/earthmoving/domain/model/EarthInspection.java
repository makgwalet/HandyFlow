package za.co.handyflow.platform.earthmoving.domain.model;

import jakarta.persistence.*;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;
import za.co.handyflow.platform.shared.TenantId;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

/** One pre-use inspection of one machine. Never edited after it is saved. */
@Entity
@Table(name = "earthmoving_inspections")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class EarthInspection {

    @Id
    private UUID id = UUID.randomUUID();

    @Embedded
    @AttributeOverride(name = "value", column = @Column(name = "tenant_id", nullable = false))
    private TenantId tenantId;

    @Column(name = "asset_id", nullable = false)
    private UUID assetId;

    @Column(name = "inspector_user_id")
    private UUID inspectorUserId;

    @Column(name = "meter_reading", precision = 10, scale = 1)
    private BigDecimal meterReading;

    @Column(nullable = false)
    private String result;

    private String notes;

    @Column(name = "performed_at", nullable = false)
    private Instant performedAt;

    public static EarthInspection create(TenantId tenantId, UUID assetId, UUID inspector, BigDecimal meterReading, String result, String notes) {
        if (meterReading != null && meterReading.signum() < 0) throw new IllegalArgumentException("A meter reading cannot be negative.");
        EarthInspection i = new EarthInspection();
        i.tenantId = tenantId;
        i.assetId = assetId;
        i.inspectorUserId = inspector;
        i.meterReading = meterReading;
        i.result = result;
        i.notes = notes == null || notes.isBlank() ? null : notes.trim();
        i.performedAt = Instant.now();
        return i;
    }
}
