package za.co.handyflow.platform.legalcompliance.domain.model;

import jakarta.persistence.*;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;
import za.co.handyflow.platform.shared.AggregateRoot;
import za.co.handyflow.platform.shared.TenantId;

import java.time.Instant;
import java.time.LocalDate;
import java.util.UUID;

/** An organisational risk. Scores live in RiskAssessment (append-only); this holds identity, ownership, treatment and acceptance. */
@Entity
@Table(name = "legalcompliance_risks")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class Risk extends AggregateRoot<Risk> {

    @Column(nullable = false, length = 20) private String reference;
    @Column(nullable = false, length = 255) private String title;
    @Column(columnDefinition = "TEXT") private String description;
    @Enumerated(EnumType.STRING) @Column(nullable = false, length = 20) private RiskCategory category;
    @Enumerated(EnumType.STRING) @Column(nullable = false, length = 12) private RiskStatus status = RiskStatus.IDENTIFIED;
    @Column(nullable = false) private boolean emerging;
    @Column(name = "owner_id") private UUID ownerId;
    @Column(name = "owner_name", nullable = false, length = 255) private String ownerName;
    @Column(name = "next_review_date", nullable = false) private LocalDate nextReviewDate;
    @Enumerated(EnumType.STRING) @Column(name = "treatment_strategy", length = 10) private TreatmentStrategy treatmentStrategy;
    @Column(name = "treatment_plan", columnDefinition = "TEXT") private String treatmentPlan;
    @Column(name = "treatment_due") private LocalDate treatmentDue;
    @Column(name = "accepted_by") private UUID acceptedBy;
    @Column(name = "accepted_by_name", length = 255) private String acceptedByName;
    @Column(name = "accepted_at") private Instant acceptedAt;
    @Column(name = "acceptance_reason", columnDefinition = "TEXT") private String acceptanceReason;
    @Column(name = "acceptance_expires") private LocalDate acceptanceExpires;
    @Column(name = "closed_at") private Instant closedAt;
    @Column(name = "close_reason", columnDefinition = "TEXT") private String closeReason;
    @Column(name = "created_by") private UUID createdBy;

    public static Risk create(TenantId tenantId, String reference, String title, String description, RiskCategory category,
                              boolean emerging, UUID ownerId, String ownerName, LocalDate nextReviewDate, UUID by) {
        require(title, "A title is required");
        require(ownerName, "A risk owner is required");
        if (nextReviewDate == null) throw new IllegalArgumentException("A next review date is required");
        Risk r = new Risk();
        r.initTenantId(tenantId);
        r.reference = reference;
        r.title = title.trim();
        r.description = description;
        r.category = category;
        r.emerging = emerging;
        r.ownerId = ownerId;
        r.ownerName = ownerName.trim();
        r.nextReviewDate = nextReviewDate;
        r.createdBy = by;
        return r;
    }

    public void update(String title, String description, RiskCategory category, boolean emerging, UUID ownerId, String ownerName, LocalDate nextReviewDate) {
        requireOpen();
        require(title, "A title is required");
        require(ownerName, "A risk owner is required");
        if (nextReviewDate == null) throw new IllegalArgumentException("A next review date is required");
        this.title = title.trim();
        this.description = description;
        this.category = category;
        this.emerging = emerging;
        this.ownerId = ownerId;
        this.ownerName = ownerName.trim();
        this.nextReviewDate = nextReviewDate;
    }

    /** First assessment moves IDENTIFIED to ASSESSED; later reassessments leave treatment/acceptance status to the service. */
    public void markAssessed() {
        requireOpen();
        if (status == RiskStatus.IDENTIFIED) status = RiskStatus.ASSESSED;
    }

    public void startTreatment(TreatmentStrategy strategy, String plan, LocalDate due) {
        requireOpen();
        if (status == RiskStatus.IDENTIFIED) throw new IllegalStateException("Assess the risk before planning its treatment");
        if (strategy == null) throw new IllegalArgumentException("A treatment strategy is required");
        require(plan, "A treatment plan is required");
        if (due == null) throw new IllegalArgumentException("A treatment due date is required");
        this.treatmentStrategy = strategy;
        this.treatmentPlan = plan.trim();
        this.treatmentDue = due;
        clearAcceptance();
        this.status = RiskStatus.TREATING;
    }

    public void accept(UUID by, String byName, String reason, LocalDate expires, LocalDate today) {
        requireOpen();
        if (status == RiskStatus.IDENTIFIED) throw new IllegalStateException("Assess the risk before accepting it");
        if (reason == null || reason.trim().length() < 15) throw new IllegalArgumentException("A reason of at least 15 characters is required");
        if (expires == null || !expires.isAfter(today)) throw new IllegalArgumentException("An acceptance must expire on a future date");
        this.acceptedBy = by;
        this.acceptedByName = byName;
        this.acceptedAt = Instant.now();
        this.acceptanceReason = reason.trim();
        this.acceptanceExpires = expires;
        this.status = RiskStatus.ACCEPTED;
    }

    /** Acceptance ran out, or the risk was reassessed above appetite: it must be treated or re-accepted. Reason is kept in the audit log. */
    public void lapseAcceptance() {
        if (status != RiskStatus.ACCEPTED) return;
        clearAcceptance();
        this.status = RiskStatus.ASSESSED;
    }

    public void close(String reason) {
        if (status == RiskStatus.CLOSED) throw new IllegalStateException("The risk is already closed");
        require(reason, "A reason for closing is required");
        this.status = RiskStatus.CLOSED;
        this.closedAt = Instant.now();
        this.closeReason = reason.trim();
    }

    public void reopen() {
        if (status != RiskStatus.CLOSED) throw new IllegalStateException("Only a closed risk can be reopened");
        this.status = RiskStatus.ASSESSED;
        this.closedAt = null;
        this.closeReason = null;
    }

    public void scheduleReview(LocalDate next) {
        requireOpen();
        if (next == null) throw new IllegalArgumentException("A review date is required");
        this.nextReviewDate = next;
    }

    public boolean isOpen() { return status != RiskStatus.CLOSED; }

    private void clearAcceptance() {
        acceptedBy = null; acceptedByName = null; acceptedAt = null; acceptanceReason = null; acceptanceExpires = null;
    }
    private void requireOpen() { if (status == RiskStatus.CLOSED) throw new IllegalStateException("The risk is closed — reopen it first"); }
    private static void require(String s, String msg) { if (s == null || s.isBlank()) throw new IllegalArgumentException(msg); }
}
