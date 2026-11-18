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

    private void clearReview() {
        reviewerName = null; reviewerProfessionalDetails = null; reviewDate = null; nextReviewDate = null; reviewNotes = null;
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

    public void publish(LocalDate today) {
        if (!"APPROVED".equals(status)) throw new IllegalStateException("Only an approved clause can be published (this one is " + status + ")");
        if (reviewExpired(today)) throw new IllegalStateException("The legal review of this clause has expired. It needs a new review.");
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
        return c;
    }

    public boolean reviewExpired(LocalDate today) { return nextReviewDate != null && today.isAfter(nextReviewDate); }

    /** May be put into a contract: published, and its review has not lapsed. */
    public boolean usable(LocalDate today) { return "PUBLISHED".equals(status) && !reviewExpired(today); }
}
