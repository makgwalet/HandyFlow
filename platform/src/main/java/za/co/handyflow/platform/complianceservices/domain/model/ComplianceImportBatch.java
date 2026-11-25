package za.co.handyflow.platform.complianceservices.domain.model;

import jakarta.persistence.*;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;
import za.co.handyflow.platform.shared.TenantId;

import java.time.Instant;
import java.util.UUID;

/** One file read in. PREVIEWED changes nothing; COMMITTED created the OK rows; DISCARDED was set aside with a reason. */
@Entity(name = "ComplianceImportBatch")
@Table(name = "compliance_import_batches")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class ComplianceImportBatch {
    @Id
    private UUID id = UUID.randomUUID();
    @Embedded
    @AttributeOverride(name = "value", column = @Column(name = "tenant_id", nullable = false))
    private TenantId tenantId;
    @Column(nullable = false)
    private String kind;
    @Column(name = "file_name", nullable = false)
    private String fileName;
    @Column(nullable = false)
    private String status = "PREVIEWED";
    @Column(name = "total_rows", nullable = false)
    private int totalRows;
    @Column(name = "ok_rows", nullable = false)
    private int okRows;
    @Column(name = "duplicate_rows", nullable = false)
    private int duplicateRows;
    @Column(name = "rejected_rows", nullable = false)
    private int rejectedRows;
    @Column(name = "imported_rows", nullable = false)
    private int importedRows;
    @Column(name = "created_by", nullable = false)
    private UUID createdBy;
    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;
    @Column(name = "committed_by")
    private UUID committedBy;
    @Column(name = "committed_at")
    private Instant committedAt;
    @Column(name = "discarded_by")
    private UUID discardedBy;
    @Column(name = "discarded_at")
    private Instant discardedAt;
    @Column(name = "discard_note", columnDefinition = "TEXT")
    private String discardNote;
    @Version
    private Long version;

    public static ComplianceImportBatch preview(TenantId tenantId, String kind, String fileName, int ok, int duplicate, int rejected, UUID by) {
        ComplianceImportBatch b = new ComplianceImportBatch();
        b.tenantId = tenantId;
        b.kind = kind;
        b.fileName = fileName == null || fileName.isBlank() ? "import.csv" : fileName.length() > 255 ? fileName.substring(0, 255) : fileName;
        b.okRows = ok; b.duplicateRows = duplicate; b.rejectedRows = rejected;
        b.totalRows = ok + duplicate + rejected;
        b.createdBy = by;
        b.createdAt = Instant.now();
        return b;
    }

    /** Applies the outcome of the commit: counts move from OK to the result of each row. */
    public void commit(UUID by, int imported, int nowDuplicate, int nowRejected) {
        if (!"PREVIEWED".equals(status)) throw new IllegalStateException("This import has already been " + status.toLowerCase());
        this.importedRows = imported;
        this.okRows = imported;
        this.duplicateRows += nowDuplicate;
        this.rejectedRows += nowRejected;
        this.status = "COMMITTED";
        this.committedBy = by;
        this.committedAt = Instant.now();
    }

    public void discard(UUID by, String note) {
        if (!"PREVIEWED".equals(status)) throw new IllegalStateException("This import has already been " + status.toLowerCase());
        String p = ImportRules.discardProblem(note);
        if (p != null) throw new IllegalArgumentException(p);
        this.status = "DISCARDED";
        this.discardedBy = by;
        this.discardedAt = Instant.now();
        this.discardNote = note.trim();
    }
}
