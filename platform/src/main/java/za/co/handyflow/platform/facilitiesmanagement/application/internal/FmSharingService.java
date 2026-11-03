package za.co.handyflow.platform.facilitiesmanagement.application.internal;

import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.PageRequest;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import za.co.handyflow.platform.evidence.application.EvidenceFacade;
import za.co.handyflow.platform.facilitiesmanagement.domain.model.FmWorkOrder;
import za.co.handyflow.platform.facilitiesmanagement.domain.repository.FmClientRepository;
import za.co.handyflow.platform.facilitiesmanagement.domain.repository.FmWorkOrderRepository;
import za.co.handyflow.platform.facilitiesmanagement.dto.FmSharingDtos.*;
import za.co.handyflow.platform.secureaccess.application.SecureAccessDtos.AccessLogView;
import za.co.handyflow.platform.secureaccess.application.SecureAccessDtos.ItemSpec;
import za.co.handyflow.platform.secureaccess.application.SecureAccessDtos.OpenShare;
import za.co.handyflow.platform.secureaccess.application.SecureAccessDtos.RecipientSpec;
import za.co.handyflow.platform.secureaccess.application.SecureAccessDtos.RecipientView;
import za.co.handyflow.platform.secureaccess.application.SecureShareService;
import za.co.handyflow.platform.shared.HandyFlowException;
import za.co.handyflow.platform.shared.ResourceNotFoundException;
import za.co.handyflow.platform.shared.TenantId;

import java.util.*;

/**
 * FM's thin facade over the Secure Document Access Engine. It owns the FM rules: only a completed work order of an active client
 * is shared, only files attached to it, and the set of files is fixed when the first person is given access (so everyone who gets
 * a link sees the same pack, each file with its hash frozen). Links, codes, sessions, expiry, revocation and the access log are
 * the engine's; recipients open the pack at /secure/share/{token}.
 */
@Service
@RequiredArgsConstructor
public class FmSharingService {

    static final String SHARE_TYPE = FmRecordsService.WORK_ORDER;
    static final int MAX_LIST = 100;

    private final FmWorkOrderRepository workOrders;
    private final FmClientRepository clients;
    private final FmWorkOrderService workOrderService;
    private final FmRecordsService records;
    private final EvidenceFacade evidence;
    private final SecureShareService engine;

    /** Completed work orders, most recent first, with how many files each holds and whether it has been shared. */
    @Transactional(readOnly = true)
    public List<WorkOrderRow> completed(TenantId tenantId, String q, Integer limit) {
        String query = q == null ? "" : q.trim().toLowerCase(Locale.ROOT);
        int n = Math.max(1, Math.min(limit == null ? 50 : limit, MAX_LIST));
        List<WorkOrderRow> out = new ArrayList<>();
        for (FmWorkOrder w : workOrders.findCompletedForSharing(tenantId, query, PageRequest.of(0, n))) {
            int files = evidence.filesFor(tenantId, FmRecordsService.EVIDENCE_MODULE, FmRecordsService.EVIDENCE_WORK_ORDER, w.getId()).size();
            boolean shared = engine.findShareId(tenantId.getValue(), FmRecordsService.MODULE, SHARE_TYPE, w.getId()).isPresent();
            out.add(new WorkOrderRow(w.getId(), w.getWorkOrderNumber(), w.getDescription(), records.context(tenantId, w.getClientId(), w.getSiteId()),
                    w.getCompletedAt(), files, shared, w.isFilesDisposed()));
        }
        return out;
    }

    @Transactional(readOnly = true)
    public SharingView view(TenantId tenantId, UUID workOrderId) {
        FmWorkOrder w = workOrder(tenantId, workOrderId);
        UUID shareId = shareId(tenantId, workOrderId);
        Set<UUID> inShare = shareId == null ? Set.of() : engine.itemIdsByDocument(tenantId.getValue(), shareId).keySet();
        List<FileRow> files = evidence.filesFor(tenantId, FmRecordsService.EVIDENCE_MODULE, FmRecordsService.EVIDENCE_WORK_ORDER, w.getId()).stream()
                .map(f -> new FileRow(f.id(), f.fileName(), f.contentType(), f.sizeBytes(), f.sha256(), f.evidenceType(), f.uploadedByName(),
                        f.createdAt(), inShare.contains(f.id())))
                .toList();
        String why = whyNotShareable(tenantId, w);
        List<RecipientView> recipients = shareId == null ? List.of() : engine.recipientViews(tenantId.getValue(), shareId);
        return new SharingView(w.getId(), w.getWorkOrderNumber(), w.getDescription(), records.context(tenantId, w.getClientId(), w.getSiteId()),
                w.getStatus(), w.getCompletedAt(), why == null, why, w.isFilesDisposed(), files, shareId, recipients);
    }

