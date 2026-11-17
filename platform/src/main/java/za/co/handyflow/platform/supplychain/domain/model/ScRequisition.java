package za.co.handyflow.platform.supplychain.domain.model;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.NoArgsConstructor;
import za.co.handyflow.platform.supplychain.domain.enums.RequisitionStatus;
import za.co.handyflow.platform.supplychain.domain.enums.RequisitionType;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.util.UUID;

@Entity
@Table(name = "sc_requisitions")
@Getter
@NoArgsConstructor
public class ScRequisition {

    @Id UUID id;
    @Column(name = "tenant_id", nullable = false) UUID tenantId;
    @Column(name = "requisition_number", nullable = false, length = 20) String requisitionNumber;
    @Enumerated(EnumType.STRING)
    @Column(name = "requisition_type", nullable = false, length = 30) RequisitionType type;
    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20) RequisitionStatus status = RequisitionStatus.DRAFT;
    @Column(nullable = false, length = 200) String title;
    @Column(name = "business_reason", nullable = false, length = 1000) String businessReason;
    @Column(length = 100) String department;
    @Column(name = "cost_centre", length = 100) String costCentre;
    @Column(name = "project_ref", length = 100) String projectRef;
    @Column(name = "deliver_to_location") UUID deliverToLocation;
    @Column(name = "required_by_date") LocalDate requiredByDate;
    @Column(nullable = false, length = 3) String currency = "ZAR";
    @Column(name = "estimated_total", nullable = false, precision = 15, scale = 2) BigDecimal estimatedTotal = BigDecimal.ZERO;
    @Column(name = "requested_by", nullable = false) UUID requestedBy;
    @Column(name = "requested_by_name", length = 255) String requestedByName;
    @Column(name = "submitted_at") Instant submittedAt;
    @Column(name = "approved_by") UUID approvedBy;
    @Column(name = "approved_by_name", length = 255) String approvedByName;
    @Column(name = "approved_at") Instant approvedAt;
    @Column(name = "decision_note", length = 500) String decisionNote;
    @Column(name = "created_at", nullable = false) Instant createdAt;
    @Column(name = "updated_at", nullable = false) Instant updatedAt;

    public static ScRequisition create(UUID tenantId, String number, RequisitionType type, String title,
                                       String businessReason, String department, String costCentre,
                                       String projectRef, UUID deliverToLocation, LocalDate requiredByDate,
                                       String currency, UUID requestedBy, String requestedByName) {
        ScRequisition r = new ScRequisition();
        r.id = UUID.randomUUID();
        r.tenantId = tenantId;
        r.requisitionNumber = number;
        r.status = RequisitionStatus.DRAFT;
        r.createdAt = Instant.now();
        r.requestedBy = requestedBy;
        r.requestedByName = requestedByName;
        r.edit(type, title, businessReason, department, costCentre, projectRef, deliverToLocation, requiredByDate, currency);
        return r;
    }

    public boolean isEditable() { return status == RequisitionStatus.DRAFT; }

    /** Header edit — DRAFT only. */
    public void edit(RequisitionType type, String title, String businessReason, String department, String costCentre,
                     String projectRef, UUID deliverToLocation, LocalDate requiredByDate, String currency) {
        if (this.status != null && this.status != RequisitionStatus.DRAFT)
            throw new IllegalStateException("Only a DRAFT requisition can be edited — current: " + status);
        this.type = type;
        this.title = title;
        this.businessReason = businessReason;
        this.department = department;
        this.costCentre = costCentre;
        this.projectRef = projectRef;
        this.deliverToLocation = deliverToLocation;
        this.requiredByDate = requiredByDate;
        this.currency = currency != null && !currency.isBlank() ? currency : "ZAR";
        this.updatedAt = Instant.now();
    }

    public void setEstimatedTotal(BigDecimal total) {
        this.estimatedTotal = total;
        this.updatedAt = Instant.now();
    }

    public void submit() {
        require(RequisitionStatus.DRAFT, "submitted");
        status = RequisitionStatus.PENDING_APPROVAL;
        submittedAt = Instant.now();
        decisionNote = null;
        touch();
    }

    public void approve(UUID by, String byName, String note) {
        require(RequisitionStatus.PENDING_APPROVAL, "approved");
        status = RequisitionStatus.APPROVED;
        approvedBy = by;
        approvedByName = byName;
        approvedAt = Instant.now();
        decisionNote = note;
        touch();
    }

    public void reject(String reason) {
        require(RequisitionStatus.PENDING_APPROVAL, "rejected");
        status = RequisitionStatus.REJECTED;
        decisionNote = reason != null && reason.length() > 500 ? reason.substring(0, 500) : reason;
        touch();
    }

    /** Requester pulls a pending requisition back to DRAFT. */
    public void withdraw() {
        require(RequisitionStatus.PENDING_APPROVAL, "withdrawn");
        status = RequisitionStatus.DRAFT;
        submittedAt = null;
        touch();
    }

    /** Re-opens a REJECTED requisition for correction. */
    public void reopen() {
        require(RequisitionStatus.REJECTED, "re-opened");
        status = RequisitionStatus.DRAFT;
        submittedAt = null;
        touch();
    }

    public void cancel(String reason) {
        if (status == RequisitionStatus.CANCELLED || status == RequisitionStatus.REJECTED)
            throw new IllegalStateException("A " + status + " requisition cannot be cancelled");
        status = RequisitionStatus.CANCELLED;
        decisionNote = reason != null && reason.length() > 500 ? reason.substring(0, 500) : reason;
        touch();
    }

    private void require(RequisitionStatus expected, String verb) {
        if (status != expected)
            throw new IllegalStateException("Only a " + expected + " requisition can be " + verb + " — current: " + status);
    }

    private void touch() { updatedAt = Instant.now(); }
}
