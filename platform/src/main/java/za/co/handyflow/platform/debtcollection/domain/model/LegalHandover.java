package za.co.handyflow.platform.debtcollection.domain.model;

import jakarta.persistence.CollectionTable;
import jakarta.persistence.Column;
import jakarta.persistence.ElementCollection;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.Table;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;
import za.co.handyflow.platform.shared.AggregateRoot;
import za.co.handyflow.platform.shared.TenantId;

import java.time.Instant;
import java.util.HashSet;
import java.util.Set;
import java.util.UUID;

/**
 * A controlled transfer of a collection case to Legal Practice. The case stays the durable record; this
 * aggregate is the reviewed hand-over of it: a classification of the agreement, a prescription review, a frozen
 * transfer package (JSON snapshot + SHA-256 hash, rebuilt as a NEW packageVersion on every resubmission), a
 * four-eyes approval (approver must differ from preparer), and finally the link to the matter created in
 * Legal Practice. Nothing is re-captured there: IDs and provenance travel with the package.
 * <p>
 * Corrections are traceable, not silent: returning to DRAFT keeps lastReturnReason, and every transition writes a
 * LegalEvent including the package version and hash it applied to.
 */
@Entity
@Table(name = "debtcollection_legal_handovers")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class LegalHandover extends AggregateRoot<LegalHandover> {

    @Column(name = "case_id", nullable = false)
    private UUID caseId;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 15)
    private HandoverStatus status;

    @Enumerated(EnumType.STRING)
    @Column(name = "agreement_type", nullable = false, length = 25)
    private CreditAgreementType agreementType;

    /** Evidence of the creditor's authority to collect/sue (cession, mandate, contract) — free text plus evidenceIds. */
    @Column(name = "creditor_authority_note", columnDefinition = "TEXT")
    private String creditorAuthorityNote;

    /** Evidence records (owned by `evidence`) staff chose to include; ids only. */
    @ElementCollection(fetch = FetchType.EAGER)
    @CollectionTable(name = "debtcollection_legal_handover_evidence", joinColumns = @JoinColumn(name = "handover_id"))
    @Column(name = "evidence_id", nullable = false)
    private Set<UUID> evidenceIds = new HashSet<>();

    @Column(name = "prescription_reviewed", nullable = false)
    private boolean prescriptionReviewed;

    @Column(name = "prescription_review_note", columnDefinition = "TEXT")
    private String prescriptionReviewNote;

    @Column(name = "prescription_reviewed_by")
    private UUID prescriptionReviewedBy;

    @Column(name = "prescription_reviewed_at")
    private Instant prescriptionReviewedAt;

    @Column(columnDefinition = "TEXT")
    private String notes;

    @Column(name = "package_version", nullable = false)
    private int packageVersion;

    @Column(name = "package_json", columnDefinition = "TEXT")
    private String packageJson;

    @Column(name = "package_hash", length = 64)
    private String packageHash;

    @Column(name = "package_built_at")
    private Instant packageBuiltAt;

    @Column(name = "prepared_by", nullable = false)
    private UUID preparedBy;

    @Column(name = "prepared_by_name", length = 255)
    private String preparedByName;

    @Column(name = "submitted_at")
    private Instant submittedAt;

    @Column(name = "approved_by")
    private UUID approvedBy;

    @Column(name = "approved_by_name", length = 255)
    private String approvedByName;

    @Column(name = "approved_at")
    private Instant approvedAt;

    @Column(name = "last_return_reason", length = 500)
    private String lastReturnReason;

    @Column(name = "legal_matter_id")
    private UUID legalMatterId;

    @Column(name = "legal_matter_number", length = 60)
    private String legalMatterNumber;

    @Column(name = "transferred_at")
    private Instant transferredAt;

    @Column(name = "transferred_by")
    private UUID transferredBy;

    /** Latest status reported back by Legal Practice (free text — that module owns the vocabulary). */
    @Column(name = "legal_status", length = 100)
    private String legalStatus;

    @Column(name = "legal_status_updated_at")
    private Instant legalStatusUpdatedAt;

    @Column(name = "cancel_reason", length = 500)
    private String cancelReason;

    public static LegalHandover draft(TenantId tenantId, UUID caseId, CreditAgreementType agreementType,
                                      String creditorAuthorityNote, Set<UUID> evidenceIds, String notes,
                                      UUID preparedBy, String preparedByName) {
        if (caseId == null || preparedBy == null) throw new IllegalArgumentException("caseId and preparer are required");
        LegalHandover h = new LegalHandover();
        h.initTenantId(tenantId);
        h.caseId = caseId;
        h.status = HandoverStatus.DRAFT;
        h.agreementType = agreementType != null ? agreementType : CreditAgreementType.UNKNOWN;
        h.creditorAuthorityNote = creditorAuthorityNote;
        h.evidenceIds = evidenceIds != null ? new HashSet<>(evidenceIds) : new HashSet<>();
        h.notes = notes;
        h.preparedBy = preparedBy;
        h.preparedByName = preparedByName;
        return h;
    }

    public void updateDraft(CreditAgreementType agreementType, String creditorAuthorityNote, Set<UUID> evidenceIds, String notes) {
        require(HandoverStatus.DRAFT, "edit");
        if (agreementType != null && agreementType != this.agreementType) {
            // A different classification changes which checks apply, so an earlier prescription sign-off must be redone.
            this.agreementType = agreementType;
            this.prescriptionReviewed = false;
            this.prescriptionReviewNote = null;
            this.prescriptionReviewedBy = null;
            this.prescriptionReviewedAt = null;
        }
        this.creditorAuthorityNote = creditorAuthorityNote;
        this.evidenceIds = evidenceIds != null ? new HashSet<>(evidenceIds) : new HashSet<>();
        this.notes = notes;
    }

    public void recordPrescriptionReview(String note, UUID by) {
        require(HandoverStatus.DRAFT, "record a prescription review on");
        if (note == null || note.isBlank()) throw new IllegalArgumentException("A prescription review note is required");
        this.prescriptionReviewed = true;
        this.prescriptionReviewNote = note;
        this.prescriptionReviewedBy = by;
        this.prescriptionReviewedAt = Instant.now();
    }

    public void submit(String packageJson, String packageHash) {
        require(HandoverStatus.DRAFT, "submit");
        this.packageVersion++;
        this.packageJson = packageJson;
        this.packageHash = packageHash;
        this.packageBuiltAt = Instant.now();
        this.status = HandoverStatus.IN_REVIEW;
        this.submittedAt = Instant.now();
        this.lastReturnReason = null;
    }

    /** Four-eyes: the approver must not be the preparer. */
    public void approve(UUID by, String byName) {
        require(HandoverStatus.IN_REVIEW, "approve");
        if (by == null || by.equals(preparedBy)) {
            throw new IllegalStateException("A handover must be approved by someone other than the person who prepared it");
        }
        this.status = HandoverStatus.APPROVED;
        this.approvedBy = by;
        this.approvedByName = byName;
        this.approvedAt = Instant.now();
    }

    public void returnToDraft(String reason) {
        if (status != HandoverStatus.IN_REVIEW && status != HandoverStatus.APPROVED) {
            throw new IllegalStateException("Only an IN_REVIEW or APPROVED handover can be returned (status is " + status + ")");
        }
        if (reason == null || reason.isBlank()) throw new IllegalArgumentException("A reason is required to return a handover");
        this.status = HandoverStatus.DRAFT;
        this.lastReturnReason = reason.length() > 500 ? reason.substring(0, 500) : reason;
        this.approvedBy = null;
        this.approvedByName = null;
        this.approvedAt = null;
        this.submittedAt = null;
    }

    public void markTransferred(UUID matterId, String matterNumber, UUID by) {
        require(HandoverStatus.APPROVED, "transfer");
        if (matterId == null) throw new IllegalArgumentException("The legal matter id is required");
        this.status = HandoverStatus.TRANSFERRED;
        this.legalMatterId = matterId;
        this.legalMatterNumber = matterNumber;
        this.transferredAt = Instant.now();
        this.transferredBy = by;
        this.legalStatus = "OPENED";
        this.legalStatusUpdatedAt = this.transferredAt;
    }

    public void cancel(String reason) {
        if (!status.isOpen()) throw new IllegalStateException("Only an open handover can be cancelled (status is " + status + ")");
        if (reason == null || reason.isBlank()) throw new IllegalArgumentException("A reason is required to cancel a handover");
        this.status = HandoverStatus.CANCELLED;
        this.cancelReason = reason.length() > 500 ? reason.substring(0, 500) : reason;
    }

    public void syncLegalStatus(String legalStatus) {
        require(HandoverStatus.TRANSFERRED, "sync a legal status for");
        if (legalStatus == null || legalStatus.isBlank()) throw new IllegalArgumentException("legalStatus is required");
        this.legalStatus = legalStatus.length() > 100 ? legalStatus.substring(0, 100) : legalStatus;
        this.legalStatusUpdatedAt = Instant.now();
    }

    private void require(HandoverStatus expected, String action) {
        if (status != expected) {
            throw new IllegalStateException("Cannot " + action + " a handover that is " + status + " (needs " + expected + ")");
        }
    }
}
