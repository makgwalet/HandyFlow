package za.co.handyflow.platform.legalcompliance.domain.model;

import jakarta.persistence.*;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;
import za.co.handyflow.platform.shared.AggregateRoot;
import za.co.handyflow.platform.shared.TenantId;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.UUID;

/**
 * A conflict-of-interest declaration by a person about an interest, relationship or outside role. DECLARED → UNDER_REVIEW → CLEARED / MITIGATED /
 * NOT_PERMITTED, or WITHDRAWN by the declarant. The reviewer cannot be the declarant. A mitigation (for example recusal) is required for MITIGATED.
 * Cleared and mitigated declarations come up for review again after review_months; the declarant can confirm nothing has changed, which extends that date.
 */
@Entity
@Table(name = "legalcompliance_coi_declarations")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class CoiDeclaration extends AggregateRoot<CoiDeclaration> {

    public static final int MIN_TEXT = 15;

    @Column(nullable = false, length = 20) private String reference;
    @Column(name = "declarant_id", nullable = false) private UUID declarantId;
    @Column(name = "declarant_name", nullable = false, length = 255) private String declarantName;
    @Enumerated(EnumType.STRING) @Column(name = "declaration_type", nullable = false, length = 20) private DeclarationType declarationType;
    @Column(nullable = false, length = 255) private String counterparty;
    @Column(nullable = false, columnDefinition = "TEXT") private String description;
    @Column(name = "estimated_value", precision = 14, scale = 2) private BigDecimal estimatedValue;
    @Column(name = "declared_on", nullable = false) private LocalDate declaredOn;
    @Enumerated(EnumType.STRING) @Column(nullable = false, length = 15) private DeclarationStatus status = DeclarationStatus.DECLARED;
    @Column(name = "reviewer_id") private UUID reviewerId;
    @Column(name = "reviewer_name", length = 255) private String reviewerName;
    @Column(name = "reviewed_on") private LocalDate reviewedOn;
    @Column(name = "decision_note", columnDefinition = "TEXT") private String decisionNote;
    @Column(columnDefinition = "TEXT") private String mitigation;
    @Column(name = "review_months", nullable = false) private int reviewMonths = 12;
    @Column(name = "next_review_due") private LocalDate nextReviewDue;

    public static CoiDeclaration declare(TenantId tenantId, String reference, UUID declarantId, String declarantName, DeclarationType type, String counterparty, String description,
                                         BigDecimal value, LocalDate today, int reviewMonths) {
        if (declarantId == null) throw new IllegalArgumentException("The declarant is required");
        CoiDeclaration d = new CoiDeclaration();
        d.initTenantId(tenantId);
        d.reference = reference;
        d.declarantId = declarantId;
        d.declarantName = declarantName;
        d.declaredOn = today;
        d.apply(type, counterparty, description, value, reviewMonths);
        return d;
    }

    /** The declarant can amend a declaration only until a reviewer has picked it up. */
    public void amend(DeclarationType type, String counterparty, String description, BigDecimal value, int reviewMonths) {
        if (status != DeclarationStatus.DECLARED) throw new IllegalStateException("A declaration can be amended only before review starts — current: " + status);
        apply(type, counterparty, description, value, reviewMonths);
    }

    private void apply(DeclarationType type, String counterparty, String description, BigDecimal value, int reviewMonths) {
        if (type == null) throw new IllegalArgumentException("A declaration type is required");
        if (counterparty == null || counterparty.isBlank()) throw new IllegalArgumentException("Say who or what the interest is in");
        if (description == null || description.trim().length() < MIN_TEXT) throw new IllegalArgumentException("Describe the interest (at least " + MIN_TEXT + " characters)");
        if (value != null && value.signum() < 0) throw new IllegalArgumentException("A value cannot be negative");
        if (reviewMonths < 1 || reviewMonths > 36) throw new IllegalArgumentException("Review period must be between 1 and 36 months");
        this.declarationType = type;
        this.counterparty = counterparty.trim();
        this.description = description.trim();
        this.estimatedValue = value;
        this.reviewMonths = reviewMonths;
    }

    public void startReview(UUID reviewerId, String reviewerName) {
        if (status != DeclarationStatus.DECLARED) throw new IllegalStateException("Only a new declaration can go under review — current: " + status);
        requireIndependent(reviewerId);
        this.reviewerId = reviewerId;
        this.reviewerName = reviewerName;
        this.status = DeclarationStatus.UNDER_REVIEW;
    }

    public void decide(DeclarationStatus outcome, String note, String mitigation, UUID reviewerId, String reviewerName, LocalDate today) {
        if (status != DeclarationStatus.DECLARED && status != DeclarationStatus.UNDER_REVIEW) throw new IllegalStateException("Only a declaration awaiting review can be decided — current: " + status);
        if (outcome != DeclarationStatus.CLEARED && outcome != DeclarationStatus.MITIGATED && outcome != DeclarationStatus.NOT_PERMITTED)
            throw new IllegalArgumentException("The outcome must be cleared, mitigated or not permitted");
        requireIndependent(reviewerId);
        if (note == null || note.trim().length() < MIN_TEXT) throw new IllegalArgumentException("Record the reasoning (at least " + MIN_TEXT + " characters)");
        if (outcome == DeclarationStatus.MITIGATED && (mitigation == null || mitigation.trim().length() < MIN_TEXT)) throw new IllegalArgumentException("Describe the mitigation (at least " + MIN_TEXT + " characters)");
        this.status = outcome;
        this.reviewerId = reviewerId;
        this.reviewerName = reviewerName;
        this.reviewedOn = today;
        this.decisionNote = note.trim();
        this.mitigation = outcome == DeclarationStatus.MITIGATED ? mitigation.trim() : null;
        this.nextReviewDue = outcome == DeclarationStatus.NOT_PERMITTED ? null : today.plusMonths(reviewMonths);
    }

    /** Declarant confirms nothing has changed; only for a cleared or mitigated declaration. */
    public void confirmUnchanged(UUID by, LocalDate today) {
        if (status != DeclarationStatus.CLEARED && status != DeclarationStatus.MITIGATED) throw new IllegalStateException("Only a cleared or mitigated declaration can be confirmed — current: " + status);
        if (!declarantId.equals(by)) throw new IllegalStateException("Only the declarant can confirm that nothing has changed");
        this.nextReviewDue = today.plusMonths(reviewMonths);
        this.decisionNote = (decisionNote == null ? "" : decisionNote + "\n") + "Declarant confirmed nothing has changed on " + today;
    }

    public void withdraw(String reason, UUID by) {
        if (!declarantId.equals(by)) throw new IllegalStateException("Only the declarant can withdraw a declaration");
        if (status == DeclarationStatus.WITHDRAWN || status == DeclarationStatus.NOT_PERMITTED) throw new IllegalStateException("This declaration cannot be withdrawn — current: " + status);
        if (reason == null || reason.trim().length() < MIN_TEXT) throw new IllegalArgumentException("Say why it is withdrawn (at least " + MIN_TEXT + " characters)");
        this.decisionNote = (decisionNote == null ? "" : decisionNote + "\n") + "Withdrawn: " + reason.trim();
        this.status = DeclarationStatus.WITHDRAWN;
        this.nextReviewDue = null;
    }

    public boolean awaitingReview() { return status == DeclarationStatus.DECLARED || status == DeclarationStatus.UNDER_REVIEW; }

    private void requireIndependent(UUID reviewerId) {
        if (reviewerId != null && reviewerId.equals(declarantId)) throw new IllegalStateException("Segregation of duties: you cannot review your own declaration");
    }
}
