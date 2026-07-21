package za.co.handyflow.platform.accountant.domain.model;

import jakarta.persistence.*;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.time.Instant;
import java.util.UUID;

/**
 * Closes the accountant module audit's "larger workpaper system" gap.
 * <p>
 * Review workflow: DRAFT -> PREPARED -> REVIEWED -> SIGNED_OFF. Each
 * forward transition validates the immediately-prior state — mirrors
 * the same discipline already used for TimeEntry/FeeNote/
 * AccPortalAccessGrant's own state machines this session, not a new
 * pattern invented for this entity specifically.
 * <p>
 * Versioning: version_number + superseded_by (self-reference). A
 * re-upload of a file with the same name in the same folder creates a
 * NEW row and marks the previous version's supersededBy, rather than
 * overwriting content in place — preserves the full history a real
 * audit trail needs.
 * <p>
 * storageKey is reserved, unused — no S3 in this environment, matching
 * every other document-storage table this session (acc_fica_documents
 * and others). Content lives in fileContentBase64 instead.
 */
@Entity(name = "AccountantWorkpaperFile")
@Table(name = "acc_workpaper_files")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class AccWorkpaperFile {

    @Id
    private UUID id = UUID.randomUUID();

    @Column(name = "tenant_id", nullable = false) private UUID tenantId;
    @Column(name = "client_id", nullable = false) private UUID clientId;
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

    public static AccWorkpaperFile create(UUID tenantId, UUID clientId, UUID folderId,
                                          String fileName, String mimeType, Long fileSizeBytes,
                                          String fileContentBase64, int versionNumber) {
        if (fileContentBase64 == null || fileContentBase64.isBlank()) {
            throw new IllegalArgumentException("File content is required");
        }
        AccWorkpaperFile f = new AccWorkpaperFile();
        f.tenantId           = tenantId;
        f.clientId           = clientId;
        f.folderId           = folderId;
        f.fileName           = fileName;
        f.mimeType           = mimeType;
        f.fileSizeBytes      = fileSizeBytes;
        f.fileContentBase64  = fileContentBase64;
        f.versionNumber      = versionNumber;
        f.createdAt          = Instant.now();
        f.updatedAt          = Instant.now();
        return f;
    }

    public boolean isDeleted() {
        return deletedAt != null;
    }

    public void markPrepared(UUID preparedBy) {
        requireStatus("DRAFT", "prepared");
        this.reviewStatus = "PREPARED";
        this.preparedBy   = preparedBy;
        this.preparedAt   = Instant.now();
        this.updatedAt    = Instant.now();
    }

    public void markReviewed(UUID reviewedBy) {
        requireStatus("PREPARED", "reviewed");
        this.reviewStatus = "REVIEWED";
        this.reviewedBy   = reviewedBy;
        this.reviewedAt   = Instant.now();
        this.updatedAt    = Instant.now();
    }

    public void signOff(UUID signedOffBy) {
        requireStatus("REVIEWED", "signed off");
        this.reviewStatus = "SIGNED_OFF";
        this.signedOffBy  = signedOffBy;
        this.signedOffAt  = Instant.now();
        this.updatedAt    = Instant.now();
    }

    /**
     * Sends a file back to DRAFT for correction — a real, legitimate
     * need (a reviewer finds an issue after review or sign-off), not
     * covered by any of the three forward transitions. Refuses on an
     * already-DRAFT file — there's nothing to "reopen".
     */
    public void reopen() {
        if ("DRAFT".equals(reviewStatus)) {
            throw new IllegalStateException("This file is already in DRAFT — nothing to reopen");
        }
        this.reviewStatus = "DRAFT";
        this.preparedBy   = null; this.preparedAt   = null;
        this.reviewedBy   = null; this.reviewedAt   = null;
        this.signedOffBy  = null; this.signedOffAt  = null;
        this.updatedAt    = Instant.now();
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
        this.updatedAt    = Instant.now();
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