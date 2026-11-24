package za.co.handyflow.platform.contracting.domain.model;

import jakarta.persistence.*;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.time.Instant;
import java.util.UUID;

/**
 * One request for a second person to approve an action on a subject. It is bound to a hash of exactly what the approver reviewed:
 * if the subject changes afterwards the approval no longer matches and is invalidated, so the action cannot go ahead on a stale approval.
 */
@Entity
@Table(name = "contract_approvals")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class ContractApproval {
    public static final int MIN_EXCEPTION_REASON = 15;

    @Id private UUID id;
    @Column(name = "tenant_id") private UUID tenantId;
    @Enumerated(EnumType.STRING) private ApprovalAction action;
    @Column(name = "subject_id") private UUID subjectId;
    @Column(name = "subject_label") private String subjectLabel;
    @Column(name = "subject_hash") private String subjectHash;
    private String status;
    @Column(name = "requested_by") private UUID requestedBy;
    @Column(name = "requested_at") private Instant requestedAt;
    @Column(name = "request_note") private String requestNote;
    @Column(name = "decided_by") private UUID decidedBy;
    @Column(name = "decided_at") private Instant decidedAt;
    @Column(name = "decision_note") private String decisionNote;
    @Column(name = "sole_user_exception") private boolean soleUserException;
    @Column(name = "exception_reason") private String exceptionReason;
    @Column(name = "closed_at") private Instant closedAt;
    // Follow-up. Routing only: who is reminded first, and how far the reminders have got. None of it decides the approval.
    @Column(name = "assigned_to") private UUID assignedTo;
    @Column(name = "assigned_by") private UUID assignedBy;
    @Column(name = "assigned_at") private Instant assignedAt;
    @Column(name = "escalation_stage") private int escalationStage;
    @Column(name = "escalation_stage_at") private Instant escalationStageAt;

    public static ContractApproval request(UUID tenantId, ApprovalAction action, UUID subjectId, String label, String hash, UUID by, String note) {
        ContractApproval a = new ContractApproval();
        a.id = UUID.randomUUID();
        a.tenantId = tenantId; a.action = action; a.subjectId = subjectId;
        a.subjectLabel = clip(label, 300); a.subjectHash = hash;
        a.status = "PENDING";
        a.requestedBy = by; a.requestedAt = Instant.now(); a.requestNote = clip(note, 1000);
        return a;
    }

    public boolean isPending() { return "PENDING".equals(status); }
    public boolean isApproved() { return "APPROVED".equals(status); }
    public boolean isOpen() { return isPending() || isApproved(); }

    /** Separation of duties: the requester cannot approve their own request unless a sole-user exception is recorded with a reason. */
    public void approve(UUID by, String note, boolean exception, String exceptionReason, boolean exceptionAllowed) {
        if (!isPending()) throw new IllegalStateException("This request has already been " + status.toLowerCase());
        boolean self = requestedBy.equals(by);
        if (self && !exception)
            throw new IllegalStateException("You requested this, so someone else must approve it");
        if (exception) {
            if (!self) throw new IllegalStateException("A sole-user exception only applies when you approve your own request");
            if (!exceptionAllowed) throw new IllegalStateException("This organisation does not allow sole-user exceptions for this action");
            if (exceptionReason == null || exceptionReason.trim().length() < MIN_EXCEPTION_REASON)
                throw new IllegalArgumentException("Give the reason there is no one else who can approve (at least " + MIN_EXCEPTION_REASON + " characters)");
            this.soleUserException = true;
            this.exceptionReason = clip(exceptionReason.trim(), 1000);
        }
        this.status = "APPROVED"; this.decidedBy = by; this.decidedAt = Instant.now(); this.decisionNote = clip(note, 1000);
    }

    public void reject(UUID by, String note) {
        if (!isPending()) throw new IllegalStateException("This request has already been " + status.toLowerCase());
        if (requestedBy.equals(by)) throw new IllegalStateException("You requested this; withdraw it instead of rejecting it");
        if (note == null || note.isBlank()) throw new IllegalArgumentException("Say why it is rejected");
        this.status = "REJECTED"; this.decidedBy = by; this.decidedAt = Instant.now(); this.decisionNote = clip(note.trim(), 1000);
        this.closedAt = decidedAt;
    }

    public void cancel(UUID by) {
        if (!isOpen()) throw new IllegalStateException("This request is already " + status.toLowerCase());
        if (!requestedBy.equals(by)) throw new IllegalStateException("Only the person who requested it can withdraw it");
        this.status = "CANCELLED"; this.closedAt = Instant.now();
    }

    /** The subject changed after the request was made (or approved). */
    /** Names who is reminded first (or, with null, hands it back to everyone who can approve). Does not stop anyone else from deciding it. */
    public void assign(UUID to, UUID by) {
        if (!isPending()) throw new IllegalStateException("This request is already " + status.toLowerCase());
        if (to != null && to.equals(requestedBy)) throw new IllegalStateException("The person who asked cannot be the one asked to approve it");
        this.assignedTo = to; this.assignedBy = to == null ? null : by; this.assignedAt = to == null ? null : Instant.now();
    }

    public EscalationRules.Stage escalationStage() { return EscalationRules.Stage.ofLevel(escalationStage); }

    /** Records that a reminder or the escalation went out. The stage only goes up, and only while the request is still waiting. */
    public void reachStage(EscalationRules.Stage stage, Instant at) {
        if (!isPending()) throw new IllegalStateException("This request is already " + status.toLowerCase());
        if (stage.level() <= escalationStage) throw new IllegalStateException("That step has already been taken");
        this.escalationStage = stage.level(); this.escalationStageAt = at;
    }

    public void invalidate() {
        if (!isOpen()) return;
        this.status = "INVALIDATED"; this.closedAt = Instant.now();
    }

    /** Used up by the action it approved; cannot approve a second time. */
    public void consume() {
        if (!isApproved()) throw new IllegalStateException("Not approved");
        this.status = "CONSUMED"; this.closedAt = Instant.now();
    }

    private static String clip(String s, int n) { return s == null ? null : s.length() > n ? s.substring(0, n) : s; }
}
