package za.co.handyflow.platform.crm.domain.model;

import jakarta.persistence.*;
import java.time.Instant;
import java.util.UUID;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;
import za.co.handyflow.platform.shared.TenantId;

/** A credential for sending leads in from outside. Only the SHA-256 hash is stored; it can be revoked but not recovered. */
@Entity
@Table(name = "crm_capture_keys")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class CaptureKey {
    @Id
    private UUID id = UUID.randomUUID();

    @Embedded
    @AttributeOverride(name = "value", column = @Column(name = "tenant_id", nullable = false))
    private TenantId tenantId;

    @Column(name = "name", nullable = false, length = 120)
    private String name;

    @Column(name = "key_hash", nullable = false, length = 64, unique = true)
    private String keyHash;

    @Column(name = "key_prefix", nullable = false, length = 16)
    private String keyPrefix;

    @Enumerated(EnumType.STRING)
    @Column(name = "default_source", nullable = false, length = 20)
    private LeadSource defaultSource;

    @Column(name = "created_by")
    private UUID createdBy;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    @Column(name = "last_used_at")
    private Instant lastUsedAt;

    @Column(name = "revoked_at")
    private Instant revokedAt;

    public static CaptureKey create(TenantId tenantId, String name, String keyHash, String keyPrefix, LeadSource defaultSource, UUID createdBy) {
        var k = new CaptureKey();
        k.tenantId = tenantId;
        k.name = name.strip();
        k.keyHash = keyHash;
        k.keyPrefix = keyPrefix;
        k.defaultSource = defaultSource;
        k.createdBy = createdBy;
        k.createdAt = Instant.now();
        return k;
    }

    public boolean isActive() { return revokedAt == null; }

    public void revoke() { if (revokedAt == null) this.revokedAt = Instant.now(); }

    /** Throttled: not worth a write on every lead. */
    public void touch(Instant now) { if (lastUsedAt == null || lastUsedAt.isBefore(now.minusSeconds(60))) this.lastUsedAt = now; }
}
