package za.co.handyflow.platform.projects.domain.model;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.NoArgsConstructor;
import za.co.handyflow.platform.projects.domain.enums.ChangeOrderStatus;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

/**
 * A formal request to change a project's scope, cost or schedule.
 *
 * CHUNK 5 CHANGES
 * ───────────────
 * - Lifecycle follows ChangeOrderStatus: DRAFT → SUBMITTED → APPROVED | REJECTED | WITHDRAWN,
 *   and REJECTED → DRAFT (reopened as a NEW revision). Every transition is checked there.
 * - Content can only be edited while DRAFT (updateDetails). The old unguarded setters are gone.
 * - revision: increments each time a rejected change order is reopened. Each submission is kept
 *   in change_order_revisions, so earlier quotations are never overwritten.
 * - Impact notes for scope, risk and procurement (cost and schedule have their own fields).
 * - Client side: sign-off and disputes are tracked here without changing the internal status.
 * - appliedAt / baselineVersion: set when approval is applied to the project baseline.
 */
@Entity
@Table(name = "change_orders")
@Getter
@NoArgsConstructor
public class ChangeOrder {

    @Id UUID id;
    @Column(name = "tenant_id",  nullable = false) UUID       tenantId;
    @Column(name = "project_id", nullable = false) UUID       projectId;
    @Column(name = "change_number", nullable = false, length = 20) String changeNumber;
    @Column(nullable = false, length = 200) String title;
    String description;
    String reason;
    @Column(nullable = false, length = 20) String status = "DRAFT";
    @Column(name = "revision", nullable = false) int revision = 1;

    @Column(name = "cost_impact",     nullable = false) BigDecimal costImpact     = BigDecimal.ZERO;
    @Column(name = "schedule_impact", nullable = false) int        scheduleImpact = 0;  // calendar days
    @Column(name = "scope_impact")       String scopeImpact;
    @Column(name = "risk_impact")        String riskImpact;
    @Column(name = "procurement_impact") String procurementImpact;

    @Column(name = "submitted_by")    UUID    submittedBy;   // the originator
    @Column(name = "submitted_at")    Instant submittedAt;
    @Column(name = "approved_by")     UUID    approvedBy;
    @Column(name = "approved_by_name") String approvedByName;
    @Column(name = "approved_at")     Instant approvedAt;
    @Column(name = "rejection_reason") String  rejectionReason;
    @Column(name = "rejected_at")     Instant rejectedAt;
    @Column(name = "withdrawn_at")    Instant withdrawnAt;
    @Column(name = "withdrawn_reason") String  withdrawnReason;

    @Column(name = "client_approved_at")           Instant clientApprovedAt;
    @Column(name = "client_disputed_at")           Instant clientDisputedAt;
    @Column(name = "client_dispute_reason")        String  clientDisputeReason;
    @Column(name = "client_dispute_resolved_at")   Instant clientDisputeResolvedAt;
    @Column(name = "client_dispute_resolution")    String  clientDisputeResolution;

    @Column(name = "applied_at")        Instant appliedAt;
    @Column(name = "baseline_version")  Integer baselineVersion;

    @Column(name = "created_at", nullable = false) Instant createdAt;
    @Column(name = "updated_at", nullable = false) Instant updatedAt;

    public static ChangeOrder create(UUID tenantId, UUID projectId, String changeNumber,
                                     String title, String description, String reason,
                                     BigDecimal costImpact, int scheduleImpact,
                                     UUID submittedBy) {
        ChangeOrder co  = new ChangeOrder();
        co.id            = UUID.randomUUID();
        co.tenantId      = tenantId;
        co.projectId     = projectId;
        co.changeNumber  = changeNumber;
        co.title         = title;
        co.description   = description;
        co.reason        = reason;
        co.costImpact    = costImpact != null ? costImpact : BigDecimal.ZERO;
        co.scheduleImpact = scheduleImpact;
        co.submittedBy   = submittedBy;
        co.status        = "DRAFT";
        co.createdAt     = Instant.now();
        co.updatedAt     = Instant.now();
        return co;
    }

