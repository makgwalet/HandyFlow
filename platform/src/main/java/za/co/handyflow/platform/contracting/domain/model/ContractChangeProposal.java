package za.co.handyflow.platform.contracting.domain.model;

import jakarta.persistence.*;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.time.Instant;
import java.util.Collection;
import java.util.UUID;
import java.util.stream.Collectors;

/** A proposed revision of a contract body, reviewed block by block. It is applied or rejected once; it is never edited. */
@Entity
@Table(name = "contract_change_proposals")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class ContractChangeProposal {
    @Id private UUID id;
    @Column(name = "tenant_id") private UUID tenantId;
    @Column(name = "contract_id") private UUID contractId;
    @Column(name = "base_version_no") private int baseVersionNo;
    @Column(name = "proposed_body", columnDefinition = "TEXT") private String proposedBody;
    private String note;
    private String status;
    @Column(name = "proposed_by") private UUID proposedBy;
    @Column(name = "created_at") private Instant createdAt;
    @Column(name = "decided_by") private UUID decidedBy;
    @Column(name = "decided_at") private Instant decidedAt;
    @Column(name = "decision_note") private String decisionNote;
    @Column(name = "accepted_hunks") private String acceptedHunks;
    @Column(name = "applied_version_no") private Integer appliedVersionNo;

    public static ContractChangeProposal create(UUID tenantId, UUID contractId, int baseVersionNo, String proposedBody, String note, UUID by) {
        ContractChangeProposal p = new ContractChangeProposal();
        p.id = UUID.randomUUID();
        p.tenantId = tenantId;
        p.contractId = contractId;
        p.baseVersionNo = baseVersionNo;
        p.proposedBody = proposedBody;
        p.note = note;
        p.status = "OPEN";
        p.proposedBy = by;
        p.createdAt = Instant.now();
        return p;
    }

    public boolean isOpen() { return "OPEN".equals(status); }

    public void markApplied(Collection<Integer> accepted, int versionNo, String note, UUID by) {
        requireOpen();
        status = "APPLIED";
        acceptedHunks = accepted.stream().sorted().map(String::valueOf).collect(Collectors.joining(","));
        appliedVersionNo = versionNo;
        decisionNote = note;
        decidedBy = by;
        decidedAt = Instant.now();
    }

    public void markRejected(String reason, UUID by) {
        requireOpen();
        if (reason == null || reason.isBlank()) throw new IllegalArgumentException("Say why the proposal is being rejected");
        status = "REJECTED";
        decisionNote = reason;
        decidedBy = by;
        decidedAt = Instant.now();
    }

    private void requireOpen() {
        if (!isOpen()) throw new IllegalStateException("This proposal was already decided (" + status + ")");
    }
}