    /** Gives one more person a link to the work order's files. The first one also fixes which files are shared. */
    @Transactional
    public RecipientView addRecipient(TenantId tenantId, UUID workOrderId, RecipientRequest req, UUID by, String byName) {
        if (req == null) throw new IllegalArgumentException("Say who to share the files with.");
        FmWorkOrder w = workOrder(tenantId, workOrderId);
        String why = whyNotShareable(tenantId, w);
        if (why != null) throw new HandyFlowException(why, HttpStatus.CONFLICT, "NOT_SHAREABLE");
        UUID shareId = shareId(tenantId, workOrderId);
        if (shareId == null) {
            shareId = engine.openShare(new OpenShare(tenantId.getValue(), FmRecordsService.MODULE, SHARE_TYPE, w.getId(), w.getClientId(),
                    items(tenantId, w, req.evidenceIds()), List.of(), null, false, by, byName)).shareId();
        } else if (req.evidenceIds() != null && !req.evidenceIds().isEmpty()) {
            throw new HandyFlowException("The files of this work order were fixed when it was first shared; the new person gets the same files.",
                    HttpStatus.CONFLICT, "FILES_FIXED");
        }
        RecipientSpec spec = new RecipientSpec(req.name(), req.organisation(), req.email(),
                req.canDownload() == null || req.canDownload(), req.canAcknowledge() == null || req.canAcknowledge(), null);
        return engine.addRecipient(tenantId.getValue(), shareId, spec, req.expiryDays(), Boolean.TRUE.equals(req.attach()), by, byName);
    }

    @Transactional
    public RecipientView reissue(TenantId tenantId, UUID workOrderId, UUID recipientId, Integer expiryDays, String byName) {
        FmWorkOrder w = workOrder(tenantId, workOrderId);
        String why = whyNotShareable(tenantId, w);
        if (why != null) throw new HandyFlowException(why, HttpStatus.CONFLICT, "NOT_SHAREABLE");
        return engine.reissue(tenantId.getValue(), shareOf(tenantId, workOrderId), recipientId, expiryDays, byName);
    }

    @Transactional
    public RecipientView revoke(TenantId tenantId, UUID workOrderId, UUID recipientId, String reason, String byName) {
        workOrder(tenantId, workOrderId);
        if (reason == null || reason.trim().length() < 3) throw new IllegalArgumentException("Give a reason to revoke access.");
        return engine.revoke(tenantId.getValue(), shareOf(tenantId, workOrderId), recipientId, reason, byName);
    }

    @Transactional(readOnly = true)
    public List<AccessLogView> accessLog(TenantId tenantId, UUID workOrderId) {
        workOrder(tenantId, workOrderId);
        UUID shareId = shareId(tenantId, workOrderId);
        return shareId == null ? List.of() : engine.accessLog(tenantId.getValue(), shareId);
    }

    // ── internals ────────────────────────────────────────────────────────────

    private FmWorkOrder workOrder(TenantId tenantId, UUID id) {
        workOrderService.requireAccessible(tenantId, id);
        return workOrders.findActiveById(tenantId, id).orElseThrow(() -> new ResourceNotFoundException("Work order", String.valueOf(id)));
    }

    /** Null when it may be shared, otherwise the reason it may not. */
    String whyNotShareable(TenantId tenantId, FmWorkOrder w) {
        if (w.isFilesDisposed()) return "The files of this work order were disposed of under the retention policy.";
        if (!"COMPLETED".equals(w.getStatus())) return "Only a completed work order can be shared.";
        boolean active = clients.findActiveById(tenantId, w.getClientId()).map(c -> "ACTIVE".equals(c.getStatus())).orElse(false);
        return active ? null : "This client is not active, so their work orders are not shared.";
    }

    private UUID shareId(TenantId tenantId, UUID workOrderId) {
        return engine.findShareId(tenantId.getValue(), FmRecordsService.MODULE, SHARE_TYPE, workOrderId).orElse(null);
    }

    private UUID shareOf(TenantId tenantId, UUID workOrderId) {
        UUID id = shareId(tenantId, workOrderId);
        if (id == null) throw new HandyFlowException("Recipient not found", HttpStatus.NOT_FOUND, "NOT_FOUND");
        return id;
    }

    private List<ItemSpec> items(TenantId tenantId, FmWorkOrder w, List<UUID> evidenceIds) {
        if (evidenceIds == null || evidenceIds.isEmpty()) throw new IllegalArgumentException("Choose the files to share.");
        Map<UUID, EvidenceFacade.EvidenceFile> attached = new LinkedHashMap<>();
        for (EvidenceFacade.EvidenceFile f : evidence.filesFor(tenantId, FmRecordsService.EVIDENCE_MODULE, FmRecordsService.EVIDENCE_WORK_ORDER, w.getId()))
            attached.put(f.id(), f);
        List<ItemSpec> out = new ArrayList<>();
        for (UUID id : new LinkedHashSet<>(evidenceIds)) {
            EvidenceFacade.EvidenceFile f = attached.get(id);
            if (f == null) throw new HandyFlowException("A chosen file is not attached to this work order.", HttpStatus.BAD_REQUEST, "FILE_NOT_ATTACHED");
            out.add(new ItemSpec(FmRecordsService.MODULE, FmShareProvider.ITEM_TYPE, f.id(), null, w.getWorkOrderNumber(), f.fileName(),
                    f.evidenceType(), f.fileName(), f.sha256()));
        }
        return out;
    }
}
