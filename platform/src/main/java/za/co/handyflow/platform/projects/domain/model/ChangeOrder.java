package za.co.handyflow.platform.projects.domain.model;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

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
    // status: DRAFT | SUBMITTED | APPROVED | REJECTED
    @Column(name = "cost_impact",     nullable = false) BigDecimal costImpact     = BigDecimal.ZERO;
    @Column(name = "schedule_impact", nullable = false) int        scheduleImpact = 0;  // days
    @Column(name = "submitted_by")    UUID    submittedBy;
    @Column(name = "submitted_at")    Instant submittedAt;
    @Column(name = "approved_by")     UUID    approvedBy;
    @Column(name = "approved_by_name") String approvedByName;
    @Column(name = "approved_at")     Instant approvedAt;
    @Column(name = "client_approved_at") Instant clientApprovedAt;
    @Column(name = "rejection_reason") String  rejectionReason;
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

    public void submit() {
        requireStatus("DRAFT");
        this.status      = "SUBMITTED";
        this.submittedAt = Instant.now();
        touch();
    }

    public void approve(UUID approverId, String approverName) {
        requireStatus("SUBMITTED");
        this.status          = "APPROVED";
        this.approvedBy      = approverId;
        this.approvedByName  = approverName;
        this.approvedAt      = Instant.now();
        touch();
    }

    public void markClientApproved() {
        this.clientApprovedAt = Instant.now();
        touch();
    }

    public void reject(String reason) {
        requireStatus("SUBMITTED");
        this.status          = "REJECTED";
        this.rejectionReason = reason;
        touch();
    }

    public void setTitle(String v)       { this.title       = v; }
    public void setDescription(String v) { this.description = v; }
    public void setReason(String v)      { this.reason      = v; }
    public void setCostImpact(BigDecimal v){ this.costImpact = v; }
    public void setScheduleImpact(int v) { this.scheduleImpact = v; }

    private void requireStatus(String expected) {
        if (!expected.equals(status))
            throw new IllegalStateException("Expected " + expected + " but status is " + status);
    }
    private void touch() { this.updatedAt = Instant.now(); }
}
