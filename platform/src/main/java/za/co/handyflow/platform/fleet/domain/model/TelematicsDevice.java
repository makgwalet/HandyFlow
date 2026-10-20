package za.co.handyflow.platform.fleet.domain.model;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.NoArgsConstructor;
import za.co.handyflow.platform.shared.TenantId;

import java.time.Instant;
import java.util.UUID;

/** Maps a provider's unit id ({@code externalId}) to one of our vehicles. Deactivated rather than deleted. */
@Entity
@Table(name = "fleet_telematics_devices")
@Getter
@NoArgsConstructor(access = lombok.AccessLevel.PROTECTED)
public class TelematicsDevice {

    @Id
    private UUID id = UUID.randomUUID();

    @Embedded
    @AttributeOverride(name = "value", column = @Column(name = "tenant_id", nullable = false))
    private TenantId tenantId;

    @Column(nullable = false, length = 50)
    private String provider = "GENERIC";

    @Column(name = "external_id", nullable = false, length = 100)
    private String externalId;

    @Column(name = "vehicle_id", nullable = false)
    private UUID vehicleId;

    @Column(length = 150)
    private String name;

    @Column(nullable = false)
    private boolean active = true;

    @Column(name = "last_seen_at")
    private Instant lastSeenAt;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;

    @Version
    private Long version;

    public static TelematicsDevice create(TenantId tenantId, String provider, String externalId, UUID vehicleId, String name) {
        TelematicsDevice d = new TelematicsDevice();
        d.tenantId = tenantId;
        d.createdAt = Instant.now();
        d.update(provider, externalId, vehicleId, name, true);
        return d;
    }

    public void update(String provider, String externalId, UUID vehicleId, String name, boolean active) {
        this.provider = provider;
        this.externalId = externalId;
        this.vehicleId = vehicleId;
        this.name = name;
        this.active = active;
        this.updatedAt = Instant.now();
    }

    public void seen(Instant at) {
        if (lastSeenAt == null || at.isAfter(lastSeenAt)) this.lastSeenAt = at;
    }
}