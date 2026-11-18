package za.co.handyflow.platform.contracting.domain.model;

import jakarta.persistence.*;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.time.Instant;
import java.time.LocalDate;
import java.util.UUID;

/**
 * Evidence that a South African legal practitioner reviewed one version of a template at a point in time. It is not a
 * guarantee that every contract made from the template is lawful or suitable for every transaction.
 */
@Entity
@Table(name = "contract_template_legal_reviews")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class ContractTemplateLegalReview {

    public static final String APPROVED = "APPROVED";
    public static final String REJECTED = "REJECTED";
    public static final String CONDITIONAL = "CONDITIONAL";

    @Id private UUID id;
    @Column(name = "tenant_id") private UUID tenantId;
    @Column(name = "template_id") private UUID templateId;
    @Column(name = "template_version") private int templateVersion;
    @Column(name = "reviewer_name") private String reviewerName;
    @Column(name = "reviewer_professional_details") private String reviewerProfessionalDetails;
    @Column(name = "reviewer_organisation") private String reviewerOrganisation;
    @Column(name = "review_date") private LocalDate reviewDate;
    @Column(name = "legal_area") private String legalArea;
    private String jurisdiction;
    @Column(name = "scope_assumptions", columnDefinition = "TEXT") private String scopeAssumptions;
    @Column(name = "outstanding_issues", columnDefinition = "TEXT") private String outstandingIssues;
    private String outcome;
    @Column(name = "next_review_date") private LocalDate nextReviewDate;
    @Column(name = "evidence_document_id") private UUID evidenceDocumentId;
    @Column(name = "recorded_by") private UUID recordedBy;
    @Column(name = "withdrawn_at") private Instant withdrawnAt;
    @Column(name = "withdrawn_reason") private String withdrawnReason;
    @Column(name = "withdrawn_by") private UUID withdrawnBy;
    @Column(name = "created_at") private Instant createdAt;

    public static ContractTemplateLegalReview record(UUID tenantId, UUID templateId, int templateVersion,
                                                     String reviewerName, String reviewerProfessionalDetails,
                                                     String reviewerOrganisation, LocalDate reviewDate, String legalArea,
                                                     String jurisdiction, String scopeAssumptions, String outstandingIssues,
                                                     String outcome, LocalDate nextReviewDate, UUID evidenceDocumentId,
                                                     UUID recordedBy) {
        if (!APPROVED.equals(outcome) && !REJECTED.equals(outcome) && !CONDITIONAL.equals(outcome))
            throw new IllegalArgumentException("Outcome must be APPROVED, REJECTED or CONDITIONAL");
        if (CONDITIONAL.equals(outcome) && (outstandingIssues == null || outstandingIssues.isBlank()))
            throw new IllegalArgumentException("A conditional approval must list the outstanding issues");
        ContractTemplateLegalReview r = new ContractTemplateLegalReview();
        r.id = UUID.randomUUID();
        r.tenantId = tenantId;
        r.templateId = templateId;
        r.templateVersion = templateVersion;
        r.reviewerName = reviewerName;
        r.reviewerProfessionalDetails = reviewerProfessionalDetails;
        r.reviewerOrganisation = reviewerOrganisation;
        r.reviewDate = reviewDate;
        r.legalArea = legalArea;
        r.jurisdiction = jurisdiction == null || jurisdiction.isBlank() ? "South Africa" : jurisdiction;
        r.scopeAssumptions = scopeAssumptions;
        r.outstandingIssues = outstandingIssues;
        r.outcome = outcome;
        r.nextReviewDate = nextReviewDate;
        r.evidenceDocumentId = evidenceDocumentId;
        r.recordedBy = recordedBy;
        r.createdAt = Instant.now();
        return r;
    }

    public void withdraw(String reason, UUID by) {
        this.withdrawnAt = Instant.now();
        this.withdrawnReason = reason;
        this.withdrawnBy = by;
    }

    /** Links the stored evidence document. A withdrawn review is closed and takes no further evidence. */
    public void attachEvidence(UUID documentId) {
        if (isWithdrawn()) throw new IllegalStateException("This review was withdrawn, so evidence can no longer be added");
        this.evidenceDocumentId = documentId;
    }

    public boolean isWithdrawn() { return withdrawnAt != null; }

    /** The review date for this review has passed its next review date. */
    public boolean isExpired(LocalDate today) { return nextReviewDate != null && today.isAfter(nextReviewDate); }
}
