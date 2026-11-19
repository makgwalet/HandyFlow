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

/**
 * Privacy impact assessment. DRAFT → SUBMITTED → APPROVED / REJECTED (a rejected PIA is edited and resubmitted).
 * The reviewer must not be the assessor (unconditional). A PIA approved with HIGH residual risk must say, in the review note, why the residual risk is accepted.
 * This records the organisation's assessment; it is not a regulator approval.
 */
@Entity
@Table(name = "legalcompliance_pias")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class Pia extends AggregateRoot<Pia> {

    public static final int MIN_TEXT = 15;

    @Column(nullable = false, length = 20) private String reference;
    @Column(nullable = false, length = 255) private String title;
    @Column(name = "popia_activity_id") private UUID popiaActivityId;
    @Column(nullable = false, columnDefinition = "TEXT") private String description;
    @Column(nullable = false, columnDefinition = "TEXT") private String necessity;
    @Column(nullable = false, columnDefinition = "TEXT") private String risks;
    @Column(nullable = false, columnDefinition = "TEXT") private String mitigations;
    @Enumerated(EnumType.STRING) @Column(name = "residual_risk", nullable = false, length = 10) private RiskLevel residualRisk;
    @Enumerated(EnumType.STRING) @Column(nullable = false, length = 10) private PiaStatus status = PiaStatus.DRAFT;
    @Column(name = "assessor_id") private UUID assessorId;
    @Column(name = "assessor_name", nullable = false, length = 255) private String assessorName;
    @Column(name = "submitted_at") private Instant submittedAt;
    @Column(name = "reviewer_id") private UUID reviewerId;
    @Column(name = "reviewer_name", length = 255) private String reviewerName;
    @Column(name = "reviewed_at") private Instant reviewedAt;
    @Column(name = "review_note", columnDefinition = "TEXT") private String reviewNote;
    @Column(name = "next_review_due") private LocalDate nextReviewDue;

    public static Pia create(TenantId tenantId, String reference, String title, UUID activityId, String description, String necessity, String risks, String mitigations,
                             RiskLevel residual, UUID assessorId, String assessorName) {
        Pia p = new Pia();
        p.initTenantId(tenantId);
        p.reference = reference;
        p.assessorId = assessorId;
        if (assessorName == null || assessorName.isBlank()) throw new IllegalArgumentException("An assessor is required");
        p.assessorName = assessorName.trim();
        p.apply(title, activityId, description, necessity, risks, mitigations, residual);
        return p;
    }

    /** Whoever last edits becomes the assessor: the person who approves must never be the person who wrote the final text. */
    public void edit(String title, UUID activityId, String description, String necessity, String risks, String mitigations, RiskLevel residual, UUID by, String byName) {
        if (status != PiaStatus.DRAFT && status != PiaStatus.REJECTED) throw new IllegalStateException("Only a draft or rejected assessment can be edited — current: " + status);
        apply(title, activityId, description, necessity, risks, mitigations, residual);
        this.assessorId = by;
        if (byName != null && !byName.isBlank()) this.assessorName = byName.trim();
        this.status = PiaStatus.DRAFT;
    }

    private void apply(String title, UUID activityId, String description, String necessity, String risks, String mitigations, RiskLevel residual) {
        if (title == null || title.isBlank()) throw new IllegalArgumentException("A title is required");
        if (residual == null) throw new IllegalArgumentException("A residual risk rating is required");
        text(description, "Describe the processing");
        text(necessity, "Explain why the processing is necessary and proportionate");
        text(risks, "Describe the risks to data subjects");
        text(mitigations, "Describe the mitigations");
        this.title = title.trim();
        this.popiaActivityId = activityId;
        this.description = description.trim();
        this.necessity = necessity.trim();
        this.risks = risks.trim();
        this.mitigations = mitigations.trim();
        this.residualRisk = residual;
    }

    private static void text(String s, String msg) {
        if (s == null || s.trim().length() < MIN_TEXT) throw new IllegalArgumentException(msg + " (at least " + MIN_TEXT + " characters)");
    }

    public void submit() {
        if (status != PiaStatus.DRAFT) throw new IllegalStateException("Only a draft can be submitted — current: " + status);
        this.submittedAt = Instant.now();
        this.status = PiaStatus.SUBMITTED;
    }

    public void approve(UUID by, String byName, String note, int reviewMonths) {
        requireReviewable(by, byName);
        if (reviewMonths < 1 || reviewMonths > 60) throw new IllegalArgumentException("The review period must be between 1 and 60 months");
        if (residualRisk == RiskLevel.HIGH && (note == null || note.trim().length() < MIN_TEXT))
            throw new IllegalArgumentException("Residual risk is HIGH: record why it is accepted (at least " + MIN_TEXT + " characters)");
        stamp(by, byName, note);
        this.nextReviewDue = LocalDate.now().plusMonths(reviewMonths);
        this.status = PiaStatus.APPROVED;
    }

    public void reject(UUID by, String byName, String note) {
        requireReviewable(by, byName);
        if (note == null || note.trim().length() < MIN_TEXT) throw new IllegalArgumentException("Say what needs to change (at least " + MIN_TEXT + " characters)");
        stamp(by, byName, note);
        this.status = PiaStatus.REJECTED;
    }

    private void requireReviewable(UUID by, String byName) {
        if (status != PiaStatus.SUBMITTED) throw new IllegalStateException("Only a submitted assessment can be reviewed — current: " + status);
        if (byName == null || byName.isBlank()) throw new IllegalArgumentException("A reviewer is required");
        if (by != null && by.equals(assessorId)) throw new IllegalStateException("Segregation of duties: the assessor cannot review their own assessment");
    }

    private void stamp(UUID by, String byName, String note) {
        this.reviewerId = by;
        this.reviewerName = byName.trim();
        this.reviewedAt = Instant.now();
        this.reviewNote = note == null || note.isBlank() ? null : note.trim();
    }
}
