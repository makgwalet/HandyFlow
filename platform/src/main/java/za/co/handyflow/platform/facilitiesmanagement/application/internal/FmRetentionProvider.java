package za.co.handyflow.platform.facilitiesmanagement.application.internal;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import za.co.handyflow.platform.documents.application.RetentionProvider;
import za.co.handyflow.platform.evidence.application.EvidenceFacade;
import za.co.handyflow.platform.facilitiesmanagement.domain.model.FmAsset;
import za.co.handyflow.platform.facilitiesmanagement.domain.model.FmWorkOrder;
import za.co.handyflow.platform.facilitiesmanagement.domain.repository.FmAssetRepository;
import za.co.handyflow.platform.facilitiesmanagement.domain.repository.FmWorkOrderRepository;
import za.co.handyflow.platform.shared.TenantId;

import java.util.List;
import java.util.UUID;

/**
 * FM work orders and assets (module FM, source types WORK_ORDER and ASSET) for the records retention engine. Disposal, once
 * approved by a second person, deletes every active file attached to the record (through Evidence and the document store, which
 * both keep the file's record and hash) and marks the record. The work order or asset itself, its history and its billing stay.
 * A work order that was reopened, or an asset brought back into service, is refused.
 */
@Slf4j
@Component
@RequiredArgsConstructor
class FmRetentionProvider implements RetentionProvider {

    private final FmWorkOrderRepository workOrders;
    private final FmAssetRepository assets;
    private final EvidenceFacade evidence;

    @Override
    public String moduleCode() { return FmRecordsService.MODULE; }

    @Override
    public Disposed dispose(TenantId tenantId, String sourceType, UUID sourceId, UUID batchId, UUID approvedBy) {
        String reason = "Disposed under the retention policy (request " + batchId + ")";
        if (FmRecordsService.WORK_ORDER.equals(sourceType)) {
            FmWorkOrder w = workOrders.findActiveById(tenantId, sourceId).orElseThrow(() -> new IllegalStateException("The work order no longer exists"));
            if (w.isFilesDisposed()) throw new IllegalStateException("The work order's files were already disposed of");
            if (!w.isTerminal()) throw new IllegalStateException("The work order is open again (status " + w.getStatus() + ")");
            int n = disposeFiles(tenantId, FmRecordsService.EVIDENCE_WORK_ORDER, w.getId(), reason);
            w.markFilesDisposed(batchId);
            workOrders.save(w);
            log.info("FM work order {} files disposed: {} (request {})", w.getWorkOrderNumber(), n, batchId);
            return new Disposed(n, n + " file(s) removed from work order " + w.getWorkOrderNumber());
        }
        if (FmRecordsService.ASSET.equals(sourceType)) {
            FmAsset a = assets.findActiveById(tenantId, sourceId).orElseThrow(() -> new IllegalStateException("The asset no longer exists"));
            if (a.isFilesDisposed()) throw new IllegalStateException("The asset's files were already disposed of");
            if (!"DECOMMISSIONED".equals(a.getStatus())) throw new IllegalStateException("The asset is in service again (status " + a.getStatus() + ")");
            int n = disposeFiles(tenantId, FmRecordsService.EVIDENCE_ASSET, a.getId(), reason);
            a.markFilesDisposed(batchId);
            assets.save(a);
            log.info("FM asset {} files disposed: {} (request {})", a.getId(), n, batchId);
            return new Disposed(n, n + " file(s) removed from asset " + (a.getAssetTag() != null ? a.getAssetTag() : a.getName()));
        }
        throw new IllegalArgumentException("Unknown FM record type " + sourceType);
    }

    private int disposeFiles(TenantId tenantId, String entityType, UUID entityId, String reason) {
        List<EvidenceFacade.EvidenceFile> files = evidence.filesFor(tenantId, FmRecordsService.EVIDENCE_MODULE, entityType, entityId);
        for (EvidenceFacade.EvidenceFile f : files) {
            evidence.dispose(tenantId, FmRecordsService.EVIDENCE_MODULE, entityType, entityId, f.id(), reason);
        }
        return files.size();
    }
}
