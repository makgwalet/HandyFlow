package za.co.handyflow.platform.trainingprovider.domain.model;

import jakarta.persistence.*;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;
import za.co.handyflow.platform.shared.TenantId;
import za.co.handyflow.platform.trainingprovider.domain.rules.RplRules;

import java.time.Instant;
import java.time.LocalDate;
import java.util.UUID;

/** One piece of evidence offered for an RPL application. Reviewed once, withdrawn with a reason, never deleted. */
@Entity(name = "TrainProvRplEvidence")
@Table(name = "trainprov_rpl_evidence")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class TrainProvRplEvidence {
    @Id
    private UUID id = UUID.randomUUID();
    @Column(name = "tenant_id", nullable = false)
    private UUID tenantId;
    @Column(name = "application_id", nullable = false, updatable = false)
    private UUID applicationId;
    @Column(nullable = false, updatable = false)
    private String kind;
    @Column(nullable = false, updatable = false)
    private String description;
    @Column(name = "source_ref", updatable = false)
    private String sourceRef;
    @Column(name = "received_on", nullable = false, updatable = false)
    private LocalDate receivedOn;
    @Column(nullable = false)
    private String status = RplRules.RECEIVED;
    @Column(name = "withdraw_reason")
    private String withdrawReason;
    @Column(name = "withdrawn_by")
    private UUID withdrawnBy;
    private String review;
    @Column(name = "review_note")
    private String reviewNote;
    @Column(name = "reviewed_on")
    private LocalDate reviewedOn;
    @Column(name = "reviewed_by")
    private UUID reviewedBy;
    @Column(name = "added_by", nullable = false, updatable = false)
    private UUID addedBy;
    @Column(name = "created_at", nullable = false)
    private Instant createdAt;
    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;
    @Version
    private Long version;

    public static TrainProvRplEvidence add(TenantId t, UUID applicationId, String kind, String description, String sourceRef, LocalDate receivedOn, UUID by) {
        TrainProvRplEvidence e = new TrainProvRplEvidence();
        e.tenantId = t.getValue();
        e.applicationId = applicationId;
        e.kind = kind;
        e.description = description.trim();
        e.sourceRef = sourceRef == null || sourceRef.isBlank() ? null : sourceRef.trim();
        e.receivedOn = receivedOn;
        e.addedBy = by;
        e.createdAt = Instant.now();
        e.updatedAt = e.createdAt;
        return e;
    }

    public boolean isReviewed() {
        return review != null;
    }

    public void review(String applicationStatus, String review, String note, LocalDate on, UUID by, LocalDate today) {
        String problem = RplRules.reviewProblem(applicationStatus, status, isReviewed(), review, note, on, receivedOn, today);
        if (problem != null) {
            throw new IllegalStateException(problem);
        }
        this.review = review;
        this.reviewNote = note.trim();
        this.reviewedOn = on;
        this.reviewedBy = by;
        this.updatedAt = Instant.now();
    }

    public void withdraw(String applicationStatus, String reason, UUID by) {
        String problem = RplRules.evidenceWithdrawProblem(applicationStatus, status, reason);
        if (problem != null) {
            throw new IllegalStateException(problem);
        }
        this.status = "WITHDRAWN";
        this.withdrawReason = reason.trim();
        this.withdrawnBy = by;
        this.updatedAt = Instant.now();
    }
}
