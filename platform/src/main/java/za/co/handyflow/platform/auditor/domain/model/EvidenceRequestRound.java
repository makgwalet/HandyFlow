package za.co.handyflow.platform.auditor.domain.model;

import jakarta.persistence.*;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.time.Instant;
import java.util.LinkedHashSet;
import java.util.Set;
import java.util.UUID;

/** One answer from the business to an evidence request: files, or a statement that the item is not available. */
@Entity(name = "EvidenceRequestRound")
@Table(name = "audit_evidence_request_rounds")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class EvidenceRequestRound {
    public static final String SUBMISSION = "SUBMISSION";
    public static final String NOT_AVAILABLE = "NOT_AVAILABLE";

    @Id private UUID id = UUID.randomUUID();
    @Column(name = "tenant_id", nullable = false) private UUID tenantId;
    @Column(name = "request_id", nullable = false) private UUID requestId;
    @Column(name = "round_no", nullable = false) private int roundNo;
    @Column(name = "kind", nullable = false) private String kind;
    @Column(name = "note", nullable = false, columnDefinition = "TEXT") private String note;
    @Column(name = "submitted_by", nullable = false) private UUID submittedBy;
    @Column(name = "submitted_at", nullable = false) private Instant submittedAt = Instant.now();
    @Column(name = "decision") private String decision;
    @Column(name = "decision_note", columnDefinition = "TEXT") private String decisionNote;
    @Column(name = "decided_by") private UUID decidedBy;
    @Column(name = "decided_at") private Instant decidedAt;

    @ElementCollection(fetch = FetchType.EAGER)
    @CollectionTable(name = "audit_evidence_request_round_files", joinColumns = @JoinColumn(name = "round_id"))
    @Column(name = "evidence_id")
    private Set<UUID> evidenceIds = new LinkedHashSet<>();

    static EvidenceRequestRound of(UUID tenantId, UUID requestId, int roundNo, String kind, String note, UUID by, Set<UUID> files) {
        EvidenceRequestRound r = new EvidenceRequestRound();
        r.tenantId = tenantId; r.requestId = requestId; r.roundNo = roundNo; r.kind = kind; r.note = note; r.submittedBy = by;
        r.evidenceIds = new LinkedHashSet<>(files);
        return r;
    }

    void decide(String decision, String note, UUID by) {
        this.decision = decision; this.decisionNote = note; this.decidedBy = by; this.decidedAt = Instant.now();
    }

    public boolean isDecided() { return decision != null; }
}
