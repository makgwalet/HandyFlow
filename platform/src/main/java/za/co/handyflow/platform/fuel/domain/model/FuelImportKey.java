package za.co.handyflow.platform.fuel.domain.model;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.NoArgsConstructor;
import za.co.handyflow.platform.shared.TenantId;

import java.time.Instant;
import java.util.UUID;

/** Phase 8e (D03): a per-tenant credential for a pump or POS system. Only the SHA-256 hash is stored; it can be revoked but not recovered. */
@Entity
@Table(name = "fuel_import_keys")
@Getter
@NoArgsConstructor(access = lombok.AccessLevel.PROTECTED)
public class FuelImportKey {

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
    private Instant createdAt = Instant.now();

    @Column(name = "created_by")
    private UUID createdBy;

    @Column(name = "last_used_at")
    private Instant lastUsedAt;

    @Column(name = "revoked_at")
    private Instant revokedAt;

    public static FuelImportKey create(TenantId tenantId, String name, String keyHash, String keyPrefix, UUID by) {
        FuelImportKey k = new FuelImportKey();
        k.tenantId = tenantId; k.name = name; k.keyHash = keyHash; k.keyPrefix = keyPrefix; k.createdBy = by;
        return k;
    }

    public boolean isActive() { return revokedAt == null; }

    public void revoke() { if (revokedAt == null) revokedAt = Instant.now(); }

    /** Throttled: not worth a write on every call. */
    public void touch(Instant now) { if (lastUsedAt == null || lastUsedAt.isBefore(now.minusSeconds(60))) lastUsedAt = now; }
}
