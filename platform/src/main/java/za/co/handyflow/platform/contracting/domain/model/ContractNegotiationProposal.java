package za.co.handyflow.platform.contracting.domain.model;

import jakarta.persistence.*;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

/**
 * A counterparty's proposed change to the wording. It is a request, never a change: the contract only moves when staff accept it
 * and apply it as a new draft revision. Every status is kept for the audit trail.
 */
@Entity
@Table(name = "contract_negotiation_proposals")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class ContractNegotiationProposal {
    public static final String ADD = "ADD", DELETE = "DELETE", REPLACE = "REPLACE";
    public static final String PENDING = "PENDING_REVIEW", ACCEPTED = "ACCEPTED", REJECTED = "REJECTED", WITHDRAWN = "WITHDRAWN",
            SUPERSEDED = "SUPERSEDED", CONFLICTED = "CONFLICTED", APPLIED = "APPLIED";
    private static final int MAX_HTML = 20_000;

    @Id private UUID id;
    @Column(name = "tenant_id") private UUID tenantId;
    @Column(name = "contract_id") private UUID contractId;
    @Column(name = "negotiation_id") private UUID negotiationId;
    @Column(name = "party_id") private UUID partyId;
    private String kind;
    @Column(name = "base_version_no") private int baseVersionNo;
    @Column(name = "block_index") private Integer blockIndex;
    @Column(name = "original_html", columnDefinition = "TEXT") private String originalHtml;
    @Column(name = "proposed_html", columnDefinition = "TEXT") private String proposedHtml;
    private String rationale;
    private String status;
    @Column(name = "conflict_with") private UUID conflictWith;
    @Column(name = "decided_by") private UUID decidedBy;
    @Column(name = "decided_at") private Instant decidedAt;
    @Column(name = "decision_note") private String decisionNote;
    @Column(name = "applied_version_no") private Integer appliedVersionNo;
    @Column(name = "applied_by") private UUID appliedBy;
    @Column(name = "applied_at") private Instant appliedAt;
    @Column(name = "created_at") private Instant createdAt;
    @Column(name = "updated_at") private Instant updatedAt;

    /**
     * @param blockIndex   position of the block in the base version (ADD: the block to insert after, -1 for the very start)
     * @param originalHtml the block it is attached to, as it was in the base version (ADD: the block to insert after, null for the start)
     * @param proposedHtml the new wording (ADD and REPLACE); sanitised here
     */
    public static ContractNegotiationProposal create(UUID tenantId, UUID contractId, UUID negotiationId, UUID partyId, String kind, int baseVersionNo,
                                                     Integer blockIndex, String originalHtml, String proposedHtml, String rationale) {
        String k = kind == null ? "" : kind.trim().toUpperCase();
        if (!List.of(ADD, DELETE, REPLACE).contains(k)) throw new IllegalArgumentException("A proposal must add, delete or replace wording");
        String clean = proposedHtml == null ? null : HtmlSanitizer.clean(proposedHtml);
        boolean hasNew = clean != null && !BodyDiff.blocks(clean).isEmpty();
        if (!DELETE.equals(k) && !hasNew) throw new IllegalArgumentException("Say what the new wording should be");
        if (DELETE.equals(k) && hasNew) throw new IllegalArgumentException("A deletion has no new wording");
        if (!ADD.equals(k) && (originalHtml == null || originalHtml.isBlank())) throw new IllegalArgumentException("Say which wording this changes");
        if (REPLACE.equals(k) && sameText(originalHtml, clean)) throw new IllegalArgumentException("The new wording is the same as the existing wording");
        if (clean != null && clean.length() > MAX_HTML) throw new IllegalArgumentException("That wording is too long for one proposal");
        if (rationale != null && rationale.length() > 2000) throw new IllegalArgumentException("The explanation is too long");
        ContractNegotiationProposal p = new ContractNegotiationProposal();
        p.id = UUID.randomUUID();
        p.tenantId = tenantId; p.contractId = contractId; p.negotiationId = negotiationId; p.partyId = partyId;
        p.kind = k;
        p.baseVersionNo = baseVersionNo;
        p.blockIndex = blockIndex;
        p.originalHtml = originalHtml;
        p.proposedHtml = hasNew ? clean : null;
        p.rationale = rationale == null || rationale.isBlank() ? null : rationale.trim();
        p.status = PENDING;
        p.createdAt = Instant.now();
        p.updatedAt = p.createdAt;
        return p;
    }

    private static boolean sameText(String a, String b) {
        return a != null && b != null && a.replaceAll("\\s+", " ").trim().equals(b.replaceAll("\\s+", " ").trim());
    }

    /** Proposals that need staff attention. */
    public boolean isUnresolved() { return PENDING.equals(status) || CONFLICTED.equals(status); }

    /** Still capable of being decided or applied. */
    public boolean isOpen() { return isUnresolved() || ACCEPTED.equals(status); }

    /** Two proposals clash when they change the same wording (or insert in the same place). */
    public String targetKey() {
        return (ADD.equals(kind) ? "A:" : "B:") + (originalHtml == null ? "" : originalHtml.replaceAll("\\s+", " ").trim());
    }

    public void accept(String note, UUID by) {
        if (!PENDING.equals(status)) throw new IllegalStateException(CONFLICTED.equals(status)
                ? "This proposal conflicts with another change. Reject or apply the other one first."
                : "Only a proposal waiting for review can be accepted (this one is " + status + ")");
        status = ACCEPTED; decidedBy = by; decidedAt = Instant.now(); decisionNote = note; touch();
    }

    public void reject(String reason, UUID by) {
        if (!isUnresolved() && !ACCEPTED.equals(status)) throw new IllegalStateException("This proposal was already decided (" + status + ")");
        if (reason == null || reason.isBlank()) throw new IllegalArgumentException("Say why the proposal is rejected so the counterparty can see it");
        status = REJECTED; decidedBy = by; decidedAt = Instant.now(); decisionNote = reason.trim(); conflictWith = null; touch();
    }

    /** The counterparty takes back their own proposal, while it has not been applied. */
    public void withdraw(UUID byPartyId) {
        if (!partyId.equals(byPartyId)) throw new IllegalStateException("A proposal can only be withdrawn by the party who made it");
        if (!isOpen()) throw new IllegalStateException("This proposal can no longer be withdrawn (it is " + status + ")");
        status = WITHDRAWN; conflictWith = null; touch();
    }

    public void supersede(String note) {
        if (!isOpen()) return;
        status = SUPERSEDED; decisionNote = note; conflictWith = null; touch();
    }

    public void markConflict(UUID with) {
        if (!PENDING.equals(status) && !CONFLICTED.equals(status)) return;
        status = CONFLICTED; conflictWith = with; touch();
    }

    public void clearConflict() {
        if (!CONFLICTED.equals(status)) return;
        status = PENDING; conflictWith = null; touch();
    }

    public void markApplied(int versionNo, UUID by) {
        if (!ACCEPTED.equals(status)) throw new IllegalStateException("Only an accepted proposal can be applied");
        status = APPLIED; appliedVersionNo = versionNo; appliedBy = by; appliedAt = Instant.now(); touch();
    }

    private void touch() { updatedAt = Instant.now(); }
}
