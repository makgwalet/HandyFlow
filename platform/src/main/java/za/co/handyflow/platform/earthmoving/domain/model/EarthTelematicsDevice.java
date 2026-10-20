package za.co.handyflow.platform.earthmoving.domain.model;

import jakarta.persistence.*;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;
import za.co.handyflow.platform.shared.TenantId;

import java.time.Instant;
import java.util.UUID;

/** A tracking device (identified by its provider's code and its own id) mapped to a machine. Unmapping keeps the row. */
@Entity
@Table(name = "earthmoving_telematics_devices")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class EarthTelematicsDevice {

    @Id
    private UUID id = UUID.randomUUID();

    @Embedded
    @AttributeOverride(name = "value", column = @Column(name = "tenant_id", nullable = false))
    private TenantId tenantId;

    @Column(name = "asset_id", nullable = false)
    private UUID assetId;

    @Column(name = "provider_code", nullable = false)
    private String providerCode;

    @Column(name = "external_id", nullable = false)
    private String externalId;

    private String label;

    @Column(nullable = false)
    private boolean active = true;

    @Column(name = "mapped_by", nullable = false)
    private UUID mappedBy;

    @Column(name = "mapped_at", nullable = false)
    private Instant mappedAt;

    @Column(name = "unmapped_by")
    private UUID unmappedBy;

    @Column(name = "unmapped_at")
    private Instant unmappedAt;

    @Column(name = "unmap_reason", columnDefinition = "TEXT")
    private String unmapReason;

    public static EarthTelematicsDevice map(TenantId tenantId, UUID assetId, String providerCode, String externalId, String label, UUID actor) {
        EarthTelematicsDevice d = new EarthTelematicsDevice();
        d.tenantId = tenantId;
        d.assetId = assetId;
        d.providerCode = TelematicsRules.providerCode(providerCode);
        d.externalId = TelematicsRules.externalId(externalId);
        String l = label == null ? "" : label.trim();
        if (l.length() > 120) throw new IllegalArgumentException("The label is too long.");
        d.label = l.isEmpty() ? null : l;
        d.mappedBy = actor;
        d.mappedAt = Instant.now();
        return d;
    }

    public void unmap(String reason, UUID actor) {
        if (!active) throw new IllegalStateException("This device is already unmapped.");
        this.unmapReason = TelematicsRules.reason(reason);
        this.active = false;
        this.unmappedBy = actor;
        this.unmappedAt = Instant.now();
    }
}
