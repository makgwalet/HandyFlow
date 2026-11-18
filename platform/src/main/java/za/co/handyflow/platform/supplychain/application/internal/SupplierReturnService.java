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
import za.co.handyflow.platform.supplychain.application.internal.SupplierReturnRules.Line;
import za.co.handyflow.platform.supplychain.domain.model.ScInventory;
import za.co.handyflow.platform.supplychain.domain.model.ScStockMovement;
import za.co.handyflow.platform.supplychain.domain.repository.ScInventoryRepository;
import za.co.handyflow.platform.supplychain.domain.repository.ScStockMovementRepository;
import za.co.handyflow.platform.supplychain.dto.ReturnDtos.*;

import java.math.BigDecimal;
import java.sql.Timestamp;
import java.util.*;

/**
 * Returns to suppliers and claims against them. See {@link SupplierReturnRules} for the lifecycle.
 * <ul>
 *   <li>A line against a receipt line can return at most what that line accepted, less what other live returns already take.</li>
 *   <li>Approval is a second pair of eyes (SCM_RETURN_APPROVE): the person who raised it cannot approve it unless nobody else can.</li>
 *   <li>Dispatching a RETURN takes the stock out of the location it was received into, refusing if it has been used since.</li>
 *   <li>The credit note is handed to Accounts Payable on recording; AP captures, approves and applies it.</li>
 * </ul>
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class SupplierReturnService {

    private final JdbcTemplate jdbc;
    private final TenantSequenceService sequenceService;
    private final ScInventoryRepository inventoryRepo;
    private final ScStockMovementRepository movementRepo;
    private final ScmSeparationOfDuties sod;
    private final QuarantineService quarantine;
    private final za.co.handyflow.platform.ap.application.ApFacade apFacade;

    // ── Create ────────────────────────────────────────────────────────────────

    @Transactional
    public Response create(TenantId tenantId, UUID userId, String userName, CreateRequest req) {
        UUID tid = tenantId.getValue();
        if (req.kind() == null || !SupplierReturnRules.KINDS.contains(req.kind())) throw bad("kind must be RETURN or CLAIM");
        if (req.reason() == null || !SupplierReturnRules.REASONS.contains(req.reason())) throw bad("reason must be one of " + SupplierReturnRules.REASONS);
        if (req.lines() == null || req.lines().isEmpty()) throw bad("A return or claim needs at least one line");

        UUID supplierId = req.supplierId();
        UUID receiptId = req.goodsReceiptId();
        UUID poId = null, locationId = null;
        if (receiptId != null) {
            Map<String, Object> gr = one("SELECT supplier_id, purchase_order_id, received_to, status FROM sc_goods_receipts WHERE tenant_id = ? AND id = ?", tid, receiptId);
            if (gr == null) throw new HandyFlowException("Goods receipt not found", HttpStatus.NOT_FOUND, "NOT_FOUND");
            if (!"POSTED".equals(gr.get("status"))) throw new HandyFlowException("Only a POSTED goods receipt can be returned against", HttpStatus.CONFLICT, "GR_NOT_POSTED");
            supplierId = (UUID) gr.get("supplier_id");
            if (req.supplierId() != null && !req.supplierId().equals(supplierId)) throw bad("That goods receipt belongs to a different supplier");
            poId = (UUID) gr.get("purchase_order_id");
            locationId = (UUID) gr.get("received_to");
        }
        if (supplierId == null) throw bad("A supplier or a goods receipt is required");
        String supplierName = jdbc.query("SELECT name FROM sc_suppliers WHERE tenant_id = ? AND id = ?", (rs, n) -> rs.getString(1), tid, supplierId)
                .stream().findFirst().orElseThrow(() -> new HandyFlowException("Supplier not found", HttpStatus.NOT_FOUND, "NOT_FOUND"));

        List<Resolved> lines = resolveLines(tid, receiptId, req.lines());
        String problem = SupplierReturnRules.lineProblem(lines.stream().map(r -> new Line(r.qty, r.unitCost)).toList());
        if (problem != null) throw bad(problem);
        if (receiptId != null) checkNotOverReturned(tid, receiptId, null, lines);
        if ("RETURN".equals(req.kind()) && receiptId == null) throw bad("A return of goods must be raised against a posted goods receipt (a claim need not be)");

        UUID id = UUID.randomUUID();
        String number = "SRET-" + String.format("%05d", sequenceService.nextValue(tenantId, "SRET"));
        BigDecimal expected = SupplierReturnRules.expectedCredit(lines.stream().map(r -> new Line(r.qty, r.unitCost)).toList());
        jdbc.update("""
                INSERT INTO sc_supplier_returns (id, tenant_id, return_number, kind, reason, status, supplier_id, supplier_name, purchase_order_id,
                    goods_receipt_id, from_location_id, expected_credit, notes, created_by, created_by_name)
                VALUES (?, ?, ?, ?, ?, 'DRAFT', ?, ?, ?, ?, ?, ?, ?, ?, ?)""",
                id, tid, number, req.kind(), req.reason(), supplierId, supplierName, poId, receiptId, locationId, expected,
                trim(req.notes(), 2000), userId, userName);
        insertLines(tid, id, lines);
        log.info("[SCM] {} {} raised for supplier {} ({} line(s), expected credit {})", req.kind(), number, supplierName, lines.size(), expected);
        return get(tenantId, id);
    }

    // ── Lifecycle ─────────────────────────────────────────────────────────────

    @Transactional
    public Response submit(TenantId tenantId, UUID id) {
        Map<String, Object> r = lock(tenantId.getValue(), id);
        move(r, "SUBMITTED");
        jdbc.update("UPDATE sc_supplier_returns SET status = 'SUBMITTED', submitted_at = now(), updated_at = now() WHERE tenant_id = ? AND id = ?", tenantId.getValue(), id);
        return get(tenantId, id);
    }

    @Transactional
    public Response approve(TenantId tenantId, UUID id, UUID userId, String userName, String note) {
        UUID tid = tenantId.getValue();
        Map<String, Object> r = lock(tid, id);
        move(r, "APPROVED");
        sod.require(tenantId, "SCM_RETURN_APPROVE", (UUID) r.get("created_by"), userId,
                "You cannot approve a return or claim you raised yourself", "ScSupplierReturn", id, "Supplier return approval");
        jdbc.update("UPDATE sc_supplier_returns SET status = 'APPROVED', approved_by = ?, approved_by_name = ?, approved_at = now(), status_reason = ?, updated_at = now() WHERE tenant_id = ? AND id = ?",
                userId, userName, trim(note, 500), tid, id);
        return get(tenantId, id);
    }

    @Transactional
    public Response reject(TenantId tenantId, UUID id, String reason) {
        requireReason(reason);
        Map<String, Object> r = lock(tenantId.getValue(), id);
        move(r, "REJECTED");
        jdbc.update("UPDATE sc_supplier_returns SET status = 'REJECTED', status_reason = ?, updated_at = now() WHERE tenant_id = ? AND id = ?", trim(reason, 500), tenantId.getValue(), id);
        return get(tenantId, id);
    }

    @Transactional
    public Response cancel(TenantId tenantId, UUID id, String reason) {
        requireReason(reason);
        Map<String, Object> r = lock(tenantId.getValue(), id);
        move(r, "CANCELLED");
        jdbc.update("UPDATE sc_supplier_returns SET status = 'CANCELLED', status_reason = ?, updated_at = now() WHERE tenant_id = ? AND id = ?", trim(reason, 500), tenantId.getValue(), id);
        return get(tenantId, id);
    }

    /** Goods leave: stock out of the receiving location. Lines are locked in (catalogue item) order, as goods receipts do. */
    @Transactional
    public Response dispatch(TenantId tenantId, UUID id, UUID userId, String userName) {
        UUID tid = tenantId.getValue();
        Map<String, Object> r = lock(tid, id);
        move(r, "DISPATCHED");
        UUID location = (UUID) r.get("from_location_id");
        if (location == null) throw new HandyFlowException("This return has no receiving location to take stock from", HttpStatus.CONFLICT, "NO_LOCATION");
        String number = (String) r.get("return_number");

        List<Map<String, Object>> lines = jdbc.queryForList(
                "SELECT catalogue_item_id, item_name, qty, unit_cost FROM sc_supplier_return_lines WHERE tenant_id = ? AND return_id = ?", tid, id);
        lines.sort(Comparator.comparing((Map<String, Object> l) -> (UUID) l.get("catalogue_item_id"), GoodsReceivingService.UUID_ORDER));
        for (Map<String, Object> l : lines) {
            UUID item = (UUID) l.get("catalogue_item_id");
            if (item == null) continue;                                   // a service / non-stock line has no stock to move
            BigDecimal qty = (BigDecimal) l.get("qty");
            // A return raised from an inspection ships the quarantined stock first; only the rest comes from the receiving location.
            UUID inspection = (UUID) r.get("inspection_id");
            if (inspection != null) {
                qty = qty.subtract(quarantine.takeForReturn(tid, inspection, item, qty, id, number, userId, userName));
                if (qty.signum() <= 0) continue;
            }
            ScInventory inv = inventoryRepo.lockByTenantIdAndLocationIdAndCatalogueItemId(tid, location, item)
                    .orElseThrow(() -> new HandyFlowException("No stock record for " + l.get("item_name") + " at the receiving location", HttpStatus.CONFLICT, "NO_STOCK_RECORD"));
            if (inv.getQtyOnHand().compareTo(qty) < 0)
                throw new HandyFlowException("Cannot dispatch " + qty.stripTrailingZeros().toPlainString() + " of " + l.get("item_name") + ": only "
                        + inv.getQtyOnHand().stripTrailingZeros().toPlainString() + " on hand — the stock has been used. Adjust stock first.",
                        HttpStatus.CONFLICT, "STOCK_CONSUMED");
            BigDecimal delta = qty.negate();
            ScStockMovement mv = ScStockMovement.record(tid, inv, "RETURN_OUT", delta, (BigDecimal) l.get("unit_cost"),
                    "SUPPLIER_RETURN", id, number, userId, userName);
            inv.adjustQty(delta, (BigDecimal) l.get("unit_cost"));
            inventoryRepo.save(inv);
            movementRepo.save(mv);
        }
        jdbc.update("UPDATE sc_supplier_returns SET status = 'DISPATCHED', dispatched_at = now(), updated_at = now() WHERE tenant_id = ? AND id = ?", tid, id);
        log.info("[SCM] Return {} dispatched by {}", number, userName);
        return get(tenantId, id);
    }

    @Transactional
    public Response recordCredit(TenantId tenantId, UUID id, CreditRequest req, UUID userId) {
        UUID tid = tenantId.getValue();
        if (req == null || req.creditNoteRef() == null || req.creditNoteRef().isBlank()) throw bad("The supplier's credit note reference is required");
        if (req.creditAmount() == null || req.creditAmount().signum() <= 0) throw bad("The credit amount must be above zero");
        Map<String, Object> r = lock(tid, id);
        move(r, "CREDITED");
        BigDecimal expected = (BigDecimal) r.get("expected_credit");
        if (SupplierReturnRules.creditExceedsExpected(expected, req.creditAmount()))
            throw new HandyFlowException("The credit note (" + req.creditAmount() + ") is more than the " + expected + " this " + r.get("kind").toString().toLowerCase()
                    + " expected — correct the credit note or amend the claim", HttpStatus.CONFLICT, "CREDIT_EXCEEDS_CLAIM");
        BigDecimal vat = req.creditVat() == null ? BigDecimal.ZERO : req.creditVat();
        if (vat.signum() < 0 || vat.compareTo(req.creditAmount()) >= 0) throw bad("The VAT on the credit note must be from zero to less than the credit amount");

        // Hand the credit note to Accounts Payable: it is captured there (and approved there, if AP's policy asks) and applied
        // to the bill this order was invoiced on. If AP refuses it, nothing here is marked credited either (one transaction).
        UUID billId = jdbc.query("SELECT ap_bill_id FROM sc_supplier_invoices WHERE tenant_id = ? AND purchase_order_id = ? AND ap_bill_id IS NOT NULL ORDER BY created_at DESC LIMIT 1",
                (rs, n) -> (UUID) rs.getObject(1), tid, r.get("purchase_order_id")).stream().findFirst().orElse(null);
        UUID apCreditNoteId = apFacade.captureSupplierCreditNote(tenantId, req.creditNoteRef().trim(), (String) r.get("supplier_name"), billId,
                java.time.LocalDate.now(), "Supplier " + r.get("kind").toString().toLowerCase() + " " + r.get("return_number") + ": " + r.get("reason"),
                req.creditAmount().subtract(vat), vat, userId);
        jdbc.update("UPDATE sc_supplier_returns SET status = 'CREDITED', credit_note_ref = ?, credit_amount = ?, ap_credit_note_id = ?, credited_at = now(), updated_at = now() WHERE tenant_id = ? AND id = ?",
                req.creditNoteRef().trim(), req.creditAmount(), apCreditNoteId, tid, id);
        return get(tenantId, id);
    }

    // ── Read ──────────────────────────────────────────────────────────────────

    @Transactional(readOnly = true)
    public List<Response> list(TenantId tenantId, String status, UUID supplierId) {
        List<Object> args = new ArrayList<>(List.of(tenantId.getValue()));
        StringBuilder sql = new StringBuilder("SELECT id FROM sc_supplier_returns WHERE tenant_id = ?");
        if (status != null && !status.isBlank()) { sql.append(" AND status = ?"); args.add(status); }
        if (supplierId != null) { sql.append(" AND supplier_id = ?"); args.add(supplierId); }
        sql.append(" ORDER BY created_at DESC LIMIT 500");
        return jdbc.query(sql.toString(), (rs, n) -> (UUID) rs.getObject(1), args.toArray()).stream().map(i -> get(tenantId, i)).toList();
    }

    @Transactional(readOnly = true)
    public Response get(TenantId tenantId, UUID id) {
        UUID tid = tenantId.getValue();
        Map<String, Object> r = one("SELECT * FROM sc_supplier_returns WHERE tenant_id = ? AND id = ?", tid, id);
        if (r == null) throw new HandyFlowException("Return not found", HttpStatus.NOT_FOUND, "NOT_FOUND");
        List<LineResponse> lines = jdbc.query("SELECT * FROM sc_supplier_return_lines WHERE tenant_id = ? AND return_id = ? ORDER BY created_at, item_name",
                (rs, n) -> new LineResponse((UUID) rs.getObject("id"), (UUID) rs.getObject("gr_line_id"), (UUID) rs.getObject("po_line_id"),
                        (UUID) rs.getObject("catalogue_item_id"), rs.getString("item_name"), rs.getBigDecimal("qty"), rs.getBigDecimal("unit_cost"),
                        rs.getBigDecimal("qty").multiply(rs.getBigDecimal("unit_cost")).setScale(2, java.math.RoundingMode.HALF_UP)), tid, id);
        return new Response(id, (String) r.get("return_number"), (String) r.get("kind"), (String) r.get("reason"), (String) r.get("status"),
                (UUID) r.get("supplier_id"), (String) r.get("supplier_name"), (UUID) r.get("purchase_order_id"), (UUID) r.get("goods_receipt_id"),
                (UUID) r.get("inspection_id"), (BigDecimal) r.get("expected_credit"), (String) r.get("credit_note_ref"), (BigDecimal) r.get("credit_amount"),
                (String) r.get("notes"), (String) r.get("status_reason"), (String) r.get("created_by_name"), (String) r.get("approved_by_name"),
                instant(r.get("created_at")), instant(r.get("approved_at")), instant(r.get("dispatched_at")), instant(r.get("credited_at")), lines);
    }

    // ── For other modules (quality inspection) ────────────────────────────────

    /** A draft return built from lines already resolved by the caller. Used when an inspection fails goods. */
    @Transactional
    public Response createDraftFromInspection(TenantId tenantId, UUID userId, String userName, UUID receiptId, UUID inspectionId, List<LineRequest> lines) {
        Response created = create(tenantId, userId, userName, new CreateRequest("RETURN", "QUALITY", null, receiptId, "Raised from quality inspection", lines));
        jdbc.update("UPDATE sc_supplier_returns SET inspection_id = ? WHERE tenant_id = ? AND id = ?", inspectionId, tenantId.getValue(), created.id());
        return get(tenantId, created.id());
    }

    // ── internals ─────────────────────────────────────────────────────────────

    private record Resolved(UUID grLineId, UUID poLineId, UUID catalogueItemId, String itemName, BigDecimal qty, BigDecimal unitCost) {}

    private List<Resolved> resolveLines(UUID tid, UUID receiptId, List<LineRequest> requested) {
        List<Resolved> out = new ArrayList<>();
        for (LineRequest l : requested) {
            if (l.grLineId() != null) {
                if (receiptId == null) throw bad("A receipt line can only be returned with its goods receipt");
                Map<String, Object> g = one("SELECT po_line_id, catalogue_item_id, item_name, unit_cost FROM sc_gr_lines WHERE tenant_id = ? AND id = ? AND goods_receipt_id = ?", tid, l.grLineId(), receiptId);
                if (g == null) throw bad("Receipt line " + l.grLineId() + " is not on that goods receipt");
                out.add(new Resolved(l.grLineId(), (UUID) g.get("po_line_id"), (UUID) g.get("catalogue_item_id"), (String) g.get("item_name"),
                        l.qty(), l.unitCost() != null ? l.unitCost() : (BigDecimal) g.get("unit_cost")));
            } else {
                if (l.itemName() == null || l.itemName().isBlank()) throw bad("A line needs a receipt line or an item name");
                out.add(new Resolved(null, null, null, l.itemName().trim(), l.qty(), l.unitCost()));
            }
        }
        return out;
    }

    private void checkNotOverReturned(UUID tid, UUID receiptId, UUID excludeReturnId, List<Resolved> lines) {
        Map<String, BigDecimal> requested = new HashMap<>();
        for (Resolved r : lines) if (r.grLineId != null) requested.merge(r.grLineId.toString(), r.qty, BigDecimal::add);
        if (requested.isEmpty()) return;
        Map<String, BigDecimal> accepted = new HashMap<>();
        jdbc.query("SELECT id, qty_received - qty_rejected AS accepted FROM sc_gr_lines WHERE tenant_id = ? AND goods_receipt_id = ?",
                rs -> { accepted.put(rs.getString("id"), rs.getBigDecimal("accepted")); }, tid, receiptId);
        Map<String, BigDecimal> already = new HashMap<>();
        jdbc.query("""
                SELECT l.gr_line_id, SUM(l.qty) AS q FROM sc_supplier_return_lines l JOIN sc_supplier_returns r ON r.id = l.return_id
                 WHERE r.tenant_id = ? AND r.goods_receipt_id = ? AND r.status NOT IN ('REJECTED','CANCELLED') AND l.gr_line_id IS NOT NULL
                 GROUP BY l.gr_line_id""", rs -> { already.put(rs.getString(1), rs.getBigDecimal(2)); }, tid, receiptId);
        String problem = SupplierReturnRules.overReturn(requested, accepted, already);
        if (problem != null) throw new HandyFlowException(problem, HttpStatus.CONFLICT, "OVER_RETURN");
    }

    private void insertLines(UUID tid, UUID returnId, List<Resolved> lines) {
        for (Resolved l : lines) {
            jdbc.update("INSERT INTO sc_supplier_return_lines (id, tenant_id, return_id, gr_line_id, po_line_id, catalogue_item_id, item_name, qty, unit_cost) VALUES (?,?,?,?,?,?,?,?,?)",
                    UUID.randomUUID(), tid, returnId, l.grLineId, l.poLineId, l.catalogueItemId, l.itemName, l.qty, l.unitCost);
        }
    }

    /** Row lock, so two people acting on the same return cannot both succeed. */
    private Map<String, Object> lock(UUID tid, UUID id) {
        Map<String, Object> r = one("SELECT * FROM sc_supplier_returns WHERE tenant_id = ? AND id = ? FOR UPDATE", tid, id);
        if (r == null) throw new HandyFlowException("Return not found", HttpStatus.NOT_FOUND, "NOT_FOUND");
        return r;
    }

    private void move(Map<String, Object> r, String to) {
        String from = (String) r.get("status");
        if (!SupplierReturnRules.canMove((String) r.get("kind"), from, to))
            throw new HandyFlowException(r.get("return_number") + " is " + from + " and cannot move to " + to, HttpStatus.CONFLICT, "BAD_TRANSITION");
    }

    private Map<String, Object> one(String sql, Object... args) {
        List<Map<String, Object>> rows = jdbc.queryForList(sql, args);
        return rows.isEmpty() ? null : rows.get(0);
    }

    private static java.time.Instant instant(Object ts) { return ts instanceof Timestamp t ? t.toInstant() : ts instanceof java.time.OffsetDateTime o ? o.toInstant() : null; }
    private static String trim(String s, int max) { return s == null ? null : s.length() > max ? s.substring(0, max) : s; }
    private static void requireReason(String reason) { if (reason == null || reason.isBlank()) throw bad("A reason is required"); }
    private static HandyFlowException bad(String m) { return new HandyFlowException(m, HttpStatus.BAD_REQUEST, "BAD_REQUEST"); }
}
