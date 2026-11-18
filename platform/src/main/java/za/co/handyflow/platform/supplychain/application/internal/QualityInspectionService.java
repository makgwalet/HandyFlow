package za.co.handyflow.platform.supplychain.application.internal;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.http.HttpStatus;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import za.co.handyflow.platform.shared.HandyFlowException;
import za.co.handyflow.platform.shared.TenantId;
import za.co.handyflow.platform.shared.TenantSequenceService;
import za.co.handyflow.platform.supplychain.dto.QualityDtos.*;
import za.co.handyflow.platform.supplychain.dto.ReturnDtos;

import java.math.BigDecimal;
import java.sql.Timestamp;
import java.util.*;

/**
 * Inspection of what a posted goods receipt accepted into stock. One inspection per receipt at a time; its lines are the receipt's
 * accepted lines. Completing it fixes the result (PASSED / PARTIAL / FAILED) and, when asked, raises a DRAFT return for the failed
 * quantity so someone approves it before anything leaves the building. Results feed the supplier scorecard.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class QualityInspectionService {

    private final JdbcTemplate jdbc;
    private final TenantSequenceService sequenceService;
    private final SupplierReturnService returns;
    private final QuarantineService quarantine;

    @Transactional
    public Response start(TenantId tenantId, UUID userId, String userName, UUID receiptId) {
        UUID tid = tenantId.getValue();
        if (receiptId == null) throw bad("goodsReceiptId is required");
        List<Map<String, Object>> gr = jdbc.queryForList("SELECT supplier_id, purchase_order_id, status FROM sc_goods_receipts WHERE tenant_id = ? AND id = ?", tid, receiptId);
        if (gr.isEmpty()) throw new HandyFlowException("Goods receipt not found", HttpStatus.NOT_FOUND, "NOT_FOUND");
        if (!"POSTED".equals(gr.get(0).get("status"))) throw new HandyFlowException("Only a POSTED goods receipt can be inspected", HttpStatus.CONFLICT, "GR_NOT_POSTED");

        UUID id = UUID.randomUUID();
        String number = "QI-" + String.format("%05d", sequenceService.nextValue(tenantId, "QI"));
        try {
            jdbc.update("INSERT INTO sc_quality_inspections (id, tenant_id, inspection_number, goods_receipt_id, purchase_order_id, supplier_id, status, created_by) VALUES (?,?,?,?,?,?,'OPEN',?)",
                    id, tid, number, receiptId, gr.get(0).get("purchase_order_id"), gr.get(0).get("supplier_id"), userId);
        } catch (DuplicateKeyException e) {
            throw new HandyFlowException("An inspection is already open for that goods receipt", HttpStatus.CONFLICT, "INSPECTION_OPEN");
        }
        int n = jdbc.update("""
                INSERT INTO sc_quality_inspection_lines (id, tenant_id, inspection_id, gr_line_id, po_line_id, catalogue_item_id, item_name, qty_accepted_on_receipt, unit_cost)
                SELECT gen_random_uuid(), tenant_id, ?, id, po_line_id, catalogue_item_id, item_name, qty_received - qty_rejected, unit_cost
                  FROM sc_gr_lines WHERE tenant_id = ? AND goods_receipt_id = ? AND qty_received - qty_rejected > 0""", id, tid, receiptId);
        if (n == 0) throw new HandyFlowException("That receipt accepted nothing into stock, so there is nothing to inspect", HttpStatus.CONFLICT, "NOTHING_TO_INSPECT");
        return get(tenantId, id);
    }

    @Transactional
    public Response record(TenantId tenantId, UUID id, UUID userId, String userName, RecordRequest req) {
        UUID tid = tenantId.getValue();
        requireOpen(tid, id);
        if (req == null || req.lines() == null || req.lines().isEmpty()) throw bad("At least one line result is required");
        for (LineResult r : req.lines()) {
            List<Map<String, Object>> line = jdbc.queryForList("SELECT qty_accepted_on_receipt FROM sc_quality_inspection_lines WHERE tenant_id = ? AND inspection_id = ? AND id = ?", tid, id, r.lineId());
            if (line.isEmpty()) throw bad("Line " + r.lineId() + " is not on this inspection");
            String problem = QualityInspectionRules.lineProblem((BigDecimal) line.get(0).get("qty_accepted_on_receipt"), r.qtyPassed(), r.qtyFailed());
            if (problem != null) throw bad(problem);
            if (r.qtyFailed().signum() > 0 && (r.defectCode() == null || r.defectCode().isBlank())) throw bad("A defect code is required when quantity fails");
            jdbc.update("UPDATE sc_quality_inspection_lines SET qty_passed = ?, qty_failed = ?, qty_inspected = ?, defect_code = ?, notes = ? WHERE tenant_id = ? AND inspection_id = ? AND id = ?",
                    r.qtyPassed(), r.qtyFailed(), r.qtyPassed().add(r.qtyFailed()), trim(r.defectCode(), 40), trim(r.notes(), 500), tid, id, r.lineId());
        }
        jdbc.update("UPDATE sc_quality_inspections SET notes = COALESCE(?, notes), inspected_by = ?, inspected_by_name = ?, updated_at = now() WHERE tenant_id = ? AND id = ?",
                trim(req.notes(), 2000), userId, userName, tid, id);
        return get(tenantId, id);
    }

    @Transactional
    public Response complete(TenantId tenantId, UUID id, UUID userId, String userName, boolean raiseReturnForFailures) {
        UUID tid = tenantId.getValue();
        Map<String, Object> insp = requireOpen(tid, id);
        List<Map<String, Object>> lines = jdbc.queryForList("SELECT gr_line_id, catalogue_item_id, item_name, defect_code, qty_inspected, qty_passed, qty_failed, unit_cost FROM sc_quality_inspection_lines WHERE tenant_id = ? AND inspection_id = ?", tid, id);
        String result = QualityInspectionRules.result(lines.stream().map(l -> new QualityInspectionRules.Result(
                (BigDecimal) l.get("qty_inspected"), (BigDecimal) l.get("qty_passed"), (BigDecimal) l.get("qty_failed"))).toList());
        if (result == null) throw new HandyFlowException("Record at least one result before completing the inspection", HttpStatus.CONFLICT, "NOTHING_INSPECTED");

        // Failed stock leaves available stock now, before anyone decides what to do with it.
        List<QuarantineService.Failed> toHold = new ArrayList<>();
        for (Map<String, Object> l : lines) {
            BigDecimal f = (BigDecimal) l.get("qty_failed");
            if (f.signum() > 0) toHold.add(new QuarantineService.Failed((UUID) l.get("catalogue_item_id"), (String) l.get("item_name"), f, (BigDecimal) l.get("unit_cost"), (String) l.get("defect_code")));
        }
        int held = quarantine.hold(tenantId, userId, userName, id, (UUID) insp.get("goods_receipt_id"), toHold);

        UUID returnId = null;
        if (raiseReturnForFailures && !"PASSED".equals(result)) {
            List<ReturnDtos.LineRequest> failed = new ArrayList<>();
            for (Map<String, Object> l : lines) {
                BigDecimal f = (BigDecimal) l.get("qty_failed");
                if (f.signum() > 0) failed.add(new ReturnDtos.LineRequest((UUID) l.get("gr_line_id"), (String) l.get("item_name"), f, (BigDecimal) l.get("unit_cost")));
            }
            returnId = returns.createDraftFromInspection(tenantId, userId, userName, (UUID) insp.get("goods_receipt_id"), id, failed).id();
        }
        jdbc.update("UPDATE sc_quality_inspections SET status = 'COMPLETED', result = ?, completed_at = now(), inspected_by = ?, inspected_by_name = ?, return_id = ?, updated_at = now() WHERE tenant_id = ? AND id = ?",
                result, userId, userName, returnId, tid, id);
        log.info("[SCM] Inspection {} completed: {}{}", insp.get("inspection_number"), result, (held > 0 ? " (" + held + " line(s) quarantined)" : "") + (returnId != null ? " (draft return raised)" : ""));
        return get(tenantId, id);
    }

    @Transactional
    public Response cancel(TenantId tenantId, UUID id) {
        requireOpen(tenantId.getValue(), id);
        jdbc.update("UPDATE sc_quality_inspections SET status = 'CANCELLED', updated_at = now() WHERE tenant_id = ? AND id = ?", tenantId.getValue(), id);
        return get(tenantId, id);
    }

    @Transactional(readOnly = true)
    public List<Response> list(TenantId tenantId, UUID supplierId, UUID receiptId) {
        List<Object> args = new ArrayList<>(List.of(tenantId.getValue()));
        StringBuilder sql = new StringBuilder("SELECT id FROM sc_quality_inspections WHERE tenant_id = ?");
        if (supplierId != null) { sql.append(" AND supplier_id = ?"); args.add(supplierId); }
        if (receiptId != null) { sql.append(" AND goods_receipt_id = ?"); args.add(receiptId); }
        sql.append(" ORDER BY created_at DESC LIMIT 500");
        return jdbc.query(sql.toString(), (rs, n) -> (UUID) rs.getObject(1), args.toArray()).stream().map(i -> get(tenantId, i)).toList();
    }

    @Transactional(readOnly = true)
    public Response get(TenantId tenantId, UUID id) {
        UUID tid = tenantId.getValue();
        List<Map<String, Object>> rows = jdbc.queryForList("SELECT * FROM sc_quality_inspections WHERE tenant_id = ? AND id = ?", tid, id);
        if (rows.isEmpty()) throw new HandyFlowException("Inspection not found", HttpStatus.NOT_FOUND, "NOT_FOUND");
        Map<String, Object> r = rows.get(0);
        List<LineResponse> lines = jdbc.query("SELECT * FROM sc_quality_inspection_lines WHERE tenant_id = ? AND inspection_id = ? ORDER BY item_name",
                (rs, n) -> new LineResponse((UUID) rs.getObject("id"), (UUID) rs.getObject("gr_line_id"), rs.getString("item_name"),
                        rs.getBigDecimal("qty_accepted_on_receipt"), rs.getBigDecimal("qty_inspected"), rs.getBigDecimal("qty_passed"),
                        rs.getBigDecimal("qty_failed"), rs.getString("defect_code"), rs.getString("notes")), tid, id);
        Object done = r.get("completed_at"), created = r.get("created_at");
        return new Response(id, (String) r.get("inspection_number"), (UUID) r.get("goods_receipt_id"), (UUID) r.get("supplier_id"), (String) r.get("status"),
                (String) r.get("result"), (String) r.get("notes"), (String) r.get("inspected_by_name"),
                done instanceof Timestamp t ? t.toInstant() : null, (UUID) r.get("return_id"), created instanceof Timestamp t ? t.toInstant() : null, lines);
    }

    private Map<String, Object> requireOpen(UUID tid, UUID id) {
        List<Map<String, Object>> rows = jdbc.queryForList("SELECT * FROM sc_quality_inspections WHERE tenant_id = ? AND id = ? FOR UPDATE", tid, id);
        if (rows.isEmpty()) throw new HandyFlowException("Inspection not found", HttpStatus.NOT_FOUND, "NOT_FOUND");
        if (!"OPEN".equals(rows.get(0).get("status")))
            throw new HandyFlowException("Inspection " + rows.get(0).get("inspection_number") + " is " + rows.get(0).get("status") + " and can no longer change", HttpStatus.CONFLICT, "INSPECTION_CLOSED");
        return rows.get(0);
    }

    private static String trim(String s, int max) { return s == null || s.isBlank() ? null : s.length() > max ? s.substring(0, max) : s.trim(); }
    private static HandyFlowException bad(String m) { return new HandyFlowException(m, HttpStatus.BAD_REQUEST, "BAD_REQUEST"); }
}
