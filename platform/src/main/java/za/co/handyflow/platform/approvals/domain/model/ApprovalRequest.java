package za.co.handyflow.platform.approvals.domain.model;

import jakarta.persistence.*;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;
import za.co.handyflow.platform.shared.TenantId;

import java.time.Instant;
import java.util.UUID;

/**
 * ApprovalRequest — one submission of one entity for approval. See the
 * 1.1 design doc for the full state-machine rationale (in particular
 * why RETURNED_FOR_CORRECTION/RESUBMITTED are distinct from a hard
 * REJECTED — a resubmission creates a NEW ApprovalRequest linked via
 * resubmittedFromId, never a mutated one, so the history of "attempt 1
 * was rejected, attempt 2 was approved" stays two real, queryable
 * records).
 * <p>
 * metadata is a JSONB snapshot of whatever fields the matched
 * ApprovalRule's conditions needed (e.g. {"totalAmount": 15000}),
 * captured AT SUBMISSION TIME. The engine evaluates against this
 * snapshot, never a live re-fetch of the source entity — a rule's
 * decision has to be reproducible even if the underlying bill/proof/etc.
 * changes after submission.
 */
@Entity
@Table(name = "approval_requests")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class ApprovalRequest {

    @Id
    private UUID id = UUID.randomUUID();

    @Embedded
    @AttributeOverride(name = "value", column = @Column(name = "tenant_id", nullable = false))
    private TenantId tenantId;

    @Column(nullable = false, length = 50) private String module;
    @Column(name = "entity_type", nullable = false, length = 50) private String entityType;
    @Column(name = "entity_id", nullable = false) private UUID entityId;

    @Column(name = "rule_id") private UUID ruleId; // nullable — an ad-hoc request without a matched rule is possible

    /**
     * Denormalized from the matched ApprovalRule at submission time,
     * not re-read from the rule later. A rule can be edited or
     * deactivated while a request is still in flight — the request has
     * to keep behaving per the mode it was actually submitted under,
     * not whatever the rule currently says.
     */
    @Enumerated(EnumType.STRING)
    @Column(name = "approval_mode", length = 20)
    private ApprovalRule.ApprovalMode approvalMode; // nullable — absent when no rule matched (auto-approved, no steps)

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 30)
    private Status status;

    @Column(name = "submitted_by") private UUID submittedBy;
    @Column(name = "submitted_at", nullable = false, updatable = false) private Instant submittedAt;
    @Column(name = "completed_at") private Instant completedAt;

    @Column(name = "resubmitted_from_id") private UUID resubmittedFromId;

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(columnDefinition = "jsonb")
    private String metadata;

    public enum Status {
        SUBMITTED, IN_PROGRESS, APPROVED, REJECTED, RETURNED_FOR_CORRECTION, RESUBMITTED, CANCELLED
    }

    public static ApprovalRequest submit(TenantId tenantId, String module, String entityType,
                                         UUID entityId, UUID ruleId, ApprovalRule.ApprovalMode approvalMode,
                                         UUID submittedBy, String metadata, UUID resubmittedFromId) {
        ApprovalRequest r = new ApprovalRequest();
        r.tenantId = tenantId;
        r.module = module;
        r.entityType = entityType;
        r.entityId = entityId;
        r.ruleId = ruleId;
        r.approvalMode = approvalMode;
        r.submittedBy = submittedBy;
        r.metadata = metadata;
        r.resubmittedFromId = resubmittedFromId;
        r.status = Status.SUBMITTED;
        r.submittedAt = Instant.now();
        return r;
    }

    public void markInProgress() {
        if (status == Status.SUBMITTED) this.status = Status.IN_PROGRESS;
    }

    public void complete(Status outcome) {
        if (outcome != Status.APPROVED && outcome != Status.REJECTED && outcome != Status.RETURNED_FOR_CORRECTION) {
            throw new IllegalArgumentException("complete() only accepts a terminal outcome");
        }
        this.status = outcome;
        this.completedAt = Instant.now();
    }

    public void markResubmitted() {
        this.status = Status.RESUBMITTED;
        this.completedAt = Instant.now();
    }

    public void cancel() {
        this.status = Status.CANCELLED;
        this.completedAt = Instant.now();
    }

    public boolean isTerminal() {
        return status == Status.APPROVED || status == Status.REJECTED
                || status == Status.RETURNED_FOR_CORRECTION || status == Status.RESUBMITTED
                || status == Status.CANCELLED;
    }
}