package za.co.handyflow.platform.contracting.domain.model;

import jakarta.persistence.*;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.time.Instant;
import java.util.UUID;

/** A counterparty's access link to a negotiation. Holds only the hash of the token; the token itself is shown once, when issued. */
@Entity
@Table(name = "contract_negotiation_links")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class ContractNegotiationLink {
    @Id private UUID id;
    @Column(name = "tenant_id") private UUID tenantId;
    @Column(name = "contract_id") private UUID contractId;
    @Column(name = "negotiation_id") private UUID negotiationId;
    @Column(name = "party_id") private UUID partyId;
    @Column(name = "token_hash") private String tokenHash;
    @Column(name = "expires_at") private Instant expiresAt;
    @Column(name = "revoked_at") private Instant revokedAt;
    @Column(name = "revoked_reason") private String revokedReason;
    @Column(name = "issued_by") private UUID issuedBy;
    @Column(name = "delivery_status") private String deliveryStatus;
    @Column(name = "last_accessed_at") private Instant lastAccessedAt;
    @Column(name = "created_at") private Instant createdAt;

    public static ContractNegotiationLink issue(UUID tenantId, UUID contractId, UUID negotiationId, UUID partyId, String tokenHash, Instant expiresAt, UUID by) {
        if (tokenHash == null || tokenHash.length() != 64) throw new IllegalArgumentException("A link is stored by the hash of its token");
        if (!expiresAt.isAfter(Instant.now())) throw new IllegalArgumentException("A link must expire in the future");
        ContractNegotiationLink l = new ContractNegotiationLink();
        l.id = UUID.randomUUID();
        l.tenantId = tenantId; l.contractId = contractId; l.negotiationId = negotiationId; l.partyId = partyId;
        l.tokenHash = tokenHash;
        l.expiresAt = expiresAt;
        l.issuedBy = by;
        l.createdAt = Instant.now();
        return l;
    }

    public boolean isActive(Instant now) { return revokedAt == null && now.isBefore(expiresAt); }

    public void revoke(String reason) {
        if (revokedAt != null) return;
        revokedAt = Instant.now();
        revokedReason = reason;
    }

    public void recordDelivery(String status) { this.deliveryStatus = status; }

    public void touch() { this.lastAccessedAt = Instant.now(); }
}
