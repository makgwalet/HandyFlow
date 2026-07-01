// security/domain/model/ApiKey.java
package za.co.handyflow.platform.security.domain.model;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.NoArgsConstructor;
import za.co.handyflow.platform.shared.TenantId;
import java.time.Instant;
import java.util.UUID;

/**
 * ApiKey — a long-lived machine-to-machine key for client BI tools and
 * third-party integrations.
 *
 * The actual key is NEVER stored — only its SHA-256 hash (key_hash) and
 * a display prefix (first 8 chars, e.g. "hf_live_a3b4...") are persisted.
 * The key is shown exactly once at creation and cannot be retrieved afterward.
 * This is the same pattern as the guard webhook secret and the CP vetting
 * principal's encrypted_detail — one-time reveal, hash-verify thereafter.
 *
 * Authentication flow:
 *   Client sends: Authorization: ApiKey hf_live_a3b4c5d6e7f8...
 *   Server: SHA-256 the presented key → lookup by key_hash → validate tenant,
 *   scope, expiry, active flag → resolve TenantId for the request.
 */
@Entity
@Table(name = "security_api_keys")
@Getter
@NoArgsConstructor(access = lombok.AccessLevel.PROTECTED)
public class ApiKey {

    @Id private UUID id = UUID.randomUUID();

    @Embedded
    @AttributeOverride(name = "value", column = @Column(name = "tenant_id", nullable = false))
    private TenantId tenantId;

    @Column(nullable = false, length = 100) private String name;
    @Column(name = "key_hash", nullable = false, unique = true, length = 64) private String keyHash;
    @Column(name = "key_prefix", nullable = false, length = 12) private String keyPrefix;

    @org.hibernate.annotations.JdbcTypeCode(org.hibernate.type.SqlTypes.JSON)
    @Column(name = "scope_prefixes", columnDefinition = "jsonb") private String scopePrefixes;

    @Column(name = "branch_id") private UUID branchId;
    @Column(name = "read_only", nullable = false) private boolean readOnly = true;
    @Column(nullable = false) private boolean active = true;
    @Column(name = "last_used_at") private Instant lastUsedAt;
    @Column(name = "expires_at") private Instant expiresAt;
    @Column(name = "created_by") private UUID createdBy;
    @Column(name = "created_at", nullable = false, updatable = false) private Instant createdAt;
    @Column(name = "revoked_at") private Instant revokedAt;
    @Column(name = "revoked_by") private UUID revokedBy;
    @Column(name = "revocation_reason") private String revocationReason;

    public static ApiKey create(TenantId tenantId, String name, String keyHash, String keyPrefix,
                                String scopePrefixes, UUID branchId, boolean readOnly,
                                Instant expiresAt, UUID createdBy) {
        ApiKey k = new ApiKey();
        k.tenantId = tenantId; k.name = name.strip(); k.keyHash = keyHash;
        k.keyPrefix = keyPrefix; k.scopePrefixes = scopePrefixes; k.branchId = branchId;
        k.readOnly = readOnly; k.expiresAt = expiresAt; k.createdBy = createdBy;
        k.active = true; k.createdAt = Instant.now();
        return k;
    }

    public void recordUse() { this.lastUsedAt = Instant.now(); }

    public void revoke(UUID revokedBy, String reason) {
        this.active = false; this.revokedAt = Instant.now();
        this.revokedBy = revokedBy; this.revocationReason = reason;
    }

    public boolean isExpired() {
        return expiresAt != null && Instant.now().isAfter(expiresAt);
    }

    public boolean isValid() { return active && !isExpired(); }
}