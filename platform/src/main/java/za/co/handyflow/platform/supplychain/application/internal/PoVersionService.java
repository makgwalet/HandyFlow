package za.co.handyflow.platform.supplychain.application.internal;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import za.co.handyflow.platform.shared.HandyFlowException;
import za.co.handyflow.platform.shared.TenantId;
import za.co.handyflow.platform.supplychain.domain.model.ScPoLine;
import za.co.handyflow.platform.supplychain.domain.model.ScPoVersion;
import za.co.handyflow.platform.supplychain.domain.model.ScPurchaseOrder;
import za.co.handyflow.platform.supplychain.domain.repository.ScPoLineRepository;
import za.co.handyflow.platform.supplychain.domain.repository.ScPoVersionRepository;
import za.co.handyflow.platform.supplychain.domain.repository.ScPurchaseOrderRepository;
import za.co.handyflow.platform.supplychain.dto.PoVersionResponse;

import java.util.*;

/**
 * Immutable revision history. A snapshot (header + lines) is written when a PO is first
 * approved and every time an amendment is applied. Dates are stored as ISO strings so the
 * snapshot doesn't depend on any Jackson module configuration.
 */
@Service
@RequiredArgsConstructor
public class PoVersionService {

    private final ScPoVersionRepository versionRepo;
    private final ScPurchaseOrderRepository poRepo;
    private final ScPoLineRepository poLineRepo;
    private final ObjectMapper objectMapper;

    /**
     * Called when a PO is approved. If this revision already has a snapshot (the PO was
     * rejected back to DRAFT, edited, and is now being approved again) the revision number
     * is bumped first so the earlier approved content is never overwritten.
     */
    @Transactional
    public void recordApproval(ScPurchaseOrder po, UUID approverId, String approverName) {
        if (versionRepo.findByPoAndRevision(po.getTenantId(), po.getId(), po.getRevisionNumber()).isPresent()) {
            po.nextRevision();
        }
        record(po, poLineRepo.findByPurchaseOrderId(po.getId()), null,
                po.getRevisionNumber() == 1 ? "Original approval" : "Re-approval after revision", approverId, approverName);
    }

    /** POs approved before versioning existed have no snapshot of their current state — capture one before changing it. */
    @Transactional
    public void ensureBaseline(ScPurchaseOrder po, List<ScPoLine> currentLines, UUID by, String byName) {
        if (versionRepo.findByPoAndRevision(po.getTenantId(), po.getId(), po.getRevisionNumber()).isEmpty()) {
            record(po, currentLines, null, "Baseline before first amendment", by, byName);
        }
    }

    @Transactional
    public ScPoVersion record(ScPurchaseOrder po, List<ScPoLine> lines, UUID amendmentId, String reason,
                              UUID by, String byName) {
        return versionRepo.save(ScPoVersion.create(po.getTenantId(), po.getId(), po.getRevisionNumber(),
                amendmentId, reason, po.getSubtotal(), po.getVatAmount(), po.getTotalAmount(),
                toJson(snapshot(po, lines)), by, byName));
    }

    @Transactional(readOnly = true)
    public List<PoVersionResponse> list(TenantId tenantId, UUID poId) {
        requirePo(tenantId, poId);
        return versionRepo.findAllForPo(tenantId.getValue(), poId).stream().map(v -> toResponse(v, false)).toList();
    }

    @Transactional(readOnly = true)
    public PoVersionResponse get(TenantId tenantId, UUID poId, int revision) {
        requirePo(tenantId, poId);
        return versionRepo.findByPoAndRevision(tenantId.getValue(), poId, revision)
                .map(v -> toResponse(v, true))
                .orElseThrow(() -> new HandyFlowException("Revision not found", HttpStatus.NOT_FOUND, "NOT_FOUND"));
    }

    // ── internals ─────────────────────────────────────────────────────────────

    private Map<String, Object> snapshot(ScPurchaseOrder po, List<ScPoLine> lines) {
        Map<String, Object> m = new LinkedHashMap<>();
        m.put("orderNumber", po.getOrderNumber());
        m.put("revisionNumber", po.getRevisionNumber());
        m.put("status", po.getStatus().name());
        m.put("supplierId", str(po.getSupplierId()));
        m.put("supplierName", po.getSupplierName());
        m.put("orderDate", str(po.getOrderDate()));
        m.put("requiredByDate", str(po.getRequiredByDate()));
        m.put("deliverToLocation", str(po.getDeliverToLocation()));
        m.put("currency", po.getCurrency());
        m.put("projectRef", po.getProjectRef());
        m.put("terms", po.getTerms());
        m.put("notes", po.getNotes());
        m.put("subtotal", str(po.getSubtotal()));
        m.put("vatAmount", str(po.getVatAmount()));
        m.put("totalAmount", str(po.getTotalAmount()));
        List<Map<String, Object>> ls = new ArrayList<>();
        for (ScPoLine l : lines) {
            Map<String, Object> lm = new LinkedHashMap<>();
            lm.put("lineId", str(l.getId()));
            lm.put("itemName", l.getItemName());
            lm.put("supplierSku", l.getSupplierSku());
            lm.put("catalogueItemId", str(l.getCatalogueItemId()));
            lm.put("qtyOrdered", str(l.getQtyOrdered()));
            lm.put("qtyReceived", str(l.getQtyReceived()));
            lm.put("unitCost", str(l.getUnitCost()));
            lm.put("vatRate", str(l.getVatRate()));
            lm.put("lineTotal", str(l.getLineTotal()));
            lm.put("vatAmount", str(l.getVatAmount()));
            ls.add(lm);
        }
        m.put("lines", ls);
        return m;
    }

    private PoVersionResponse toResponse(ScPoVersion v, boolean withSnapshot) {
        Map<String, Object> snap = null;
        if (withSnapshot) {
            try {
                snap = objectMapper.readValue(v.getSnapshotJson(), new TypeReference<Map<String, Object>>() {});
            } catch (JsonProcessingException e) {
                throw new IllegalStateException("Corrupt PO snapshot for revision " + v.getRevisionNumber(), e);
            }
        }
        return new PoVersionResponse(v.getId(), v.getRevisionNumber(), v.getAmendmentId(), v.getReason(),
                v.getSubtotal(), v.getVatAmount(), v.getTotalAmount(), v.getCreatedByName(), v.getCreatedAt(), snap);
    }

    private String toJson(Map<String, Object> m) {
        try { return objectMapper.writeValueAsString(m); }
        catch (JsonProcessingException e) { throw new IllegalStateException("Could not serialise PO snapshot", e); }
    }

    private void requirePo(TenantId tenantId, UUID poId) {
        poRepo.findByTenantIdAndId(tenantId.getValue(), poId)
                .orElseThrow(() -> new HandyFlowException("Purchase order not found", HttpStatus.NOT_FOUND, "NOT_FOUND"));
    }

    private static String str(Object o) { return o == null ? null : o.toString(); }
}
