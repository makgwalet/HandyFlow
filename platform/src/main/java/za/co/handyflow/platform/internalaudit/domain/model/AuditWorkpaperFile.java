package za.co.handyflow.platform.internalaudit.domain.model;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.time.Instant;
import java.util.UUID;

/**
 * Direct structural mirror of AccWorkpaperFile (Accountant module) —
 * same review workflow (DRAFT -> PREPARED -> REVIEWED -> SIGNED_OFF,
 * each forward transition validating the immediately-prior state, plus
 * reopen() back to DRAFT), same versioning approach (versionNumber +
 * supersededBy self-reference — a re-upload creates a new row rather
 * than overwriting content in place), same soft-delete/restore. Ties
 * directly into the engagement-scoped role model from Phase 1: who is
 * allowed to prepare vs. review vs. sign off a given file is enforced
 * by checking EngagementAssignment at the service layer, not by this
 * entity — this entity only enforces the state machine itself.
 * <p>
 * storageKey reserved, unused — no S3 in this environment, matching
 * every other document-storage table in this codebase. Content lives
 * in fileContentBase64 instead.
 */
@Entity(name = "AuditWorkpaperFile")
@Table(name = "audit_workpaper_files")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class AuditWorkpaperFile {

    @Id
    private UUID id = UUID.randomUUID();

    @Column(name = "tenant_id", nullable = false) private UUID tenantId;
    @Column(name = "engagement_id", nullable = false) private UUID engagementId;
    @Column(name = "folder_id", nullable = false) private UUID folderId;
    @Column(name = "file_name", nullable = false, length = 300) private String fileName;
    @Column(name = "storage_key", length = 500) private String storageKey;
    @Column(name = "mime_type", length = 100) private String mimeType;
    @Column(name = "file_size_bytes") private Long fileSizeBytes;

    @Column(name = "file_content_base64", columnDefinition = "TEXT") private String fileContentBase64;

    @Column(name = "review_status", nullable = false) private String reviewStatus = "DRAFT";
    @Column(name = "prepared_by")   private UUID    preparedBy;
    @Column(name = "prepared_at")   private Instant preparedAt;
    @Column(name = "reviewed_by")   private UUID    reviewedBy;
    @Column(name = "reviewed_at")   private Instant reviewedAt;
    @Column(name = "signed_off_by") private UUID    signedOffBy;
    @Column(name = "signed_off_at") private Instant signedOffAt;

    @Column(name = "version_number", nullable = false) private int versionNumber = 1;
    @Column(name = "superseded_by") private UUID supersededBy;

    @Column(name = "deleted_at") private Instant deletedAt;

    @Column(name = "created_at", nullable = false, updatable = false) private Instant createdAt;
    @Column(name = "updated_at", nullable = false) private Instant updatedAt;

    public static AuditWorkpaperFile create(UUID tenantId, UUID engagementId, UUID folderId,
                                            String fileName, String mimeType, Long fileSizeBytes,
                                            String fileContentBase64, int versionNumber) {
        if (fileContentBase64 == null || fileContentBase64.isBlank()) {
            throw new IllegalArgumentException("File content is required");
        }
        AuditWorkpaperFile f = new AuditWorkpaperFile();
        f.tenantId = tenantId;
        f.engagementId = engagementId;
        f.folderId = folderId;
        f.fileName = fileName;
        f.mimeType = mimeType;
        f.fileSizeBytes = fileSizeBytes;
        f.fileContentBase64 = fileContentBase64;
        f.versionNumber = versionNumber;
        f.createdAt = Instant.now();
        f.updatedAt = Instant.now();
        return f;
    }

    public boolean isDeleted() {
        return deletedAt != null;
    }

    public void markPrepared(UUID preparedBy) {
        requireStatus("DRAFT", "prepared");
        this.reviewStatus = "PREPARED";
        this.preparedBy = preparedBy;
        this.preparedAt = Instant.now();
        this.updatedAt = Instant.now();
    }

    public void markReviewed(UUID reviewedBy) {
        requireStatus("PREPARED", "reviewed");
        this.reviewStatus = "REVIEWED";
        this.reviewedBy = reviewedBy;
        this.reviewedAt = Instant.now();
        this.updatedAt = Instant.now();
    }

    public void signOff(UUID signedOffBy) {
        requireStatus("REVIEWED", "signed off");
        this.reviewStatus = "SIGNED_OFF";
        this.signedOffBy = signedOffBy;
        this.signedOffAt = Instant.now();
        this.updatedAt = Instant.now();
    }

    public void reopen() {
        if ("DRAFT".equals(reviewStatus)) {
            throw new IllegalStateException("This file is already in DRAFT — nothing to reopen");
        }
        this.reviewStatus = "DRAFT";
        this.preparedBy = null; this.preparedAt = null;
        this.reviewedBy = null; this.reviewedAt = null;
        this.signedOffBy = null; this.signedOffAt = null;
        this.updatedAt = Instant.now();
    }

    private void requireStatus(String required, String actionPastTense) {
        if (!required.equals(reviewStatus)) {
            throw new IllegalStateException(
                    "Cannot mark " + actionPastTense + " — file is currently " + reviewStatus
                            + ", expected " + required);
        }
    }

    public void markSuperseded(UUID newVersionId) {
        this.supersededBy = newVersionId;
        this.updatedAt = Instant.now();
    }

    public void softDelete() {
        this.deletedAt = Instant.now();
        this.updatedAt = Instant.now();
    }

    public void restore() {
        this.deletedAt = null;
        this.updatedAt = Instant.now();
    }
}
