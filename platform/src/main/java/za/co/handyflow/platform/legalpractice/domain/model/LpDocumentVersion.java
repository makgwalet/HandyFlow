package za.co.handyflow.platform.legalpractice.domain.model;

import jakarta.persistence.*;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;
import za.co.handyflow.platform.shared.TenantId;

import java.time.Instant;
import java.util.UUID;

/** One file of a document, kept for good. The hash was taken when the firm received the file, so a later copy can be checked against it. */
@Entity
@Table(name = "lp_document_versions")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class LpDocumentVersion {

    @Id
    private UUID id = UUID.randomUUID();

    @Embedded
    @AttributeOverride(name = "value", column = @Column(name = "tenant_id", nullable = false))
    private TenantId tenantId;

    @Column(name = "document_id", nullable = false, updatable = false) private UUID documentId;
    @Column(name = "version_no", nullable = false, updatable = false) private int versionNo;
    @Column(name = "evidence_id", nullable = false, updatable = false) private UUID evidenceId;
    @Column(name = "file_name", nullable = false, updatable = false) private String fileName;
    @Column(name = "content_type", nullable = false, updatable = false) private String contentType;
    @Column(name = "size_bytes", nullable = false, updatable = false) private long sizeBytes;
    @Column(nullable = false, updatable = false, length = 64) private String sha256;
    @Column(name = "change_note", updatable = false) private String changeNote;
    @Column(name = "uploaded_by", nullable = false, updatable = false) private UUID uploadedBy;
    @Column(name = "uploaded_by_name", updatable = false) private String uploadedByName;
    @Column(name = "uploaded_at", nullable = false, updatable = false) private Instant uploadedAt;

    public static LpDocumentVersion record(TenantId tenantId, UUID documentId, int versionNo, UUID evidenceId, String fileName, String contentType,
                                           long sizeBytes, String sha256, String changeNote, UUID by, String byName, Instant now) {
        LpDocumentVersion v = new LpDocumentVersion();
        v.tenantId = tenantId; v.documentId = documentId; v.versionNo = versionNo; v.evidenceId = evidenceId;
        v.fileName = fileName; v.contentType = contentType; v.sizeBytes = sizeBytes; v.sha256 = sha256;
        v.changeNote = changeNote == null || changeNote.isBlank() ? null : changeNote.trim();
        v.uploadedBy = by; v.uploadedByName = byName; v.uploadedAt = now;
        return v;
    }
}
