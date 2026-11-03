package za.co.handyflow.platform.facilitiesmanagement.application.internal;

import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import za.co.handyflow.platform.evidence.application.EvidenceFacade;
import za.co.handyflow.platform.facilitiesmanagement.domain.model.FmWorkOrder;
import za.co.handyflow.platform.facilitiesmanagement.domain.repository.FmClientRepository;
import za.co.handyflow.platform.facilitiesmanagement.domain.repository.FmWorkOrderRepository;
import za.co.handyflow.platform.secureaccess.application.SecureShareProvider;
import za.co.handyflow.platform.shared.TenantId;

import java.util.UUID;

/**
 * FM's side of the Secure Document Access Engine: the files of a completed work order (photos, certificates, reports) shared with
 * the client's people or a third party such as an insurer.
 * <pre>
 *   share = FM / WORK_ORDER / the work order (context = the client)
 *   items = FM / EVIDENCE / the file, with its hash frozen at issue
 * </pre>
 * Asked on every view and download: the work order must still exist in the tenant and be completed (not reopened), its files must
 * not have been disposed of, the client must still be active, and the file must still be attached to it, unchanged.
 */
@Service
@RequiredArgsConstructor
public class FmShareProvider implements SecureShareProvider {

    public static final String ITEM_TYPE = "EVIDENCE";

    private final FmWorkOrderRepository workOrders;
    private final FmClientRepository clients;
    private final FmRecordsService records;
    private final EvidenceFacade evidence;

    @Override public String moduleCode() { return FmRecordsService.MODULE; }

    @Override
    public ShareHeader describe(UUID tenantId, ShareRef share) {
        TenantId t = TenantId.of(tenantId);
        FmWorkOrder w = workOrders.findActiveById(t, share.sourceId()).orElse(null);
        if (w == null) return null;
        String subject = w.getDescription() == null ? "Work order" : w.getDescription().length() > 160 ? w.getDescription().substring(0, 157) + "…" : w.getDescription();
        return new ShareHeader(w.getWorkOrderNumber(), subject, records.context(t, w.getClientId(), w.getSiteId()), "Work order documents",
                null, w.getTechnicianName(), w.getCompletedAt());
    }

    @Override
    public Decision authorise(UUID tenantId, ShareRef share, DocumentRef doc, Who who) {
        if (!FmRecordsService.MODULE.equals(doc.moduleCode()) || !ITEM_TYPE.equals(doc.sourceType())) return Decision.deny("This document is not available");
        TenantId t = TenantId.of(tenantId);
        FmWorkOrder w = workOrders.findActiveById(t, share.sourceId()).orElse(null);
        if (w == null) return Decision.deny("This work order is no longer available");
        if (w.isFilesDisposed()) return Decision.deny("These files were disposed of under the retention policy");
        if (!"COMPLETED".equals(w.getStatus())) return Decision.deny("This work order is not completed");
        boolean clientActive = clients.findActiveById(t, w.getClientId()).map(c -> "ACTIVE".equals(c.getStatus())).orElse(false);
        if (!clientActive) return Decision.deny("This client relationship is no longer active");
        boolean attached = evidence.filesFor(t, FmRecordsService.EVIDENCE_MODULE, FmRecordsService.EVIDENCE_WORK_ORDER, w.getId()).stream()
                .anyMatch(f -> f.id().equals(doc.sourceId()));
        return attached ? Decision.allow() : Decision.deny("This file has been removed from the work order");
    }

    @Override
    public ReleasedFile release(UUID tenantId, ShareRef share, DocumentRef doc, String issuedSha256, String actor) {
        TenantId t = TenantId.of(tenantId);
        EvidenceFacade.EvidenceFile f = evidence.filesFor(t, FmRecordsService.EVIDENCE_MODULE, FmRecordsService.EVIDENCE_WORK_ORDER, share.sourceId()).stream()
                .filter(x -> x.id().equals(doc.sourceId())).findFirst()
                .orElseThrow(() -> new IllegalStateException("The file is no longer attached to the work order"));
        if (issuedSha256 != null && !issuedSha256.equalsIgnoreCase(f.sha256())) throw new IllegalStateException("The file is not the one that was shared");
        EvidenceFacade.DownloadedEvidence d = evidence.download(t, f.id());
        return new ReleasedFile(d.fileName(), d.contentType(), d.content());
    }
}
