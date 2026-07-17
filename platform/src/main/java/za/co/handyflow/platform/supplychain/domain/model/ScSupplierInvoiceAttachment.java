package za.co.handyflow.platform.supplychain.domain.model;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.time.Instant;
import java.util.UUID;

/**
 * A file attached to a supplier invoice — the source invoice PDF/photo from
 * the supplier, a delivery note, or any other supporting document for the
 * AP audit trail. Flagged in the SCM gap analysis as a missing capability.
 * <p>
 * STORAGE: base64-encoded file content stored directly in this table,
 * following the same working pattern already proven in production use by
 * CreProof/CreDeliverable (Creative module) — there is no S3/object
 * storage available in this environment yet.
 * <p>
 * Two real gaps found in Creative's version of this pattern, fixed here:
 * <ol>
 *   <li>Creative's equivalent column is named "fileUrl" despite holding
 *       the raw base64 content, not a URL — genuinely misleading. This
 *       entity's column is named for what it actually is.</li>
 *   <li>Creative's upload path (CreativeService.uploadProof/
 *       addDeliverable) has no file-size validation anywhere — confirmed
 *       by reading its whole service layer. ScmService.
 *       uploadInvoiceAttachment() enforces a real cap before this entity
 *       is ever created.</li>
 * </ol>
 * <p>
 * This is a known, deliberate limitation to revisit once real object
 * storage exists — not a permanent architecture decision. Same honest
 * framing as Creative's own "flagged urgent, needs revisiting" status,
 * not something to silently treat as settled.
 */
@Entity
@Table(name = "sc_supplier_invoice_attachments")
@Getter
@NoArgsConstructor
public class ScSupplierInvoiceAttachment {

    @Id UUID id;
    @Column(name = "tenant_id", nullable = false) UUID tenantId;
    @Column(name = "supplier_invoice_id", nullable = false) UUID supplierInvoiceId;
    @Column(name = "file_name", nullable = false, length = 255) String fileName;
    @Column(name = "content_type", nullable = false, length = 100) String contentType;
    @Column(name = "file_size_bytes", nullable = false) long fileSizeBytes;

    // NEW: named for what it actually is, unlike Creative's "fileUrl" —
    // see class Javadoc. Genuinely a placeholder for a real object-storage
    // key/URL once that exists; holds the base64-encoded file content
    // directly until then.
    @Column(name = "file_content_base64", columnDefinition = "TEXT", nullable = false)
    String fileContentBase64;

    @Column(name = "uploaded_by") UUID uploadedBy;
    @Column(name = "uploaded_by_name", length = 255) String uploadedByName;
    @Column(name = "created_at", nullable = false) Instant createdAt;

    public static ScSupplierInvoiceAttachment create(UUID tenantId, UUID supplierInvoiceId,
                                                     String fileName, String contentType,
                                                     long fileSizeBytes, String fileContentBase64,
                                                     UUID uploadedBy, String uploadedByName) {
        ScSupplierInvoiceAttachment a = new ScSupplierInvoiceAttachment();
        a.id                  = UUID.randomUUID();
        a.tenantId            = tenantId;
        a.supplierInvoiceId   = supplierInvoiceId;
        a.fileName            = fileName;
        a.contentType         = contentType;
        a.fileSizeBytes       = fileSizeBytes;
        a.fileContentBase64   = fileContentBase64;
        a.uploadedBy          = uploadedBy;
        a.uploadedByName      = uploadedByName;
        a.createdAt           = Instant.now();
        return a;
    }
}