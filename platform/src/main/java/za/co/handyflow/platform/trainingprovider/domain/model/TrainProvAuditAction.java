package za.co.handyflow.platform.trainingprovider.domain.model;

import jakarta.persistence.*;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;
import za.co.handyflow.platform.shared.TenantId;
import za.co.handyflow.platform.trainingprovider.domain.rules.AuditRules;

import java.time.Instant;
import java.time.LocalDate;
import java.util.UUID;

/** A corrective action from an audit. Done by its owner, verified by a second person or with a written reason; cancelled with a reason, never deleted. */
@Entity(name = "TrainProvAuditAction")
@Table(name = "trainprov_audit_actions")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class TrainProvAuditAction {
    @Id
    private UUID id = UUID.randomUUID();
    @Column(name = "tenant_id", nullable = false, updatable = false)
    private UUID tenantId;
    @Column(name = "audit_id", nullable = false, updatable = false)
    private UUID auditId;
    @Column(name = "item_id", updatable = false)
    private UUID itemId;
    @Column(nullable = false, updatable = false)
    private String description;
    @Column(name = "owner_name", nullable = false, updatable = false)
    private String ownerName;
    @Column(name = "due_on", nullable = false, updatable = false)
    private LocalDate dueOn;
    @Column(nullable = false)
    private String status = AuditRules.OPEN;
    @Column(name = "done_on")
    private LocalDate doneOn;
    @Column(name = "done_note")
    private String doneNote;
    @Column(name = "done_by")
    private UUID doneBy;
    @Column(name = "verified_by")
    private UUID verifiedBy;
    @Column(name = "verified_at")
    private Instant verifiedAt;
    @Column(name = "verified_alone_reason")
    private String verifiedAloneReason;
    @Column(name = "cancelled_reason")
    private String cancelledReason;
    @Column(name = "created_by", nullable = false, updatable = false)
    private UUID createdBy;
    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;
    @Version
    private Long version;

    public static TrainProvAuditAction create(TenantId t, UUID auditId, UUID itemId, String description, String owner, LocalDate dueOn, LocalDate today, UUID by) {
        String p = AuditRules.actionProblem(description, owner, dueOn, today);
        if (p != null) {
            throw new IllegalArgumentException(p);
        }
        TrainProvAuditAction a = new TrainProvAuditAction();
        a.tenantId = t.getValue();
        a.auditId = auditId;
        a.itemId = itemId;
        a.description = description.trim();
        a.ownerName = owner.trim();
        a.dueOn = dueOn;
        a.createdBy = by;
        a.createdAt = Instant.now();
        return a;
    }

    public void markDone(LocalDate today, String note, UUID by) {
        if (!AuditRules.OPEN.equals(status)) {
            throw new IllegalStateException("Only an open action can be marked done");
        }
        String p = AuditRules.noteProblem(note);
        if (p != null) {
            throw new IllegalArgumentException(p);
        }
        status = AuditRules.DONE;
        doneOn = today;
        doneNote = note.trim();
        doneBy = by;
    }

    public void verify(UUID by, boolean confirmAlone, String aloneReason) {
        if (!AuditRules.DONE.equals(status) || verifiedBy != null) {
            throw new IllegalStateException("Only a done action that is not yet verified can be verified");
        }
        String p = AuditRules.secondPersonProblem(doneBy, by, confirmAlone, aloneReason, "verify this action");
        if (p != null) {
            throw new IllegalStateException(p);
        }
        verifiedBy = by;
        verifiedAt = Instant.now();
        verifiedAloneReason = doneBy.equals(by) ? aloneReason.trim() : null;
    }

    public void cancel(String reason) {
        if (!AuditRules.OPEN.equals(status)) {
            throw new IllegalStateException("Only an open action can be cancelled");
        }
        String p = AuditRules.noteProblem(reason);
        if (p != null) {
            throw new IllegalArgumentException(p);
        }
        status = "CANCELLED";
        cancelledReason = reason.trim();
    }
}
