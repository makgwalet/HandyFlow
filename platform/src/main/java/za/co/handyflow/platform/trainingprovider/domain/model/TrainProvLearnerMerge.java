package za.co.handyflow.platform.trainingprovider.domain.model;

import jakarta.persistence.*;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;
import za.co.handyflow.platform.shared.TenantId;
import za.co.handyflow.platform.trainingprovider.domain.rules.LearnerMatchRules;

import java.time.Instant;
import java.util.UUID;

/** A proposed or finished merge of two learner records. Decided once; never reopened. */
@Entity(name = "TrainProvLearnerMerge")
@Table(name = "trainprov_learner_merges")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class TrainProvLearnerMerge {
    @Id
    private UUID id = UUID.randomUUID();
    @Column(name = "tenant_id", nullable = false)
    private UUID tenantId;
    @Column(name = "survivor_id", nullable = false)
    private UUID survivorId;
    @Column(name = "merged_id", nullable = false)
    private UUID mergedId;
    @Column(nullable = false)
    private String status = "PROPOSED";
    @Column(name = "match_reasons")
    private String matchReasons;
    private String conflicts;
    @Column(name = "conflicts_accepted", nullable = false)
    private boolean conflictsAccepted;
    @Column(nullable = false)
    private String reason;
    @Column(name = "survivor_snapshot", nullable = false)
    private String survivorSnapshot;
    @Column(name = "merged_snapshot", nullable = false)
    private String mergedSnapshot;
    @Column(name = "proposed_by", nullable = false)
    private UUID proposedBy;
    @Column(name = "proposed_at", nullable = false)
    private Instant proposedAt;
    @Column(name = "decided_by")
    private UUID decidedBy;
    @Column(name = "decided_at")
    private Instant decidedAt;
    @Column(name = "decision_reason")
    private String decisionReason;
    @Column(name = "confirmed_alone", nullable = false)
    private boolean confirmedAlone;
    @Column(name = "moved_enrolments")
    private Integer movedEnrolments;
    @Column(name = "moved_applications")
    private Integer movedApplications;
    @Column(name = "moved_documents")
    private Integer movedDocuments;
    @Column(name = "delegate_note")
    private String delegateNote;
    @Version
    private Long version;

    public static TrainProvLearnerMerge propose(TenantId t, UUID survivorId, UUID mergedId, String matchReasons, String conflicts,
                                                boolean conflictsAccepted, String reason, String survivorSnapshot, String mergedSnapshot, UUID by) {
        TrainProvLearnerMerge m = new TrainProvLearnerMerge();
        m.tenantId = t.getValue();
        m.survivorId = survivorId;
        m.mergedId = mergedId;
        m.matchReasons = blank(matchReasons);
        m.conflicts = blank(conflicts);
        m.conflictsAccepted = conflictsAccepted;
        m.reason = reason.trim();
        m.survivorSnapshot = survivorSnapshot;
        m.mergedSnapshot = mergedSnapshot;
        m.proposedBy = by;
        m.proposedAt = Instant.now();
        return m;
    }

    public void complete(UUID by, String decisionReason, boolean confirmAlone, String singleReason, int enrolments, int applications,
                         int documents, String delegateNote) {
        decide("COMPLETED", by, decisionReason, confirmAlone, singleReason);
        this.movedEnrolments = enrolments;
        this.movedApplications = applications;
        this.movedDocuments = documents;
        this.delegateNote = blank(delegateNote);
    }

    public void reject(UUID by, String decisionReason, boolean confirmAlone, String singleReason) {
        decide("REJECTED", by, decisionReason, confirmAlone, singleReason);
    }

    /** The person who proposed it takes it back. */
    public void cancel(UUID by, String decisionReason) {
        if (!by.equals(proposedBy)) {
            throw new IllegalStateException("Only the person who proposed the merge can cancel it. Anyone else can reject it");
        }
        if (!LearnerMatchRules.reasonOk(decisionReason)) {
            throw new IllegalArgumentException("Say why, in at least " + LearnerMatchRules.MIN_REASON + " characters");
        }
        if (!"PROPOSED".equals(status)) {
            throw new IllegalStateException("This merge has already been decided");
        }
        this.status = "CANCELLED";
        this.decidedBy = by;
        this.decidedAt = Instant.now();
        this.decisionReason = decisionReason.trim();
    }

    private void decide(String to, UUID by, String decisionReason, boolean confirmAlone, String singleReason) {
        String p = LearnerMatchRules.decisionProblem(status, proposedBy, by, confirmAlone, singleReason, decisionReason);
        if (p != null) {
            throw new IllegalStateException(p);
        }
        this.status = to;
        this.decidedBy = by;
        this.decidedAt = Instant.now();
        this.confirmedAlone = by.equals(proposedBy);
        this.decisionReason = confirmedAlone ? decisionReason.trim() + " (confirmed alone: " + singleReason.trim() + ")" : decisionReason.trim();
    }

    private static String blank(String s) {
        return s == null || s.isBlank() ? null : s.trim();
    }
}
