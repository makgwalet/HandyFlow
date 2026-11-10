package za.co.handyflow.platform.warehousing.application.internal;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import za.co.handyflow.platform.shared.HandyFlowException;
import za.co.handyflow.platform.shared.ResourceNotFoundException;
import za.co.handyflow.platform.shared.TenantId;
import za.co.handyflow.platform.shared.TenantSequenceService;
import za.co.handyflow.platform.warehousing.domain.model.WhseInventory;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.UUID;

/**
 * Returns (RMA). A return is expected, received line by line into restocked, held or damaged stock with RETURN movements, and closed.
 * A return linked to a shipped order can never bring back more than that order shipped, counting what other returns already took back.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class WhseReturnsService {

    static final String SEQUENCE_PREFIX = "WAREHOUSING_RMA";

    private final JdbcTemplate jdbc;
    private final WhseInventoryService inventoryService;
    private final TenantSequenceService sequenceService;

    public record NewLine(UUID itemId, BigDecimal expectedQty) {}
    public record ReturnRow(UUID id, String rmaNumber, UUID clientId, String clientName, UUID outboundOrderId, String orderReference, String status,
                            String reason, String notes, Instant createdAt, Instant closedAt, String closeNote) {}
    public record LineRow(UUID id, UUID itemId, String sku, String description, BigDecimal expectedQty, BigDecimal restocked, BigDecimal held,
                          BigDecimal damaged, BigDecimal received, BigDecimal maxReturnable) {}
    public record Detail(ReturnRow header, List<LineRow> lines) {}

    // ── create ──────────────────────────────────────────────────────────────

    @Transactional
    public UUID create(TenantId tenantId, UUID clientId, UUID outboundOrderId, String reason, String notes, List<NewLine> lines, UUID actor) {
        requireActor(actor);
        UUID t = tenantId.getValue();
        if (reason == null || reason.trim().length() < 3) throw new IllegalArgumentException("Say why the goods are coming back.");
        if (lines == null || lines.isEmpty()) throw new IllegalArgumentException("A return needs at least one line.");
        if (count("SELECT count(*) FROM whse_clients WHERE id = ? AND tenant_id = ?", clientId, t) == 0) throw new ResourceNotFoundException("Client", String.valueOf(clientId));

        Set<UUID> seen = new HashSet<>();
        for (NewLine l : lines) {
            if (l.itemId() == null || l.expectedQty() == null || l.expectedQty().signum() <= 0) throw new IllegalArgumentException("Each line needs an item and a quantity above zero.");
            if (!seen.add(l.itemId())) throw new IllegalArgumentException("An item can appear once per return.");
            if (count("SELECT count(*) FROM whse_items WHERE id = ? AND tenant_id = ? AND client_id = ? AND deleted_at IS NULL", l.itemId(), t, clientId) == 0) {
                throw new ResourceNotFoundException("Item", l.itemId().toString());
            }
        }
        if (outboundOrderId != null) {
            List<String> st = jdbc.query("SELECT status || ':' || client_id FROM whse_outbound_orders WHERE id = ? AND tenant_id = ?", (rs, i) -> rs.getString(1), outboundOrderId, t);
            if (st.isEmpty()) throw new ResourceNotFoundException("Outbound order", outboundOrderId.toString());
            if (!st.get(0).equals("SHIPPED:" + clientId)) throw conflict("Returns can only be linked to a shipped order of the same client.", "ORDER_NOT_RETURNABLE");
            for (NewLine l : lines) {
                BigDecimal room = returnable(t, outboundOrderId, l.itemId(), null);
                if (l.expectedQty().compareTo(room) > 0) {
                    throw new IllegalArgumentException("Only " + room.stripTrailingZeros().toPlainString() + " of that item can still come back against this order.");
                }
            }
        }
        UUID id = UUID.randomUUID();
        String number = "RMA-" + String.format("%05d", sequenceService.nextValue(tenantId, SEQUENCE_PREFIX));
        jdbc.update("INSERT INTO whse_returns (id, tenant_id, rma_number, client_id, outbound_order_id, reason, notes, created_by) VALUES (?,?,?,?,?,?,?,?)",
                id, t, number, clientId, outboundOrderId, reason.trim(), blank(notes), actor);
        for (NewLine l : lines) {
            jdbc.update("INSERT INTO whse_return_lines (id, tenant_id, return_id, item_id, expected_qty) VALUES (?,?,?,?,?)", UUID.randomUUID(), t, id, l.itemId(), l.expectedQty());
        }
        return id;
    }

    // ── receive ─────────────────────────────────────────────────────────────

    @Transactional
    public UUID receive(TenantId tenantId, UUID returnId, UUID lineId, BigDecimal restock, BigDecimal quarantine, BigDecimal damaged, UUID locationId,
                        String reason, String idempotencyKey, UUID actor) {
        requireActor(actor);
        BigDecimal r = nz(restock), q = nz(quarantine), d = nz(damaged);
        if (r.signum() < 0 || q.signum() < 0 || d.signum() < 0) throw new IllegalArgumentException("Quantities cannot be negative.");
        BigDecimal total = r.add(q).add(d);
        if (total.signum() == 0) throw new IllegalArgumentException("Enter a quantity to receive.");
        if (q.add(d).signum() > 0 && (reason == null || reason.trim().length() < 5)) throw new IllegalArgumentException("Say what is wrong with the held or damaged goods (at least 5 characters).");
        if (locationId == null) throw new IllegalArgumentException("Every return must be put away somewhere.");
        UUID t = tenantId.getValue();

        String key = blank(idempotencyKey);
        if (key != null) {
            List<UUID> existing = jdbc.query("SELECT id FROM whse_return_receipts WHERE tenant_id = ? AND idempotency_key = ?", (rs, i) -> rs.getObject(1, UUID.class), t, key);
            if (!existing.isEmpty()) return existing.get(0);
        }

        record Hdr(UUID clientId, UUID orderId, String status, String rma) {}
        List<Hdr> h = jdbc.query("SELECT client_id, outbound_order_id, status, rma_number FROM whse_returns WHERE id = ? AND tenant_id = ? FOR UPDATE",
                (rs, i) -> new Hdr(rs.getObject(1, UUID.class), rs.getObject(2, UUID.class), rs.getString(3), rs.getString(4)), returnId, t);
        if (h.isEmpty()) throw new ResourceNotFoundException("Return", returnId.toString());
        Hdr hdr = h.get(0);
        if (!"EXPECTED".equals(hdr.status()) && !"RECEIVING".equals(hdr.status())) throw conflict("This return is " + hdr.status().toLowerCase() + " and cannot be received against.", "RETURN_FINISHED");
        List<UUID> items = jdbc.query("SELECT item_id FROM whse_return_lines WHERE id = ? AND return_id = ? AND tenant_id = ?", (rs, i) -> rs.getObject(1, UUID.class), lineId, returnId, t);
        if (items.isEmpty()) throw new ResourceNotFoundException("Return line", lineId.toString());
        UUID itemId = items.get(0);
        if (count("SELECT count(*) FROM whse_locations WHERE id = ? AND tenant_id = ? AND deleted_at IS NULL", locationId, t) == 0) throw new ResourceNotFoundException("Location", locationId.toString());

        if (hdr.orderId() != null) {
            BigDecimal room = returnable(t, hdr.orderId(), itemId, returnId).subtract(receivedOnLine(lineId));
            if (total.compareTo(room) > 0) {
                throw new IllegalArgumentException("That is more than was shipped. Only " + room.max(BigDecimal.ZERO).stripTrailingZeros().toPlainString() + " can still be received against this order.");
            }
        }

        UUID receiptId = UUID.randomUUID();
        jdbc.update("INSERT INTO whse_return_receipts (id, tenant_id, return_id, line_id, item_id, location_id, qty_restock, qty_quarantine, qty_damaged, reason, received_by, idempotency_key) VALUES (?,?,?,?,?,?,?,?,?,?,?,?)",
                receiptId, t, returnId, lineId, itemId, locationId, r, q, d, q.add(d).signum() > 0 ? reason.trim() : null, actor, key);
        post(tenantId, hdr.clientId(), itemId, locationId, r, WhseInventory.STATUS_AVAILABLE, returnId, hdr.rma(), receiptId, "R", null, actor);
        post(tenantId, hdr.clientId(), itemId, locationId, q, WhseInventory.STATUS_QUARANTINE, returnId, hdr.rma(), receiptId, "Q", reason, actor);
        post(tenantId, hdr.clientId(), itemId, locationId, d, WhseInventory.STATUS_DAMAGED, returnId, hdr.rma(), receiptId, "D", reason, actor);
        jdbc.update("UPDATE whse_returns SET status = 'RECEIVING', version = version + 1 WHERE id = ? AND status = 'EXPECTED'", returnId);
        return receiptId;
    }

    // ── close / cancel ──────────────────────────────────────────────────────

    @Transactional
    public void close(TenantId tenantId, UUID returnId, String note, UUID actor) {
        requireActor(actor);
        UUID t = tenantId.getValue();
        String status = lockStatus(t, returnId);
        if ("CLOSED".equals(status) || "CANCELLED".equals(status)) throw conflict("This return is already " + status.toLowerCase() + ".", "RETURN_FINISHED");
        List<LineRow> lines = lines(t, returnId);
        if (lines.stream().allMatch(l -> l.received().signum() == 0)) throw new IllegalArgumentException("Nothing has been received. Cancel the return instead.");
        boolean variance = lines.stream().anyMatch(l -> l.received().compareTo(l.expectedQty()) != 0);
        if (variance && (note == null || note.trim().length() < 5)) throw new IllegalArgumentException("Some lines came back short or over. Add a note explaining the difference (at least 5 characters).");
        jdbc.update("UPDATE whse_returns SET status = 'CLOSED', closed_by = ?, closed_at = now(), close_note = ?, version = version + 1 WHERE id = ?", actor, blank(note), returnId);
    }

    @Transactional
    public void cancel(TenantId tenantId, UUID returnId, UUID actor) {
        requireActor(actor);
        UUID t = tenantId.getValue();
        String status = lockStatus(t, returnId);
        if (!"EXPECTED".equals(status)) throw conflict("Only a return that has not been received can be cancelled.", "RETURN_NOT_CANCELLABLE");
        jdbc.update("UPDATE whse_returns SET status = 'CANCELLED', closed_by = ?, closed_at = now(), version = version + 1 WHERE id = ?", actor, returnId);
    }

    // ── read ────────────────────────────────────────────────────────────────

    @Transactional(readOnly = true)
    public List<ReturnRow> list(TenantId tenantId, UUID clientId, String status) {
        return jdbc.query(SQL_RETURNS + " WHERE r.tenant_id = ? AND (?::uuid IS NULL OR r.client_id = ?::uuid) AND (?::text IS NULL OR r.status = ?::text) ORDER BY r.created_at DESC LIMIT 200",
                (rs, i) -> header(rs), tenantId.getValue(), clientId, clientId, status, status);
    }

    @Transactional(readOnly = true)
    public Detail detail(TenantId tenantId, UUID returnId) {
        List<ReturnRow> h = jdbc.query(SQL_RETURNS + " WHERE r.tenant_id = ? AND r.id = ?", (rs, i) -> header(rs), tenantId.getValue(), returnId);
        if (h.isEmpty()) throw new ResourceNotFoundException("Return", returnId.toString());
        return new Detail(h.get(0), lines(tenantId.getValue(), returnId));
    }

    // ── internals ───────────────────────────────────────────────────────────

    private List<LineRow> lines(UUID tenant, UUID returnId) {
        UUID orderId = jdbc.query("SELECT outbound_order_id FROM whse_returns WHERE id = ?", (rs, i) -> rs.getObject(1, UUID.class), returnId).stream().findFirst().orElse(null);
        return jdbc.query(SQL_LINES, (rs, i) -> {
            BigDecimal rest = rs.getBigDecimal("restocked"), held = rs.getBigDecimal("held"), dmg = rs.getBigDecimal("damaged");
            UUID item = rs.getObject("item_id", UUID.class);
            BigDecimal max = orderId == null ? null : returnable(tenant, orderId, item, returnId);
            return new LineRow(rs.getObject("id", UUID.class), item, rs.getString("sku"), rs.getString("description"), rs.getBigDecimal("expected_qty"),
                    rest, held, dmg, rest.add(held).add(dmg), max);
        }, returnId, tenant);
    }

    /** What can still come back for an item on an order: shipped, less what other returns already received. This return's own receipts are excluded when {@code self} is given. */
    BigDecimal returnable(UUID tenant, UUID orderId, UUID itemId, UUID self) {
        // Shipped means what the ledger says left the building: the PICK movements posted when the order shipped. qty_picked on the order
        // line is only a picking-progress note and can be zero on an order that shipped.
        BigDecimal shipped = jdbc.queryForObject("SELECT coalesce(-sum(qty_change), 0) FROM whse_stock_movements WHERE tenant_id = ? AND movement_type = 'PICK' AND reference_type = 'OUTBOUND_ORDER' AND reference_id = ? AND item_id = ?", BigDecimal.class, tenant, orderId, itemId);
        if (shipped == null || shipped.signum() == 0) throw new IllegalArgumentException("That item was not shipped on the linked order.");
        BigDecimal other = jdbc.queryForObject("""
                SELECT coalesce(sum(CASE WHEN r.status = 'CLOSED' THEN x.received ELSE greatest(x.received, x.expected) END), 0)
                  FROM (SELECT l.return_id, l.expected_qty AS expected,
                               coalesce((SELECT sum(c.qty_restock + c.qty_quarantine + c.qty_damaged) FROM whse_return_receipts c WHERE c.line_id = l.id), 0) AS received
                          FROM whse_return_lines l WHERE l.item_id = ?) x
                  JOIN whse_returns r ON r.id = x.return_id
                 WHERE r.outbound_order_id = ? AND r.tenant_id = ? AND r.status <> 'CANCELLED' AND (?::uuid IS NULL OR r.id <> ?::uuid)""",
                BigDecimal.class, itemId, orderId, tenant, self, self);
        BigDecimal room = shipped.subtract(other == null ? BigDecimal.ZERO : other);
        return room.signum() > 0 ? room : BigDecimal.ZERO;
    }

    private BigDecimal receivedOnLine(UUID lineId) {
        BigDecimal v = jdbc.queryForObject("SELECT coalesce(sum(qty_restock + qty_quarantine + qty_damaged), 0) FROM whse_return_receipts WHERE line_id = ?", BigDecimal.class, lineId);
        return v == null ? BigDecimal.ZERO : v;
    }

    private void post(TenantId tenantId, UUID clientId, UUID itemId, UUID locationId, BigDecimal qty, String status, UUID returnId, String rma,
                      UUID receiptId, String suffix, String note, UUID actor) {
        if (qty.signum() == 0) return;
        inventoryService.receiveStockInStatus(tenantId, clientId, itemId, locationId, qty, status, "RETURN", "RETURN", returnId, rma,
                note == null ? null : note.trim(), actor, "ret:" + receiptId + ":" + suffix);
    }

    private String lockStatus(UUID tenant, UUID returnId) {
        List<String> s = jdbc.query("SELECT status FROM whse_returns WHERE id = ? AND tenant_id = ? FOR UPDATE", (rs, i) -> rs.getString(1), returnId, tenant);
        if (s.isEmpty()) throw new ResourceNotFoundException("Return", returnId.toString());
        return s.get(0);
    }

    private ReturnRow header(java.sql.ResultSet rs) throws java.sql.SQLException {
        java.sql.Timestamp closed = rs.getTimestamp("closed_at");
        return new ReturnRow(rs.getObject("id", UUID.class), rs.getString("rma_number"), rs.getObject("client_id", UUID.class), rs.getString("client_name"),
                rs.getObject("outbound_order_id", UUID.class), rs.getString("order_reference"), rs.getString("status"), rs.getString("reason"), rs.getString("notes"),
                rs.getTimestamp("created_at").toInstant(), closed == null ? null : closed.toInstant(), rs.getString("close_note"));
    }

    private long count(String sql, Object... args) { Long n = jdbc.queryForObject(sql, Long.class, args); return n == null ? 0 : n; }
    private static BigDecimal nz(BigDecimal v) { return v == null ? BigDecimal.ZERO : v; }
    private static String blank(String s) { return s == null || s.isBlank() ? null : s.trim(); }
    private static void requireActor(UUID actor) { if (actor == null) throw new AccessDeniedException("Your user could not be identified, so this step cannot be recorded."); }
    private static HandyFlowException conflict(String message, String code) { return new HandyFlowException(message, HttpStatus.CONFLICT, code); }

    static final String SQL_RETURNS = """
            SELECT r.id, r.rma_number, r.client_id, c.trading_name AS client_name, r.outbound_order_id, o.order_reference, r.status, r.reason, r.notes,
                   r.created_at, r.closed_at, r.close_note
              FROM whse_returns r
              JOIN whse_clients c ON c.id = r.client_id
              LEFT JOIN whse_outbound_orders o ON o.id = r.outbound_order_id""";

    static final String SQL_LINES = """
            SELECT l.id, l.item_id, it.sku, it.description, l.expected_qty,
                   coalesce(sum(c.qty_restock), 0) AS restocked, coalesce(sum(c.qty_quarantine), 0) AS held, coalesce(sum(c.qty_damaged), 0) AS damaged
              FROM whse_return_lines l
              JOIN whse_items it ON it.id = l.item_id
              LEFT JOIN whse_return_receipts c ON c.line_id = l.id
             WHERE l.return_id = ? AND l.tenant_id = ?
             GROUP BY l.id, it.sku, it.description
             ORDER BY it.sku""";
}
