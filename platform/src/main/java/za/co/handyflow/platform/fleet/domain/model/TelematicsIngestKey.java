package za.co.handyflow.platform.fleet.domain.model;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.NoArgsConstructor;
import za.co.handyflow.platform.shared.TenantId;

import java.time.Instant;
import java.util.UUID;

/** A per-tenant credential for a telematics provider. Only the SHA-256 hash of the key is stored; it can be revoked but not recovered. */
@Entity
@Table(name = "fleet_telematics_ingest_keys")
@Getter
@NoArgsConstructor(access = lombok.AccessLevel.PROTECTED)
public class TelematicsIngestKey {

    @Id
    private UUID id = UUID.randomUUID();

    @Embedded
    @AttributeOverride(name = "value", column = @Column(name = "tenant_id", nullable = false))
    private TenantId tenantId;

    @Column(nullable = false, length = 150)
    private String name;

    @Column(name = "key_hash", nullable = false, length = 64, unique = true)
    private String keyHash;

    @Column(name = "key_prefix", nullable = false, length = 16)
    private String keyPrefix;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    @Column(name = "created_by")
    private UUID createdBy;

    @Column(name = "last_used_at")
    private Instant lastUsedAt;

    @Column(name = "revoked_at")
    private Instant revokedAt;

    public static TelematicsIngestKey create(TenantId tenantId, String name, String keyHash, String keyPrefix, UUID createdBy) {
        TelematicsIngestKey k = new TelematicsIngestKey();
        k.tenantId = tenantId;
        k.name = name;
        k.keyHash = keyHash;
        k.keyPrefix = keyPrefix;
        k.createdBy = createdBy;
        k.createdAt = Instant.now();
        return k;
    }

    public boolean isActive() { return revokedAt == null; }

    public void revoke() {
        if (revokedAt == null) this.revokedAt = Instant.now();
    }

    /** Throttled: not worth a write on every ingest call. */
    public void touch(Instant now) {
        if (lastUsedAt == null || lastUsedAt.isBefore(now.minusSeconds(60))) this.lastUsedAt = now;
    }
}