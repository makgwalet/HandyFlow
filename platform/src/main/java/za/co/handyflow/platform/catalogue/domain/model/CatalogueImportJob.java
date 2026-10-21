package za.co.handyflow.platform.catalogue.domain.model;

import jakarta.persistence.*;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;
import za.co.handyflow.platform.shared.TenantId;

import java.time.Instant;
import java.util.UUID;

/** The record of one applied import. */
@Getter
@Entity
@Table(name = "catalogue_import_jobs")
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class CatalogueImportJob {

    @Id
    private UUID id = UUID.randomUUID();

    @Embedded
    @AttributeOverride(name = "value", column = @Column(name = "tenant_id", nullable = false))
    private TenantId tenantId;

    @Column(name = "file_name", length = 255)
    private String fileName;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private ImportRules.Mode mode;

    @Column(name = "total_rows", nullable = false) private int totalRows;
    @Column(nullable = false) private int created;
    @Column(nullable = false) private int updated;
    @Column(nullable = false) private int unchanged;
    @Column(nullable = false) private int skipped;
    @Column(nullable = false) private int failed;

    @Column(columnDefinition = "TEXT")
    private String problems;

    @Column(name = "created_by")
    private UUID createdBy;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt = Instant.now();

    public static CatalogueImportJob of(TenantId tenantId, String fileName, ImportRules.Mode mode, int total, int created, int updated,
                                        int unchanged, int skipped, int failed, String problems, UUID by) {
        var j = new CatalogueImportJob();
        j.tenantId = tenantId;
        j.fileName = fileName == null ? null : fileName.length() > 255 ? fileName.substring(0, 255) : fileName;
        j.mode = mode;
        j.totalRows = total; j.created = created; j.updated = updated; j.unchanged = unchanged; j.skipped = skipped; j.failed = failed;
        j.problems = problems;
        j.createdBy = by;
        return j;
    }
}
