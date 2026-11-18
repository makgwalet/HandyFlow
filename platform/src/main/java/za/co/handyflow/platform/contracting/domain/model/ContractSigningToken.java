package za.co.handyflow.platform.contracting.domain.model;

import jakarta.persistence.*;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.time.Instant;
import java.util.HexFormat;
import java.util.UUID;


@Getter
@Setter
@Entity
@Table(name = "contract_signing_tokens")
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class ContractSigningToken {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @Column(name = "tenant_id", nullable = false)
    private UUID tenantId;

    @Column(name = "contract_id", nullable = false)
    private UUID contractId;

    @Column(name = "party_id", nullable = false)
    private UUID partyId;

    /**
     * Legacy column. The raw token is no longer stored (V771 blanked it): only {@link #tokenHash} is kept, so a database
     * leak cannot yield working signing links. Always null for new rows.
     */
    @Column(name = "token", length = 512)
    private String token;

    /** Who issued or reissued the link; null when the system did (for example the next signer's turn notice). */
    @Column(name = "issued_by")
    private UUID issuedBy;

    /** QUEUED (email queued or SMS sent), FAILED (only SMS was possible and it failed) or NO_CHANNEL. Email is sent asynchronously, so QUEUED is not "delivered". */
    @Column(name = "delivery_status", length = 20)
    private String deliveryStatus;

    @Column(name = "delivery_attempted_at")
    private Instant deliveryAttemptedAt;

    /**
     * SHA-256 hex of the raw token.
     * Required by the NOT NULL constraint added in V55__contracting_fixes.sql.
     * Can also be used as a secondary lookup or for audit purposes.
     */
    @Column(name = "token_hash", nullable = false, length = 64)
    private String tokenHash;

    @Column(name = "expires_at", nullable = false)
    private Instant expiresAt;

    @Column(name = "used_at")
    private Instant usedAt;        // null = not yet used

    @Column(name = "revoked_at")
    private Instant revokedAt;     // null = still valid; set on resend

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    /** Factory — always call this rather than constructor directly. */
    public static ContractSigningToken create(UUID tenantId, UUID contractId,
                                              UUID partyId, String token,
                                              Instant expiresAt, UUID issuedBy) {
        ContractSigningToken t = new ContractSigningToken();
        t.tenantId   = tenantId;
        t.contractId = contractId;
        t.partyId    = partyId;
        t.token      = null;               // the raw token is never stored
        t.tokenHash  = sha256hex(token);
        t.issuedBy   = issuedBy;
        t.expiresAt  = expiresAt;
        t.createdAt  = Instant.now();
        return t;
    }

    public void recordDelivery(String status) {
        this.deliveryStatus = status;
        this.deliveryAttemptedAt = Instant.now();
    }

    public boolean isValid() {
        return revokedAt == null && usedAt == null && Instant.now().isBefore(expiresAt);
    }

    // ── Internal ─────────────────────────────────────────────────────────────

    private static String sha256hex(String input) {
        try {
            MessageDigest md = MessageDigest.getInstance("SHA-256");
            byte[] hash = md.digest(input.getBytes(StandardCharsets.UTF_8));
            return HexFormat.of().formatHex(hash);
        } catch (Exception e) {
            throw new RuntimeException("SHA-256 failed", e);
        }
    }
}
