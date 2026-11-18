package za.co.handyflow.platform.contracting.domain.model;

import jakarta.persistence.*;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.time.Instant;
import java.util.UUID;

/** A comment in the negotiation, optionally about one clause or one proposal. Internal staff notes are never shown to a counterparty. */
@Entity
@Table(name = "contract_negotiation_comments")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class ContractNegotiationComment {
    @Id private UUID id;
    @Column(name = "tenant_id") private UUID tenantId;
    @Column(name = "contract_id") private UUID contractId;
    @Column(name = "negotiation_id") private UUID negotiationId;
    @Column(name = "author_type") private String authorType;
    @Column(name = "author_user_id") private UUID authorUserId;
    @Column(name = "party_id") private UUID partyId;
    @Column(name = "block_index") private Integer blockIndex;
    @Column(name = "block_excerpt") private String blockExcerpt;
    @Column(name = "proposal_id") private UUID proposalId;
    @Column(name = "parent_id") private UUID parentId;
    private String body;
    @Column(name = "internal_only") private boolean internalOnly;
    @Column(name = "created_at") private Instant createdAt;

    /** @param audiencePartyId the one counterparty who can see it; null = every counterparty (unless internal, which none can see) */
    public static ContractNegotiationComment staff(UUID tenantId, UUID contractId, UUID negotiationId, UUID userId, UUID audiencePartyId, Integer blockIndex,
                                                   String blockExcerpt, UUID proposalId, UUID parentId, String body, boolean internalOnly) {
        return build(tenantId, contractId, negotiationId, "STAFF", userId, audiencePartyId, blockIndex, blockExcerpt, proposalId, parentId, body, internalOnly);
    }

    /** Whether this party may see the comment. Counterparties never see internal notes or another party's thread. */
    public boolean visibleTo(UUID viewerPartyId) {
        return !internalOnly && (partyId == null || partyId.equals(viewerPartyId));
    }

    /** Counterparty comments are never internal. */
    public static ContractNegotiationComment counterparty(UUID tenantId, UUID contractId, UUID negotiationId, UUID partyId, Integer blockIndex, String blockExcerpt,
                                                          UUID proposalId, UUID parentId, String body) {
        return build(tenantId, contractId, negotiationId, "COUNTERPARTY", null, partyId, blockIndex, blockExcerpt, proposalId, parentId, body, false);
    }

    private static ContractNegotiationComment build(UUID tenantId, UUID contractId, UUID negotiationId, String type, UUID userId, UUID partyId, Integer blockIndex,
                                                    String excerpt, UUID proposalId, UUID parentId, String body, boolean internal) {
        if (body == null || body.isBlank()) throw new IllegalArgumentException("A comment cannot be empty");
        if (body.length() > 4000) throw new IllegalArgumentException("That comment is too long");
        ContractNegotiationComment c = new ContractNegotiationComment();
        c.id = UUID.randomUUID();
        c.tenantId = tenantId; c.contractId = contractId; c.negotiationId = negotiationId;
        c.authorType = type; c.authorUserId = userId; c.partyId = partyId;
        c.blockIndex = blockIndex;
        c.blockExcerpt = excerpt == null ? null : excerpt.length() > 300 ? excerpt.substring(0, 300) : excerpt;
        c.proposalId = proposalId; c.parentId = parentId;
        c.body = body.trim();
        c.internalOnly = internal;
        c.createdAt = Instant.now();
        return c;
    }
}
