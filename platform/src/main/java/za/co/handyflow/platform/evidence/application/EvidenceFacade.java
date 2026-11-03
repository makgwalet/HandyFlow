package za.co.handyflow.platform.evidence.application;

import org.springframework.web.multipart.MultipartFile;
import za.co.handyflow.platform.evidence.dto.EvidenceResponse;
import za.co.handyflow.platform.shared.TenantId;

import java.util.List;
import java.util.UUID;

/**
 * The public contract other modules depend on — mirrors the
 * established HrFacade / CrmFacade / BillingFacade shape (a narrow
 * interface in application/, implementation kept in
 * application/internal/, so a calling module never depends on
 * evidence's internal service class directly).
 * <p>
 * This IS the reusable pattern the adoption plan's Gate 0 exists to
 * validate: does a second, independent consumer adopt this contract
 * without needing it to change? Expenses is the first consumer;
 * whichever module picks this up second is the real test.
 */
public interface EvidenceFacade {

    EvidenceResponse attach(TenantId tenantId, MultipartFile file, String evidenceType,
                            String sourceModule, String relatedEntityType, UUID relatedEntityId,
                            UUID periodId, UUID uploadedBy, String uploadedByName);

    List<EvidenceResponse> listFor(TenantId tenantId, String sourceModule,
                                   String relatedEntityType, UUID relatedEntityId);

    /**
     * NEW: everything for a tenant, across every module/entity — not
     * scoped to one specific record the way listFor() is. Added for
     * Stage 3 (external auditor portal): an auditor browsing evidence
     * doesn't already know which specific ExpenseClaim or PayClient
     * they want, they want to see everything. Every other consumer
     * (Expenses, Recruitment Agency's CV, Payroll Bureau's logo) still
     * uses listFor() exactly as before — this is additive, not a
     * replacement.
     */
    List<EvidenceResponse> listAllForTenant(TenantId tenantId);

    /** Only the named items, and only those that belong to this tenant (others are silently left out). */
    List<EvidenceResponse> listByIds(TenantId tenantId, java.util.Collection<UUID> ids);

    /** Filename/content-type alongside the bytes, for the calling controller to set response headers. */
    record DownloadedEvidence(byte[] content, String fileName, String contentType) {}

    DownloadedEvidence download(TenantId tenantId, UUID evidenceId);

    void detach(TenantId tenantId, UUID evidenceId);

    /**
     * Evidence whose source module starts with this prefix is restricted: only its own module can attach, list, download or remove it, through
     * the methods below. The generic endpoints, the tenant-wide listing and sharing with an external auditor never see it. Used for files
     * that must not leave the case they belong to, such as employee relations evidence.
     */
    String RESTRICTED_PREFIX = "restricted-";

    static boolean isRestricted(String sourceModule) { return sourceModule != null && sourceModule.startsWith(RESTRICTED_PREFIX); }

    EvidenceResponse attachRestricted(TenantId tenantId, MultipartFile file, String evidenceType,
                                      String sourceModule, String relatedEntityType, UUID relatedEntityId,
                                      UUID uploadedBy, String uploadedByName);

    List<EvidenceResponse> listRestricted(TenantId tenantId, String sourceModule, String relatedEntityType, UUID relatedEntityId);

    /** Not found unless the file is active and attached to exactly this entity. */
    DownloadedEvidence downloadRestricted(TenantId tenantId, String sourceModule, String relatedEntityType, UUID relatedEntityId, UUID evidenceId);

    /**
     * Deletes the file of one piece of evidence attached to exactly this record, because a disposal was approved under the records
     * retention rules. Only the module that owns the record calls this, from its retention provider. The evidence row stays (status
     * DISPOSED) with its hash. Returns that hash. Not found unless the evidence is attached to exactly this record.
     */
    String dispose(TenantId tenantId, String sourceModule, String relatedEntityType, UUID relatedEntityId, UUID evidenceId, String reason);

    /** An active file attached to a record, with the hash taken when it was attached. */
    record EvidenceFile(UUID id, String fileName, String contentType, long sizeBytes, String sha256, String evidenceType,
                        String uploadedByName, java.time.Instant createdAt) {}

    /**
     * The active files attached to exactly this record, with their hashes, for a module that shares them through the Secure Document
     * Access Engine (the hash is frozen when the share is issued). Never returns restricted evidence.
     */
    List<EvidenceFile> filesFor(TenantId tenantId, String sourceModule, String relatedEntityType, UUID relatedEntityId);

    /** Not found unless the file is active and attached to exactly this entity. */
    void detachRestricted(TenantId tenantId, String sourceModule, String relatedEntityType, UUID relatedEntityId, UUID evidenceId);
}