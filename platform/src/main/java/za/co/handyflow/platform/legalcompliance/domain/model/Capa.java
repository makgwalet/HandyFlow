package za.co.handyflow.platform.legalcompliance.domain.model;

import jakarta.persistence.*;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;
import za.co.handyflow.platform.shared.AggregateRoot;
import za.co.handyflow.platform.shared.TenantId;

import java.time.Instant;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * A corrective or preventive action against exactly one finding (control-test finding or audit finding) or incident.
 *
 * PROPOSED → APPROVED → IN_PROGRESS → COMPLETED → VERIFIED, with CANCELLED from any non-final state.
 *  - Approval is by someone other than the person who proposed it and other than the action owner (unconditional).
 *  - Completion needs a note and at least one evidence file (snapshotted).
 *  - Verification is by someone other than the owner and the person who completed it (unconditional). A verifier who finds the action
 *    did not work sends it back to IN_PROGRESS; nothing is deleted and the reopen count is kept.
 */
@Entity
@Table(name = "legalcompliance_capas")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class Capa extends AggregateRoot<Capa> {

    public static final int MIN_NOTE = 15;

    @Column(nullable = false, length = 20) private String reference;
    @Column(name = "finding_id") private UUID findingId;
    @Column(name = "audit_finding_id") private UUID auditFindingId;
    @Column(name = "incident_id") private UUID incidentId;
    @Enumerated(EnumType.STRING) @Column(name = "capa_type", nullable = false, length = 10) private CapaType capaType;
    @Column(nullable = false, length = 255) private String title;
    @Column(name = "action_plan", nullable = false, columnDefinition = "TEXT") private String actionPlan;
    @Column(name = "root_cause", nullable = false, columnDefinition = "TEXT") private String rootCause;
    @Column(name = "owner_id") private UUID ownerId;
    @Column(name = "owner_name", nullable = false, length = 255) private String ownerName;
    @Column(name = "due_date", nullable = false) private LocalDate dueDate;
    @Enumerated(EnumType.STRING) @Column(nullable = false, length = 12) private CapaStatus status = CapaStatus.PROPOSED;
    @Column(name = "approved_by") private UUID approvedBy;
    @Column(name = "approved_by_name", length = 255) private String approvedByName;
    @Column(name = "approved_at") private Instant approvedAt;
    @Column(name = "approval_note", columnDefinition = "TEXT") private String approvalNote;
    @Column(name = "returned_count", nullable = false) private int returnedCount;
    @Column(name = "started_at") private Instant startedAt;
    @Column(name = "completed_by") private UUID completedBy;
    @Column(name = "completed_by_name", length = 255) private String completedByName;
    @Column(name = "completed_at") private Instant completedAt;
    @Column(name = "completion_note", columnDefinition = "TEXT") private String completionNote;
    @JdbcTypeCode(SqlTypes.JSON) @Column(name = "evidence_snapshot", nullable = false, columnDefinition = "jsonb") private List<Map<String, Object>> evidenceSnapshot = new ArrayList<>();
    @Column(name = "verified_by") private UUID verifiedBy;
    @Column(name = "verified_by_name", length = 255) private String verifiedByName;
    @Column(name = "verified_at") private Instant verifiedAt;
    @Column(name = "verification_note", columnDefinition = "TEXT") private String verificationNote;
    @Column(name = "reopened_count", nullable = false) private int reopenedCount;
    @Column(name = "cancel_reason", columnDefinition = "TEXT") private String cancelReason;
    @Column(name = "created_by") private UUID createdBy;
    @Column(name = "created_by_name", length = 255) private String createdByName;

    /** Exactly one of findingId / auditFindingId / incidentId must be given. */
    public static Capa propose(TenantId tenantId, String reference, UUID findingId, UUID auditFindingId, UUID incidentId, CapaType type, String title, String actionPlan,
                               String rootCause, UUID ownerId, String ownerName, LocalDate dueDate, UUID by, String byName) {
        int parents = (findingId != null ? 1 : 0) + (auditFindingId != null ? 1 : 0) + (incidentId != null ? 1 : 0);
        if (parents != 1) throw new IllegalArgumentException("An action must belong to exactly one finding or incident");
        Capa c = new Capa();
        c.initTenantId(tenantId);
        c.reference = reference;
        c.findingId = findingId;
        c.auditFindingId = auditFindingId;
        c.incidentId = incidentId;
        c.createdBy = by;
        c.createdByName = byName;
        c.apply(type, title, actionPlan, rootCause, ownerId, ownerName, dueDate);
        return c;
    }

    /** The proposer revises a returned (or not yet approved) action. */
    public void revise(CapaType type, String title, String actionPlan, String rootCause, UUID ownerId, String ownerName, LocalDate dueDate) {
        if (status != CapaStatus.PROPOSED) throw new IllegalStateException("Only a proposed action can be revised — current: " + status);
        apply(type, title, actionPlan, rootCause, ownerId, ownerName, dueDate);
    }

    private void apply(CapaType type, String title, String actionPlan, String rootCause, UUID ownerId, String ownerName, LocalDate dueDate) {
        if (type == null) throw new IllegalArgumentException("An action type is required");
        if (title == null || title.isBlank()) throw new IllegalArgumentException("A title is required");
        if (actionPlan == null || actionPlan.trim().length() < MIN_NOTE) throw new IllegalArgumentException("Describe the action (at least " + MIN_NOTE + " characters)");
        if (rootCause == null || rootCause.trim().length() < MIN_NOTE) throw new IllegalArgumentException("State the root cause (at least " + MIN_NOTE + " characters)");
        if (ownerName == null || ownerName.isBlank()) throw new IllegalArgumentException("An owner is required");
        if (dueDate == null) throw new IllegalArgumentException("A due date is required");
        this.capaType = type;
        this.title = title.trim();
        this.actionPlan = actionPlan.trim();
        this.rootCause = rootCause.trim();
        this.ownerId = ownerId;
        this.ownerName = ownerName.trim();
        this.dueDate = dueDate;
    }

    public void approve(UUID by, String byName, String note) {
        if (status != CapaStatus.PROPOSED) throw new IllegalStateException("Only a proposed action can be approved — current: " + status);
        if (by != null && (by.equals(createdBy) || by.equals(ownerId))) throw new IllegalStateException("Segregation of duties: you proposed or own this action, so you cannot approve it");
        this.approvedBy = by;
        this.approvedByName = byName;
        this.approvedAt = Instant.now();
        this.approvalNote = note == null || note.isBlank() ? null : note.trim();
        this.status = CapaStatus.APPROVED;
    }

    /** The approver sends the proposal back for rework; it stays PROPOSED. */
    public void returnForRework(UUID by, String byName, String note) {
        if (status != CapaStatus.PROPOSED) throw new IllegalStateException("Only a proposed action can be returned — current: " + status);
        if (by != null && (by.equals(createdBy) || by.equals(ownerId))) throw new IllegalStateException("Segregation of duties: you proposed or own this action, so you cannot review it");
        if (note == null || note.trim().length() < MIN_NOTE) throw new IllegalArgumentException("Say what must change (at least " + MIN_NOTE + " characters)");
        this.approvedBy = by;
        this.approvedByName = byName;
        this.approvedAt = Instant.now();
        this.approvalNote = note.trim();
        this.returnedCount++;
    }

    public void start() {
        if (status != CapaStatus.APPROVED) throw new IllegalStateException("Only an approved action can be started — current: " + status);
        this.startedAt = Instant.now();
        this.status = CapaStatus.IN_PROGRESS;
    }

    public void complete(UUID by, String byName, String note, List<Map<String, Object>> evidence) {
        if (status != CapaStatus.IN_PROGRESS) throw new IllegalStateException("Only an action in progress can be completed — current: " + status);
        if (note == null || note.trim().length() < MIN_NOTE) throw new IllegalArgumentException("Describe what was done (at least " + MIN_NOTE + " characters)");
        if (evidence == null || evidence.isEmpty()) throw new IllegalArgumentException("Attach at least one evidence file that shows the action was carried out");
        this.completedBy = by;
        this.completedByName = byName;
        this.completedAt = Instant.now();
        this.completionNote = note.trim();
        this.evidenceSnapshot = new ArrayList<>(evidence);
        this.status = CapaStatus.COMPLETED;
    }

    /** effective=true verifies the action; effective=false sends it back to IN_PROGRESS. */
    public void verify(boolean effective, UUID by, String byName, String note) {
        if (status != CapaStatus.COMPLETED) throw new IllegalStateException("Only a completed action can be verified — current: " + status);
        if (by != null && (by.equals(ownerId) || by.equals(completedBy))) throw new IllegalStateException("Segregation of duties: the owner or the person who completed the action cannot verify it");
        if (note == null || note.trim().length() < MIN_NOTE) throw new IllegalArgumentException("A verification note of at least " + MIN_NOTE + " characters is required");
        this.verifiedBy = by;
        this.verifiedByName = byName;
        this.verifiedAt = Instant.now();
        this.verificationNote = note.trim();
        if (effective) {
            this.status = CapaStatus.VERIFIED;
        } else {
            this.reopenedCount++;
            this.status = CapaStatus.IN_PROGRESS;
        }
    }

    public void cancel(String reason) {
        if (status == CapaStatus.VERIFIED || status == CapaStatus.CANCELLED) throw new IllegalStateException("The action is already " + status);
        if (reason == null || reason.trim().length() < MIN_NOTE) throw new IllegalArgumentException("A reason of at least " + MIN_NOTE + " characters is required");
        this.cancelReason = reason.trim();
        this.status = CapaStatus.CANCELLED;
    }

    public boolean isOpen() { return status != CapaStatus.VERIFIED && status != CapaStatus.CANCELLED; }
    public boolean isOverdue(LocalDate today) { return isOpen() && dueDate.isBefore(today); }
}
