package za.co.handyflow.platform.supplychain.application.internal;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import za.co.handyflow.platform.shared.HandyFlowException;
import za.co.handyflow.platform.shared.TenantId;
import za.co.handyflow.platform.shared.TenantSequenceService;
import za.co.handyflow.platform.supplychain.dto.ContractDtos.*;

import java.math.BigDecimal;
import java.sql.Date;
import java.sql.Timestamp;
import java.time.LocalDate;
import java.util.*;

/**
 * Supplier contracts: a term, agreed prices per item, an optional value cap, and a renewal notice. Activation is a second pair of
 * eyes (SCM_CONTRACT_MANAGE; the drafter cannot activate unless nobody else can). Purchase orders are checked against the agreed
 * prices when approved; a price above the contract raises a warning, it never blocks the order.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class ContractService {

    private final JdbcTemplate jdbc;
    private final TenantSequenceService sequenceService;
    private final ScmSeparationOfDuties sod;

    @Transactional
    public Response create(TenantId tenantId, UUID userId, String userName, SaveRequest req) {
        UUID tid = tenantId.getValue();
        validate(req);
        String supplierName = jdbc.query("SELECT name FROM sc_suppliers WHERE tenant_id = ? AND id = ?", (rs, n) -> rs.getString(1), tid, req.supplierId())
                .stream().findFirst().orElseThrow(() -> new HandyFlowException("Supplier not found", HttpStatus.NOT_FOUND, "NOT_FOUND"));
        UUID id = UUID.randomUUID();
        String number = "CTR-" + String.format("%05d", sequenceService.nextValue(tenantId, "CTR"));
        jdbc.update("""
                INSERT INTO sc_contracts (id, tenant_id, contract_number, supplier_id, supplier_name, title, status, start_date, end_date, currency, value_cap,
                    payment_terms_days, auto_renew, renewal_notice_days, document_id, notes, created_by, created_by_name)
                VALUES (?,?,?,?,?,?,'DRAFT',?,?,?,?,?,?,?,?,?,?,?)""",
                id, tid, number, req.supplierId(), supplierName, req.title().trim(), req.startDate(), req.endDate(),
                currency(req.currency()), req.valueCap(), req.paymentTermsDays(), Boolean.TRUE.equals(req.autoRenew()),
                req.renewalNoticeDays() != null ? req.renewalNoticeDays() : 60, req.documentId(), trim(req.notes(), 2000), userId, userName);
        replaceLines(tid, id, req.lines());
        return get(tenantId, id);
    }

    @Transactional
    public Response update(TenantId tenantId, UUID id, SaveRequest req) {
        UUID tid = tenantId.getValue();
        Map<String, Object> c = lock(tid, id);
        if (!"DRAFT".equals(c.get("status"))) throw new HandyFlowException("Only a DRAFT contract can be edited; this one is " + c.get("status"), HttpStatus.CONFLICT, "CONTRACT_NOT_DRAFT");
        validate(req);
        jdbc.update("""
                UPDATE sc_contracts SET title = ?, start_date = ?, end_date = ?, currency = ?, value_cap = ?, payment_terms_days = ?, auto_renew = ?,
                    renewal_notice_days = ?, document_id = ?, notes = ?, updated_at = now() WHERE tenant_id = ? AND id = ?""",
                req.title().trim(), req.startDate(), req.endDate(), currency(req.currency()), req.valueCap(), req.paymentTermsDays(),
                Boolean.TRUE.equals(req.autoRenew()), req.renewalNoticeDays() != null ? req.renewalNoticeDays() : 60, req.documentId(), trim(req.notes(), 2000), tid, id);
        replaceLines(tid, id, req.lines());
        return get(tenantId, id);
    }

    @Transactional
    public Response activate(TenantId tenantId, UUID id, UUID userId, String userName) {
        UUID tid = tenantId.getValue();
        Map<String, Object> c = lock(tid, id);
        move(c, "ACTIVE");
        LocalDate end = ((Date) c.get("end_date")).toLocalDate();
        if (ContractRules.hasEnded(end, LocalDate.now())) throw new HandyFlowException("The end date has already passed", HttpStatus.CONFLICT, "CONTRACT_ENDED");
        if (jdbc.queryForObject("SELECT count(*) FROM sc_contract_lines WHERE tenant_id = ? AND contract_id = ?", Long.class, tid, id) == 0)
            throw new HandyFlowException("Add at least one priced line before activating", HttpStatus.CONFLICT, "NO_LINES");
        sod.require(tenantId, "SCM_CONTRACT_MANAGE", (UUID) c.get("created_by"), userId,
                "You cannot activate a contract you drafted yourself", "ScContract", id, "Contract activation");
        jdbc.update("UPDATE sc_contracts SET status = 'ACTIVE', activated_by = ?, activated_by_name = ?, activated_at = now(), updated_at = now() WHERE tenant_id = ? AND id = ?", userId, userName, tid, id);
        return get(tenantId, id);
    }

    @Transactional
    public Response terminate(TenantId tenantId, UUID id, String reason) {
        if (reason == null || reason.isBlank()) throw bad("A reason is required");
        UUID tid = tenantId.getValue();
        move(lock(tid, id), "TERMINATED");
        jdbc.update("UPDATE sc_contracts SET status = 'TERMINATED', terminated_reason = ?, updated_at = now() WHERE tenant_id = ? AND id = ?", trim(reason, 500), tid, id);
        return get(tenantId, id);
    }

    @Transactional(readOnly = true)
    public List<Response> list(TenantId tenantId, String status, UUID supplierId, Boolean expiringSoon) {
        List<Object> args = new ArrayList<>(List.of(tenantId.getValue()));
        StringBuilder sql = new StringBuilder("SELECT id FROM sc_contracts WHERE tenant_id = ?");
        if (status != null && !status.isBlank()) { sql.append(" AND status = ?"); args.add(status); }
        if (supplierId != null) { sql.append(" AND supplier_id = ?"); args.add(supplierId); }
        if (Boolean.TRUE.equals(expiringSoon)) sql.append(" AND status = 'ACTIVE' AND end_date - renewal_notice_days <= CURRENT_DATE");
        sql.append(" ORDER BY end_date LIMIT 500");
        return jdbc.query(sql.toString(), (rs, n) -> (UUID) rs.getObject(1), args.toArray()).stream().map(i -> get(tenantId, i)).toList();
    }

    @Transactional(readOnly = true)
    public Response get(TenantId tenantId, UUID id) {
        UUID tid = tenantId.getValue();
        List<Map<String, Object>> rows = jdbc.queryForList("SELECT * FROM sc_contracts WHERE tenant_id = ? AND id = ?", tid, id);
        if (rows.isEmpty()) throw new HandyFlowException("Contract not found", HttpStatus.NOT_FOUND, "NOT_FOUND");
        Map<String, Object> c = rows.get(0);
        List<LineResponse> lines = jdbc.query("SELECT * FROM sc_contract_lines WHERE tenant_id = ? AND contract_id = ? ORDER BY description",
                (rs, n) -> new LineResponse((UUID) rs.getObject("id"), (UUID) rs.getObject("catalogue_item_id"), rs.getString("description"),
                        rs.getBigDecimal("agreed_unit_price"), rs.getBigDecimal("min_qty")), tid, id);
        LocalDate end = ((Date) c.get("end_date")).toLocalDate();
        int notice = ((Number) c.get("renewal_notice_days")).intValue();
        boolean due = "ACTIVE".equals(c.get("status")) && ContractRules.renewalNoticeDue(end, LocalDate.now(), notice);
        Object act = c.get("activated_at");
        return new Response(id, (String) c.get("contract_number"), (UUID) c.get("supplier_id"), (String) c.get("supplier_name"), (String) c.get("title"),
                (String) c.get("status"), ((Date) c.get("start_date")).toLocalDate(), end, (String) c.get("currency"), (BigDecimal) c.get("value_cap"),
                c.get("payment_terms_days") == null ? null : ((Number) c.get("payment_terms_days")).intValue(), Boolean.TRUE.equals(c.get("auto_renew")), notice,
                (UUID) c.get("document_id"), (String) c.get("notes"), (String) c.get("terminated_reason"), (String) c.get("created_by_name"),
                (String) c.get("activated_by_name"), act instanceof Timestamp t ? t.toInstant() : null, due, lines);
    }

    /** PO lines priced above the agreed price on an ACTIVE contract with the same supplier, valid on the order date. */
    @Transactional(readOnly = true)
    public List<PriceVariance> priceVariances(TenantId tenantId, UUID purchaseOrderId) {
        UUID tid = tenantId.getValue();
        List<Map<String, Object>> po = jdbc.queryForList("SELECT supplier_id, order_date FROM sc_purchase_orders WHERE tenant_id = ? AND id = ?", tid, purchaseOrderId);
        if (po.isEmpty()) return List.of();
        UUID supplier = (UUID) po.get(0).get("supplier_id");
        Date orderDate = (Date) po.get(0).get("order_date");
        LocalDate on = orderDate != null ? orderDate.toLocalDate() : LocalDate.now();
        List<PriceVariance> out = new ArrayList<>();
        jdbc.query("""
                SELECT l.id AS po_line_id, l.item_name, l.unit_cost, k.agreed_unit_price, c.contract_number
                  FROM sc_po_lines l
                  JOIN sc_contract_lines k ON k.tenant_id = ? AND k.catalogue_item_id = l.catalogue_item_id
                  JOIN sc_contracts c ON c.id = k.contract_id AND c.tenant_id = k.tenant_id AND c.supplier_id = ? AND c.status = 'ACTIVE'
                       AND ? BETWEEN c.start_date AND c.end_date
                 WHERE l.purchase_order_id = ? AND l.catalogue_item_id IS NOT NULL""",
                rs -> {
                    BigDecimal over = ContractRules.overAgreedPct(rs.getBigDecimal("unit_cost"), rs.getBigDecimal("agreed_unit_price"));
                    if (over != null) out.add(new PriceVariance((UUID) rs.getObject("po_line_id"), rs.getString("item_name"), rs.getBigDecimal("unit_cost"),
                            rs.getBigDecimal("agreed_unit_price"), over, rs.getString("contract_number")));
                }, tid, supplier, Date.valueOf(on), purchaseOrderId);
        return out;
    }

    // ── internals ─────────────────────────────────────────────────────────────

    private void validate(SaveRequest req) {
        if (req == null || req.supplierId() == null) throw bad("A supplier is required");
        if (req.title() == null || req.title().isBlank()) throw bad("A title is required");
        String problem = ContractRules.problem(req.startDate(), req.endDate(), req.renewalNoticeDays() != null ? req.renewalNoticeDays() : 60, req.valueCap());
        if (problem != null) throw bad(problem);
        if (req.lines() != null) for (LineRequest l : req.lines()) {
            if (l.description() == null || l.description().isBlank()) throw bad("Every contract line needs a description");
            if (l.agreedUnitPrice() == null || l.agreedUnitPrice().signum() < 0) throw bad("Every contract line needs an agreed price of zero or more");
        }
    }

    private void replaceLines(UUID tid, UUID contractId, List<LineRequest> lines) {
        jdbc.update("DELETE FROM sc_contract_lines WHERE tenant_id = ? AND contract_id = ?", tid, contractId);
        if (lines == null) return;
        for (LineRequest l : lines)
            jdbc.update("INSERT INTO sc_contract_lines (id, tenant_id, contract_id, catalogue_item_id, description, agreed_unit_price, min_qty) VALUES (?,?,?,?,?,?,?)",
                    UUID.randomUUID(), tid, contractId, l.catalogueItemId(), l.description().trim(), l.agreedUnitPrice(), l.minQty());
    }

    private Map<String, Object> lock(UUID tid, UUID id) {
        List<Map<String, Object>> rows = jdbc.queryForList("SELECT * FROM sc_contracts WHERE tenant_id = ? AND id = ? FOR UPDATE", tid, id);
        if (rows.isEmpty()) throw new HandyFlowException("Contract not found", HttpStatus.NOT_FOUND, "NOT_FOUND");
        return rows.get(0);
    }

    private void move(Map<String, Object> c, String to) {
        if (!ContractRules.canMove((String) c.get("status"), to))
            throw new HandyFlowException(c.get("contract_number") + " is " + c.get("status") + " and cannot move to " + to, HttpStatus.CONFLICT, "BAD_TRANSITION");
    }

    private static String currency(String c) { return c == null || c.isBlank() ? "ZAR" : c.trim().toUpperCase(); }
    private static String trim(String s, int max) { return s == null || s.isBlank() ? null : s.length() > max ? s.substring(0, max) : s.trim(); }
    private static HandyFlowException bad(String m) { return new HandyFlowException(m, HttpStatus.BAD_REQUEST, "BAD_REQUEST"); }
}
