package za.co.handyflow.platform.projects.domain.model;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.NoArgsConstructor;
import za.co.handyflow.platform.projects.domain.enums.DocumentStatus;

import java.time.Instant;
import java.time.LocalDate;
import java.util.UUID;

/**
 * One REVISION of a controlled document. Revisions of the same document share a documentNumber
 * (the "series"); each upload under that number is a new row.
 *
 * CHUNK 6 CHANGES
 * ───────────────
 * - documentNumber: identity of the document across revisions (e.g. DWG-007).
 * - Lifecycle is enforced by DocumentStatus — the old unguarded setStatus() / approve() /
 *   supersede() are gone, so a document can't be approved without being reviewed.
 * - Approval, supersession and voiding record who/when/why. A SUPERSEDED row keeps its approval
 *   details and points at the revision that replaced it (supersededBy) — the approved version
 *   stays traceable after a newer one is issued.
 * - visibility: INTERNAL or CLIENT (the client portal shows only CLIENT + APPROVED).
 * - Reviewed documents are never deleted; unreviewed ones can be VOIDED (kept, marked, with a reason).
 */
@Entity
@Table(name = "project_documents")
@Getter
@NoArgsConstructor
public class ProjectDocument {

    @Id UUID id;
    @Column(name = "tenant_id",  nullable = false) UUID    tenantId;
    @Column(name = "project_id", nullable = false) UUID    projectId;
    @Column(name = "document_number", length = 50) String  documentNumber;
    // document_type: DRAWING | RFI | SUBMITTAL | CONTRACT | REPORT | PHOTO | GENERAL
    @Column(name = "document_type", nullable = false, length = 30) String documentType = "GENERAL";
    @Column(nullable = false, length = 300) String title;
    @Column(length = 20) String revision;
    @Column(name = "file_url")     String fileUrl;
    @Column(name = "file_name")    String fileName;
    @Column(name = "file_size_kb") Integer fileSizeKb;
    @Column(nullable = false, length = 20) String status = "CURRENT";
    String description;
    @Column(name = "visibility", nullable = false, length = 10) String visibility = "INTERNAL";
    @Column(name = "uploaded_by")      UUID   uploadedBy;
    @Column(name = "uploaded_by_name") String uploadedByName;

    @Column(name = "superseded_by")     UUID    supersededBy;
    @Column(name = "approved_by")       UUID    approvedBy;
    @Column(name = "approved_by_name")  String  approvedByName;
    @Column(name = "approved_at")       Instant approvedAt;
    @Column(name = "voided_at")         Instant voidedAt;
    @Column(name = "voided_by")         UUID    voidedBy;
    @Column(name = "void_reason")       String  voidReason;

    // ── Stored file (null evidenceId = a link-only entry) ─────────────────────
    @Column(name = "evidence_id")                    UUID   evidenceId;
    @Column(name = "file_sha256", length = 64)       String fileSha256;
    @Column(name = "content_type", length = 150)     String contentType;

    // ── Retention (stamped at project closure; nothing is auto-deleted) ───────
    @Column(name = "retention_until")                LocalDate retentionUntil;
    @Column(name = "legal_hold", nullable = false)   boolean   legalHold = false;
    /** Set when a disposal approved by two people (records retention) deleted the file. The register entry stays. */
    @Column(name = "disposed_at")                    Instant   disposedAt;
    @Column(name = "disposal_batch_id")              UUID      disposalBatchId;

    @Column(name = "created_at", nullable = false) Instant createdAt;

    public static ProjectDocument create(UUID tenantId, UUID projectId, String documentNumber,
                                         String documentType, String title, String revision,
                                         String fileUrl, String fileName, Integer fileSizeKb,
                                         String description, String visibility,
                                         UUID uploadedBy, String uploadedByName) {
        ProjectDocument d  = new ProjectDocument();
        d.id               = UUID.randomUUID();
        d.tenantId         = tenantId;
        d.projectId        = projectId;
        d.documentNumber   = documentNumber;
        d.documentType     = documentType != null ? documentType : "GENERAL";
        d.title            = title;
        d.revision         = revision;
        d.fileUrl          = fileUrl;
        d.fileName         = fileName;
        d.fileSizeKb       = fileSizeKb;
        d.description      = description;
        d.visibility       = "CLIENT".equals(visibility) ? "CLIENT" : "INTERNAL";
        d.status           = DocumentStatus.CURRENT.name();
        d.uploadedBy       = uploadedBy;
        d.uploadedByName   = uploadedByName;
        d.createdAt        = Instant.now();
        return d;
    }

    // ── Lifecycle (every change goes through DocumentStatus) ──────────────────

    public void submitForReview() { transition(DocumentStatus.FOR_REVIEW); }

    public void approve(UUID by, String byName) {
        transition(DocumentStatus.APPROVED);
        this.approvedBy     = by;
        this.approvedByName = byName;
        this.approvedAt     = Instant.now();
    }

    public void reject() { transition(DocumentStatus.REJECTED); }

    /** replacedBy = the newer revision. Approval details are deliberately left in place. */
    public void supersede(UUID replacedBy) {
        transition(DocumentStatus.SUPERSEDED);
        this.supersededBy = replacedBy;
    }

    public void voidDocument(UUID by, String reason) {
        transition(DocumentStatus.VOIDED);
        this.voidedAt   = Instant.now();
        this.voidedBy   = by;
        this.voidReason = reason;
    }

    public void changeVisibility(String visibility) {
        if (DocumentStatus.valueOf(status).isFinal()) {
            throw new IllegalStateException("A " + status + " document's visibility can no longer be changed");
        }
        this.visibility = "CLIENT".equals(visibility) ? "CLIENT" : "INTERNAL";
    }

    public DocumentStatus statusEnum() { return DocumentStatus.valueOf(status); }

    // ── Stored file ───────────────────────────────────────────────────────────

    /**
     * Marks this revision as a stored file. fileUrl becomes the authenticated download path of THIS
     * revision, so existing screens that render fileUrl as a link keep working. Called once, before
     * the first save.
     */
    public void attachStoredFile(UUID evidenceId, String sha256, String contentType,
                                 String fileName, Integer fileSizeKb) {
        if (this.evidenceId != null) throw new IllegalStateException("This revision already has a stored file");
        this.evidenceId  = evidenceId;
        this.fileSha256  = sha256;
        this.contentType = contentType;
        this.fileName    = fileName;
        this.fileSizeKb  = fileSizeKb;
        this.fileUrl     = "/api/v1/projects/documents/" + id + "/download";
    }

    public boolean hasStoredFile() { return evidenceId != null; }

    // ── Retention ─────────────────────────────────────────────────────────────

    /** Legal hold can be placed or lifted at any status — a superseded revision can still be needed in a dispute. */
    public void changeLegalHold(boolean hold) { this.legalHold = hold; }

    public boolean isDisposed() { return disposedAt != null; }

    /** The stored file was deleted under an approved disposal. Never for a document under legal hold, and only once. */
    public void markDisposed(UUID batchId) {
        if (legalHold) throw new IllegalStateException("This document is under legal hold and cannot be disposed of");
        if (disposedAt != null) throw new IllegalStateException("This document was already disposed of");
        if (evidenceId == null) throw new IllegalStateException("This entry is a link, not a stored file");
        this.disposedAt = Instant.now();
        this.disposalBatchId = batchId;
    }

    private void transition(DocumentStatus target) {
        DocumentStatus current = DocumentStatus.valueOf(status);
        if (!current.canTransitionTo(target)) {
            throw new IllegalStateException("A " + current + " document cannot become " + target);
        }
        this.status = target.name();
    }
}