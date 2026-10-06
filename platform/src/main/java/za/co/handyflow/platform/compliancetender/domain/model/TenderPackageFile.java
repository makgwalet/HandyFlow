package za.co.handyflow.platform.compliancetender.domain.model;

import jakarta.persistence.*;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;
import za.co.handyflow.platform.shared.TenantId;

import java.util.UUID;

/**
 * One file in a package build, in package order. GENERATED = a section this system rendered, ATTACHED =
 * an existing document merged in, ORIGINAL = a Word/Excel/other file kept as it was uploaded (never
 * converted, never dropped). Immutable like its package.
 */
@Entity
@Table(name = "tender_package_files")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class TenderPackageFile {

    public enum Source { GENERATED, ATTACHED, ORIGINAL }

    @Id
    private UUID id;

    @Embedded
    @AttributeOverride(name = "value", column = @Column(name = "tenant_id", nullable = false, updatable = false))
    private TenantId tenantId;

    @Column(name = "package_id", nullable = false, updatable = false)
    private UUID packageId;

    @Column(name = "sequence_no", nullable = false, updatable = false)
    private int sequenceNo;

    @Column(name = "section_key", nullable = false, updatable = false, length = 60)
    private String sectionKey;

    @Column(name = "file_name", nullable = false, updatable = false)
    private String fileName;

    @Enumerated(EnumType.STRING)
    @Column(name = "source_type", nullable = false, updatable = false, length = 20)
    private Source sourceType;

    @Column(name = "size_bytes", nullable = false, updatable = false)
    private long sizeBytes;

    @Column(nullable = false, updatable = false, length = 64)
    private String sha256;

    @Column(updatable = false)
    private Integer pages;

    @Column(name = "evidence_id", updatable = false)
    private UUID evidenceId;

    public static TenderPackageFile create(TenantId tenantId, UUID packageId, int sequenceNo, String sectionKey, String fileName,
                                           Source sourceType, long sizeBytes, String sha256, Integer pages, UUID evidenceId) {
        if (sequenceNo < 1) throw new IllegalArgumentException("sequenceNo must be 1 or more");
        TenderPackageFile f = new TenderPackageFile();
        f.id = UUID.randomUUID();
        f.tenantId = tenantId;
        f.packageId = packageId;
        f.sequenceNo = sequenceNo;
        f.sectionKey = sectionKey;
        f.fileName = fileName;
        f.sourceType = sourceType;
        f.sizeBytes = sizeBytes;
        f.sha256 = sha256;
        f.pages = pages;
        f.evidenceId = evidenceId;
        return f;
    }
}
