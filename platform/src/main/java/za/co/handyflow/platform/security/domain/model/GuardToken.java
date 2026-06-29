// security/domain/model/GuardToken.java

package za.co.handyflow.platform.security.domain.model;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.NoArgsConstructor;
import za.co.handyflow.platform.shared.TenantId;

import java.time.Instant;
import java.util.UUID;

/**
 * GuardToken — a persisted record of an issued guard JWT.
 *
 * WHY persist tokens at all when JWTs are stateless?
 * Guard tokens need to be revocable by a supervisor in real time:
 *   - Guard's status changes to SUSPENDED mid-shift
 *   - Device is reported stolen
 *   - Supervisor force-closes a device session (Phase 2)
 *
 * The JWT carries a `jti` claim equal to this row's id.
 * GuardJwtFilter checks this table on every request to /api/v1/guard/**
 * to confirm the token hasn't been revoked since it was issued.
 *
 * WHY not a blocklist (only store revoked tokens)?
 * A blocklist grows unboundedly.  An allowlist of active tokens is finite
 * (one per active guard session) and can be indexed and cleaned up easily.
 * Expired rows are purged by a nightly job.
 *
 * WHY a separate table from user sessions?
 * Guard tokens have different lifetimes (12h = one shift), different
 * revocation triggers (supervisor action, status change), and different
 * metadata (device_id for Phase 2 binding).  Mixing them with user JWT
 * tracking creates coupling between two unrelated auth flows.
 */
@Entity
@Table(name = "security_guard_tokens")
@Getter
@NoArgsConstructor(access = lombok.AccessLevel.PROTECTED)
public class GuardToken {

    @Id
    private UUID id = UUID.randomUUID();   // this is the JWT jti claim

    @Embedded
    @AttributeOverride(name = "value",
            column = @Column(name = "tenant_id", nullable = false))
    private TenantId tenantId;

    @Column(name = "guard_id", nullable = false)
    private UUID guardId;

    @Column(name = "device_id")
    private String deviceId;

    @Column(name = "issued_at", nullable = false, updatable = false)
    private Instant issuedAt;

    @Column(name = "expires_at", nullable = false)
    private Instant expiresAt;

    @Column(name = "revoked_at")
    private Instant revokedAt;

    @Column(name = "revoke_reason", length = 100)
    private String revokeReason;

    // ── Factory ────────────────────────────────────────────────────────────────

    public static GuardToken issue(TenantId tenantId, UUID guardId,
                                   String deviceId, Instant expiresAt) {
        GuardToken t  = new GuardToken();
        t.tenantId    = tenantId;
        t.guardId     = guardId;
        t.deviceId    = deviceId;
        t.issuedAt    = Instant.now();
        t.expiresAt   = expiresAt;
        return t;
    }

    // ── Mutations ──────────────────────────────────────────────────────────────

    public void revoke(String reason) {
        this.revokedAt    = Instant.now();
        this.revokeReason = reason;
    }

    // ── Queries ────────────────────────────────────────────────────────────────

    public boolean isActive() {
        return revokedAt == null && expiresAt.isAfter(Instant.now());
    }

    public boolean isRevoked() { return revokedAt != null; }

    public boolean isExpired()  { return expiresAt.isBefore(Instant.now()); }
}