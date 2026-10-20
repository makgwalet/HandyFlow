package za.co.handyflow.platform.documents.domain.model;

import jakarta.persistence.*;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;
import za.co.handyflow.platform.shared.TenantId;

import java.time.Instant;
import java.util.UUID;

/** One stored file of a document. Never changed once saved (the database refuses an update). */
@Entity
@Table(name = "document_versions")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class DocumentVersion {

    @Id
    private UUID id = UUID.randomUUID();

    @Embedded
    @AttributeOverride(name = "value", column = @Column(name = "tenant_id", nullable = false))
    private TenantId tenantId;

    @Column(name = "document_id", nullable = false)
    private UUID documentId;

    @Column(name = "version_no", nullable = false)
    private int versionNo;

    @Column(name = "file_name", nullable = false)
    private String fileName;

    @Column(name = "content_type", nullable = false)
    private String contentType;

    @Column(name = "size_bytes", nullable = false)
    private long sizeBytes;

    @Column(nullable = false)
    private String sha256;

    @Column(name = "storage_key", nullable = false)
    private String storageKey;

    @Column(name = "scan_status", nullable = false)
    private String scanStatus;

    @Column(name = "scan_detail")
    private String scanDetail;

    private String note;

    @Column(name = "uploaded_by")
    private UUID uploadedBy;

    @Column(name = "uploaded_at", nullable = false)
    private Instant uploadedAt = Instant.now();

    public static DocumentVersion of(TenantId tenantId, UUID documentId, int versionNo, DocumentRules.Checked file, long size, String storageKey,
                                     String scanStatus, String scanDetail, String note, UUID by) {
        DocumentVersion v = new DocumentVersion();
        v.tenantId = tenantId;
        v.documentId = documentId;
        v.versionNo = versionNo;
        v.fileName = file.fileName();
        v.contentType = file.contentType();
        v.sizeBytes = size;
        v.sha256 = file.sha256();
        v.storageKey = storageKey;
        v.scanStatus = scanStatus;
        v.scanDetail = scanDetail == null ? null : scanDetail.length() > 300 ? scanDetail.substring(0, 300) : scanDetail;
        v.note = DocumentRules.optional(note, 300, "note");
        v.uploadedBy = by;
        return v;
    }
}
