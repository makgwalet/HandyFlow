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

    /** Filename/content-type alongside the bytes, for the calling controller to set response headers. */
    record DownloadedEvidence(byte[] content, String fileName, String contentType) {}

    DownloadedEvidence download(TenantId tenantId, UUID evidenceId);

    void detach(TenantId tenantId, UUID evidenceId);
}