    // ── Lifecycle ─────────────────────────────────────────────────────────────

    public void submit() {
        transition(ChangeOrderStatus.SUBMITTED);
        this.submittedAt = Instant.now();
        touch();
    }

    public void approve(UUID approverId, String approverName) {
        transition(ChangeOrderStatus.APPROVED);
        this.approvedBy     = approverId;
        this.approvedByName = approverName;
        this.approvedAt     = Instant.now();
        touch();
    }

    public void reject(String reason) {
        transition(ChangeOrderStatus.REJECTED);
        this.rejectionReason = reason;
        this.rejectedAt      = Instant.now();
        touch();
    }

    public void withdraw(String reason) {
        transition(ChangeOrderStatus.WITHDRAWN);
        this.withdrawnReason = reason;
        this.withdrawnAt     = Instant.now();
        touch();
    }

    /** REJECTED → DRAFT as a new revision. The rejection itself stays in change_order_revisions. */
    public void reopenForRevision() {
        transition(ChangeOrderStatus.DRAFT);
        this.revision++;
        this.rejectionReason = null;
        this.rejectedAt      = null;
        this.submittedAt     = null;
        touch();
    }

    /** Content edits are only possible while DRAFT. */
    public void updateDetails(String title, String description, String reason,
                              BigDecimal costImpact, int scheduleImpact,
                              String scopeImpact, String riskImpact, String procurementImpact) {
        if (!ChangeOrderStatus.valueOf(status).isEditable()) {
            throw new IllegalStateException("Only a DRAFT change order can be edited (current: " + status + ")");
        }
        this.title              = title;
        this.description        = description;
        this.reason             = reason;
        this.costImpact         = costImpact != null ? costImpact : BigDecimal.ZERO;
        this.scheduleImpact     = scheduleImpact;
        this.scopeImpact        = scopeImpact;
        this.riskImpact         = riskImpact;
        this.procurementImpact  = procurementImpact;
        touch();
    }

    public void markApplied(int baselineVersion) {
        this.appliedAt        = Instant.now();
        this.baselineVersion = baselineVersion;
        touch();
    }

    // ── Client side (an APPROVED change order only) ───────────────────────────

    public void markClientApproved() {
        requireApproved();
        if (hasOpenClientDispute()) throw new IllegalStateException("Resolve the client dispute before recording client approval");
        this.clientApprovedAt = Instant.now();
        touch();
    }

    public void raiseClientDispute(String reason) {
        requireApproved();
        if (clientApprovedAt != null) throw new IllegalStateException("The client has already approved this change order");
        if (hasOpenClientDispute())   throw new IllegalStateException("A client dispute is already open");
        this.clientDisputedAt         = Instant.now();
        this.clientDisputeReason      = reason;
        this.clientDisputeResolvedAt  = null;
        this.clientDisputeResolution  = null;
        touch();
    }

    public void resolveClientDispute(String resolution) {
        if (!hasOpenClientDispute()) throw new IllegalStateException("There is no open client dispute");
        this.clientDisputeResolvedAt = Instant.now();
        this.clientDisputeResolution = resolution;
        touch();
    }

    public boolean hasOpenClientDispute() {
        return clientDisputedAt != null && clientDisputeResolvedAt == null;
    }

    // ── Helpers ───────────────────────────────────────────────────────────────

    private void transition(ChangeOrderStatus target) {
        ChangeOrderStatus current = ChangeOrderStatus.valueOf(status);
        if (!current.canTransitionTo(target)) {
            throw new IllegalStateException("A " + current + " change order cannot become " + target);
        }
        this.status = target.name();
    }

    private void requireApproved() {
        if (!"APPROVED".equals(status)) {
            throw new IllegalStateException("Only an APPROVED change order can be signed off or disputed by the client");
        }
    }

    private void touch() { this.updatedAt = Instant.now(); }
}