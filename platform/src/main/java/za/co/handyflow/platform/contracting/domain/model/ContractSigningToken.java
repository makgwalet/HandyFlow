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

    /** Raw base64url token — stored in signing URL, indexed for fast lookup. */
    @Column(name = "token", nullable = false, unique = true, length = 512)
    private String token;

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
                                              Instant expiresAt) {
        ContractSigningToken t = new ContractSigningToken();
        t.tenantId   = tenantId;
        t.contractId = contractId;
        t.partyId    = partyId;
        t.token      = token;
        t.tokenHash  = sha256hex(token);   // ← populate the NOT NULL column
        t.expiresAt  = expiresAt;
        t.createdAt  = Instant.now();
        return t;
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
