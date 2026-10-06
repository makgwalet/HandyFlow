package za.co.handyflow.platform.compliancetender.domain.model;

import jakarta.persistence.*;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;
import za.co.handyflow.platform.shared.TenantId;

import java.time.Instant;
import java.util.UUID;

/**
 * One build of a tender's submission package (ADR-005). Immutable: there are no setters and no update
 * path, a rebuild is a new version. The package hash covers the ordered file list in
 * {@link TenderPackageFile}; the bytes are in TenderPackageStorage under {@code storageKey}.
 */
@Entity
@Table(name = "tender_packages")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class TenderPackage {

    @Id
    private UUID id;

    @Embedded
    @AttributeOverride(name = "value", column = @Column(name = "tenant_id", nullable = false, updatable = false))
    private TenantId tenantId;

    @Column(name = "tender_id", nullable = false, updatable = false)
    private UUID tenderId;

    @Column(name = "version_no", nullable = false, updatable = false)
    private int versionNo;

    @Column(name = "submission_ready", nullable = false, updatable = false)
    private boolean submissionReady;

    @Column(name = "includes_pricing", nullable = false, updatable = false)
    private boolean includesPricing;

    @Column(name = "profile_name", updatable = false)
    private String profileName;

    @Column(name = "profile_snapshot", nullable = false, updatable = false, columnDefinition = "TEXT")
    private String profileSnapshot;

    @Column(name = "issues_snapshot", nullable = false, updatable = false, columnDefinition = "TEXT")
    private String issuesSnapshot;

    @Column(name = "package_hash", nullable = false, updatable = false, length = 64)
    private String packageHash;

    @Column(name = "file_name", nullable = false, updatable = false)
    private String fileName;

    @Column(name = "file_sha256", nullable = false, updatable = false, length = 64)
    private String fileSha256;

    @Column(name = "content_type", nullable = false, updatable = false)
    private String contentType;

    @Column(name = "size_bytes", nullable = false, updatable = false)
    private long sizeBytes;

    @Column(name = "page_count", updatable = false)
    private Integer pageCount;

    @Column(name = "storage_key", nullable = false, updatable = false, length = 1000)
    private String storageKey;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    @Column(name = "created_by", updatable = false)
    private UUID createdBy;

    @Column(name = "created_by_name", updatable = false)
    private String createdByName;

    public static TenderPackage create(UUID id, TenantId tenantId, UUID tenderId, int versionNo, boolean submissionReady,
                                       boolean includesPricing, String profileName, String profileSnapshot, String issuesSnapshot,
                                       String packageHash, String fileName, String fileSha256, String contentType, long sizeBytes, Integer pageCount,
                                       String storageKey, UUID createdBy, String createdByName) {
        if (versionNo < 1) throw new IllegalArgumentException("versionNo must be 1 or more");
        if (packageHash == null || packageHash.length() != 64) throw new IllegalArgumentException("packageHash must be a SHA-256 hex digest");
        if (storageKey == null || storageKey.isBlank()) throw new IllegalArgumentException("storageKey is required");
        TenderPackage p = new TenderPackage();
        p.id = id;
        p.tenantId = tenantId;
        p.tenderId = tenderId;
        p.versionNo = versionNo;
        p.submissionReady = submissionReady;
        p.includesPricing = includesPricing;
        p.profileName = profileName;
        p.profileSnapshot = profileSnapshot;
        p.issuesSnapshot = issuesSnapshot;
        p.packageHash = packageHash;
        p.fileName = fileName;
        p.fileSha256 = fileSha256;
        p.contentType = contentType;
        p.sizeBytes = sizeBytes;
        p.pageCount = pageCount;
        p.storageKey = storageKey;
        p.createdAt = Instant.now();
        p.createdBy = createdBy;
        p.createdByName = createdByName;
        return p;
    }
}
