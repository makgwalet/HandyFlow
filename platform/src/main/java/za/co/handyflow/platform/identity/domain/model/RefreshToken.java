package za.co.handyflow.platform.identity.domain.model;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.time.Instant;
import java.util.UUID;

/**
 * A hashed, revocable refresh token — the piece the previous architecture
 * was entirely missing. Never holds the raw token value; see
 * RefreshTokenService for hashing and the rotation/reuse-detection logic
 * that actually makes this useful rather than just a longer-lived copy of
 * the same problem.
 */
@Entity
@Table(name = "refresh_tokens")
@Getter
@NoArgsConstructor(access = lombok.AccessLevel.PROTECTED)
public class RefreshToken {

    @Id
    private UUID id = UUID.randomUUID();

    @Column(name = "user_id", nullable = false)
    private UUID userId;

    @Column(name = "tenant_id", nullable = false)
    private UUID tenantId;

    @Column(name = "token_hash", nullable = false, unique = true, length = 64)
    private String tokenHash;

    @Column(name = "device_fingerprint")
    private String deviceFingerprint;

    @Column(name = "ip_address")
    private String ipAddress;

    @Column(name = "user_agent")
    private String userAgent;

    @Column(name = "created_at", nullable = false)
    private Instant createdAt;

    @Column(name = "last_used_at")
    private Instant lastUsedAt;

    @Column(name = "expires_at", nullable = false)
    private Instant expiresAt;

    @Column(name = "revoked_at")
    private Instant revokedAt;

    @Column(name = "replaced_by_token_hash")
    private String replacedByTokenHash;

    public static RefreshToken create(UUID userId, UUID tenantId, String tokenHash,
                                      String deviceFingerprint, String ipAddress,
                                      String userAgent, long expirationMs) {
        RefreshToken rt = new RefreshToken();
        rt.userId             = userId;
        rt.tenantId            = tenantId;
        rt.tokenHash           = tokenHash;
        rt.deviceFingerprint   = deviceFingerprint;
        rt.ipAddress           = ipAddress;
        rt.userAgent           = userAgent;
        rt.createdAt           = Instant.now();
        rt.expiresAt           = Instant.now().plusMillis(expirationMs);
        return rt;
    }

    public boolean isExpired() { return Instant.now().isAfter(expiresAt); }
    public boolean isRevoked() { return revokedAt != null; }
    public boolean isActive()  { return !isExpired() && !isRevoked(); }

    /** Called the moment a token is rotated away — not a hard delete, so the reuse-detection check below still has something to find. */
    public void revoke(String replacedByTokenHash) {
        this.revokedAt            = Instant.now();
        this.replacedByTokenHash  = replacedByTokenHash;
    }

    /** Explicit revocation — logout, "sign out everywhere", or theft-response sweep. No successor token, since nothing replaced this one. */
    public void revoke() {
        this.revokedAt = Instant.now();
    }

    public void recordUse() {
        this.lastUsedAt = Instant.now();
    }
}