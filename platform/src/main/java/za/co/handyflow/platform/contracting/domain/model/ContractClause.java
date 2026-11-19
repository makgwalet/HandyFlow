package za.co.handyflow.platform.contracting.domain.model;

import jakarta.persistence.*;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.time.Instant;
import java.time.LocalDate;
import java.util.Set;
import java.util.UUID;

/**
 * A reusable clause written and approved by the tenant's legal reviewer. The review is evidence that it took place on the date
 * given; it is not a guarantee that the clause is lawful or suitable for any particular contract.
 */
@Entity
@Table(name = "contract_clauses")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class ContractClause {

    public static final Set<String> CATEGORIES = Set.of("DEFINITIONS", "FORCE_MAJEURE", "DOMICILIUM", "PAYMENT", "TERMINATION",
            "CONFIDENTIALITY", "LIABILITY", "DISPUTE_RESOLUTION", "DATA_PROTECTION", "OTHER");

    /** STANDARD wording needs no evidence file; every other kind does before it can be published. */
    public static final Set<String> KINDS = Set.of("STANDARD", "CUSTOM", "REGULATORY", "SENSITIVE", "SOURCED");
    /** Categories whose wording is sensitive whatever the author says: they always need evidence before publishing. */
    public static final Set<String> SENSITIVE_CATEGORIES = Set.of("DATA_PROTECTION", "LIABILITY");

    @Id private UUID id;
    @Column(name = "tenant_id") private UUID tenantId;
    @Column(name = "family_id") private UUID familyId;
    @Column(name = "version_no") private int versionNo;
    private String title;
    private String category;
    @Column(columnDefinition = "TEXT") private String body;
    @Column(name = "legal_area") private String legalArea;
    private String jurisdiction;
    private String status;
    @Column(name = "reviewer_name") private String reviewerName;
    @Column(name = "reviewer_professional_details") private String reviewerProfessionalDetails;
    @Column(name = "review_date") private LocalDate reviewDate;
    @Column(name = "next_review_date") private LocalDate nextReviewDate;
    @Column(name = "review_notes", columnDefinition = "TEXT") private String reviewNotes;
    @Column(name = "clause_kind") private String clauseKind = "STANDARD";
    @Column(name = "source_reference") private String sourceReference;
    @Column(name = "evidence_document_id") private UUID evidenceDocumentId;
    @Column(name = "published_at") private Instant publishedAt;
    @Column(name = "withdrawn_at") private Instant withdrawnAt;
    @Column(name = "withdrawn_reason") private String withdrawnReason;
    @Column(name = "created_by") private UUID createdBy;
    @Column(name = "created_at") private Instant createdAt;
    @Column(name = "updated_at") private Instant updatedAt;

    public static ContractClause create(UUID tenantId, String title, String category, String body, String legalArea, String jurisdiction, UUID by) {
        ContractClause c = new ContractClause();
        c.id = UUID.randomUUID();
        c.familyId = c.id;
        c.tenantId = tenantId;
        c.versionNo = 1;
        c.status = "DRAFT";
        c.createdBy = by;
        c.createdAt = Instant.now();
        c.applyDetails(title, category, body, legalArea, jurisdiction);
        return c;
    }

    /** Says what sort of clause this is and, for a sourced one, where the wording came from. Draft only. */
    public void classify(String kind, String source) {
        if (!"DRAFT".equals(status)) throw new IllegalStateException("Only a draft clause can be reclassified (this one is " + status + ")");
        String k = kind == null || kind.isBlank() ? "STANDARD" : kind.trim().toUpperCase();
        if (!KINDS.contains(k)) throw new IllegalArgumentException("Unknown clause kind: " + kind);
        boolean noSource = source == null || source.isBlank();
        if ("SOURCED".equals(k) && noSource) throw new IllegalArgumentException("Say where the wording came from");
        this.clauseKind = k;
        this.sourceReference = noSource ? null : source.trim();
        this.updatedAt = Instant.now();
    }

    /** Why the reviewer's signed-off review must be attached before publishing, or null when it is optional. */
    public String evidenceReason() {
        if (!"STANDARD".equals(clauseKind)) return "it is a " + clauseKind.toLowerCase() + " clause";
        if (SENSITIVE_CATEGORIES.contains(category)) return "its category (" + category.toLowerCase().replace('_', ' ') + ") is sensitive";
        return null;
    }

    public boolean evidenceRequired() { return evidenceReason() != null; }

    /** Draft only. A clause that has been approved or published is replaced by a new version, never edited. */
    public void edit(String title, String category, String body, String legalArea, String jurisdiction) {
        if (!"DRAFT".equals(status)) throw new IllegalStateException("Only a draft clause can be edited (this one is " + status + "). Make a new version instead.");
        applyDetails(title, category, body, legalArea, jurisdiction);
        clearReview();
    }

    private void applyDetails(String title, String category, String body, String legalArea, String jurisdiction) {
        if (title == null || title.isBlank()) throw new IllegalArgumentException("A clause needs a title");
        String cat = category == null ? "" : category.trim().toUpperCase();
        if (!CATEGORIES.contains(cat)) throw new IllegalArgumentException("Unknown clause category: " + category);
        String clean = HtmlSanitizer.clean(body);
        if (clean == null || clean.isBlank()) throw new IllegalArgumentException("A clause needs wording");
        this.title = title.trim();
        this.category = cat;
        this.body = clean;
        this.legalArea = legalArea;
        this.jurisdiction = jurisdiction == null || jurisdiction.isBlank() ? "South Africa" : jurisdiction;
        this.updatedAt = Instant.now();
    }

    // The evidence belongs to the review that is being cleared, so it is unlinked. The stored document itself is kept for the audit trail.
    private void clearReview() {
        reviewerName = null; reviewerProfessionalDetails = null; reviewDate = null; nextReviewDate = null; reviewNotes = null; evidenceDocumentId = null;
    }

    /** Links the reviewer's signed-off review. Only an approved or published clause has a review to evidence. */
    public void attachEvidence(UUID documentId) {
        if (!"APPROVED".equals(status) && !"PUBLISHED".equals(status))
            throw new IllegalStateException("Evidence can only be added to an approved or published clause (this one is " + status + ")");
        this.evidenceDocumentId = documentId;
        this.updatedAt = Instant.now();
    }

    /** Records the legal reviewer's approval of this exact wording. */
    public void approve(String reviewerName, String professionalDetails, LocalDate reviewDate, LocalDate nextReviewDate, String notes, LocalDate today) {
        if (!"DRAFT".equals(status)) throw new IllegalStateException("Only a draft clause can be approved (this one is " + status + ")");
        if (reviewerName == null || reviewerName.isBlank() || professionalDetails == null || professionalDetails.isBlank())
            throw new IllegalArgumentException("The reviewer's name and professional details are required");
        if (reviewDate == null || reviewDate.isAfter(today)) throw new IllegalArgumentException("The review date is required and cannot be in the future");
        if (nextReviewDate != null && !nextReviewDate.isAfter(reviewDate)) throw new IllegalArgumentException("The next review date must be after the review date");
        this.reviewerName = reviewerName.trim();
        this.reviewerProfessionalDetails = professionalDetails.trim();
        this.reviewDate = reviewDate;
        this.nextReviewDate = nextReviewDate;
        this.reviewNotes = notes;
        this.status = "APPROVED";
        this.updatedAt = Instant.now();
    }

    /** Sends an approved clause back to draft, for example when the reviewer withdraws the approval. The review details are cleared. */
    public void returnToDraft() {
        if (!"APPROVED".equals(status)) throw new IllegalStateException("Only an approved clause can be returned to draft (this one is " + status + ")");
        status = "DRAFT";
        clearReview();
        updatedAt = Instant.now();
    }

    public void publish(LocalDate today) { publish(today, evidenceDocumentId != null); }

    /** Throws unless the clause may be published now. Changes nothing, so it can run before anything else is touched. */
    public void assertPublishable(LocalDate today, boolean evidenceOnFile) {
        if (!"APPROVED".equals(status)) throw new IllegalStateException("Only an approved clause can be published (this one is " + status + ")");
        if (reviewExpired(today)) throw new IllegalStateException("The legal review of this clause has expired. It needs a new review.");
        String why = evidenceReason();
        if (why != null && !evidenceOnFile)
            throw new IllegalStateException("The reviewer's signed-off review must be attached before this clause can be published, because " + why + ".");
    }

    public void publish(LocalDate today, boolean evidenceOnFile) {
        assertPublishable(today, evidenceOnFile);
        status = "PUBLISHED";
        publishedAt = Instant.now();
        updatedAt = publishedAt;
    }

    public void supersede() {
        if (!"PUBLISHED".equals(status)) throw new IllegalStateException("Only a published clause can be superseded");
        status = "SUPERSEDED";
        updatedAt = Instant.now();
    }

    public void withdraw(String reason) {
        if (!"PUBLISHED".equals(status) && !"APPROVED".equals(status)) throw new IllegalStateException("Only an approved or published clause can be withdrawn (this one is " + status + ")");
        if (reason == null || reason.isBlank()) throw new IllegalArgumentException("Say why the clause is being withdrawn");
        status = "WITHDRAWN";
        withdrawnAt = Instant.now();
        withdrawnReason = reason;
        updatedAt = withdrawnAt;
    }

    /** A draft copy of this clause's wording as the next version in the same family. */
    public ContractClause newVersion(int nextVersionNo, UUID by) {
        if (!"PUBLISHED".equals(status)) throw new IllegalStateException("A new version can only be made from a published clause (this one is " + status + ")");
        ContractClause c = new ContractClause();
        c.id = UUID.randomUUID();
        c.familyId = familyId;
        c.tenantId = tenantId;
        c.versionNo = nextVersionNo;
        c.status = "DRAFT";
        c.createdBy = by;
        c.createdAt = Instant.now();
        c.applyDetails(title, category, body, legalArea, jurisdiction);
        c.clauseKind = clauseKind;
        c.sourceReference = sourceReference;
        return c;
    }

    public boolean reviewExpired(LocalDate today) { return nextReviewDate != null && today.isAfter(nextReviewDate); }

    /** May be put into a contract: published, and its review has not lapsed. */
    public boolean usable(LocalDate today) { return "PUBLISHED".equals(status) && !reviewExpired(today); }
}
