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

import java.math.BigDecimal;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.UUID;
import java.util.regex.Pattern;

/**
 * The serial register. An item that is serial tracked carries one serial per unit through receiving, picking, shipping and returns, and
 * every step is written to the serial's history. A serial is IN_STOCK, PICKED (assigned to an order line) or SHIPPED.
 *
 * What this does not do: it does not track which location a serial sits in. The stock balance stays the source of truth for how many
 * units are where; the register answers "which units, and where did they go". Counts are held in step at the doors (a receipt must
 * bring exactly as many serials as units, a shipment cannot leave without a serial per unit), not by a per-location invariant.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class WhseSerialService {

    static final Pattern FORMAT = Pattern.compile("[A-Za-z0-9][A-Za-z0-9._/-]{0,79}");
    static final int MAX_PER_CALL = 1000;

    private final JdbcTemplate jdbc;

    public record OrderLine(UUID lineId, UUID itemId, String sku, String description, BigDecimal toShip, List<String> serials) {}
    public record Event(String event, String reference, Instant at, UUID actorId) {}
    public record Trace(UUID id, String serialNumber, String status, UUID clientId, String clientName, UUID itemId, String sku, String description,
                        UUID outboundOrderId, String orderReference, Instant shippedAt, int returnCount, List<Event> history) {}

    // ── the item switch ─────────────────────────────────────────────────────

    @Transactional(readOnly = true)
    public boolean isSerialTracked(TenantId tenantId, UUID itemId) {
        return tracked(tenantId.getValue(), itemId);
    }

    /** Switches serial tracking for an item. Refused while it has stock, because that stock has no serials; switching off is also refused once serials exist. */
    @Transactional
    public boolean setSerialTracked(TenantId tenantId, UUID itemId, boolean on, UUID actor) {
        if (actor == null) throw new AccessDeniedException("Your user could not be identified, so this step cannot be recorded.");
        UUID t = tenantId.getValue();
        boolean now = tracked(t, itemId);
        if (now == on) return on;
        if (count("SELECT count(*) FROM whse_inventory WHERE tenant_id = ? AND item_id = ? AND qty_on_hand > 0", t, itemId) > 0) {
            throw new HandyFlowException("This item has stock on hand, so serial tracking cannot be changed. Count it out first, or switch it on while stock is zero.",
                    HttpStatus.CONFLICT, "ITEM_HAS_STOCK");
        }
        if (!on && count("SELECT count(*) FROM whse_serials WHERE tenant_id = ? AND item_id = ?", t, itemId) > 0) {
            throw new HandyFlowException("Serial numbers have already been recorded for this item, so tracking cannot be switched off.", HttpStatus.CONFLICT, "SERIALS_EXIST");
        }
        jdbc.update("UPDATE whse_items SET serial_tracked = ?, updated_at = now() WHERE id = ? AND tenant_id = ?", on, itemId, t);
        jdbc.update("INSERT INTO audit_events (tenant_id, actor_type, actor_id, action, item_type, item_id, after_json) VALUES (?, 'STAFF', ?, ?, ?, ?, ?::jsonb)",
                t, actor, "WHSE_ITEM_SERIAL_TRACKING_CHANGED", "WHSE_ITEM", itemId, "{\"serialTracked\":" + on + "}");
        return on;
    }

    // ── receiving ───────────────────────────────────────────────────────────

    /** Records the serials that arrived on a receipt. For a serial-tracked item there must be exactly one per unit; for any other item there must be none. */
    @Transactional
    public int registerReceipt(TenantId tenantId, UUID shipmentId, UUID lineId, UUID receiptEventId, BigDecimal totalQty, List<String> rawSerials, UUID actor) {
        UUID t = tenantId.getValue();
        record Ln(UUID itemId, UUID clientId) {}
        List<Ln> ln = jdbc.query("SELECT l.item_id, s.client_id FROM whse_inbound_shipment_lines l JOIN whse_inbound_shipments s ON s.id = l.shipment_id WHERE l.id = ? AND l.shipment_id = ? AND l.tenant_id = ?",
                (rs, i) -> new Ln(rs.getObject(1, UUID.class), rs.getObject(2, UUID.class)), lineId, shipmentId, t);
        if (ln.isEmpty()) throw new ResourceNotFoundException("WhseInboundShipmentLine", lineId.toString());
        return register(t, ln.get(0).clientId(), ln.get(0).itemId(), receiptEventId, totalQty, rawSerials, actor, "RECEIVED", receiptEventId.toString());
    }

    private int register(UUID t, UUID clientId, UUID itemId, UUID receiptEventId, BigDecimal totalQty, List<String> raw, UUID actor, String event, String reference) {
        List<String> serials = clean(raw);
        if (!tracked(t, itemId)) {
            if (!serials.isEmpty()) throw new IllegalArgumentException("This item is not serial tracked, so serial numbers cannot be recorded for it.");
            return 0;
        }
        // A replay of the same receipt (same idempotency key) finds its serials already recorded.
        if (receiptEventId != null && count("SELECT count(*) FROM whse_serials WHERE tenant_id = ? AND receipt_event_id = ?", t, receiptEventId) > 0) return 0;
        requireCount(totalQty, serials, "received");
        List<String> clash = existing(t, clientId, itemId, serials);
        if (!clash.isEmpty()) {
            throw new HandyFlowException("Already on file for this item: " + String.join(", ", clash.stream().limit(3).toList()) + (clash.size() > 3 ? " and " + (clash.size() - 3) + " more" : "")
                    + ". A serial that has shipped comes back through a return.", HttpStatus.CONFLICT, "SERIAL_EXISTS");
        }
        List<Object[]> rows = new ArrayList<>(), events = new ArrayList<>();
        for (String s : serials) {
            UUID id = UUID.randomUUID();
            rows.add(new Object[]{id, t, clientId, itemId, s, receiptEventId, actor});
            events.add(new Object[]{UUID.randomUUID(), t, id, event, reference, actor});
        }
        jdbc.batchUpdate("INSERT INTO whse_serials (id, tenant_id, client_id, item_id, serial_number, status, receipt_event_id, created_by) VALUES (?,?,?,?,?,'IN_STOCK',?,?)", rows);
        jdbc.batchUpdate("INSERT INTO whse_serial_events (id, tenant_id, serial_id, event, reference, actor_id) VALUES (?,?,?,?,?,?)", events);
        return serials.size();
    }

    // ── picking ─────────────────────────────────────────────────────────────

    /** Assigns serials to an order line being picked. Each must be in stock for that client and item. */
    @Transactional
    public int pick(TenantId tenantId, UUID orderId, UUID lineId, List<String> raw, UUID actor) {
        UUID t = tenantId.getValue();
        record Ln(UUID itemId, BigDecimal toShip, UUID clientId, String status, String ref) {}
        List<Ln> ln = jdbc.query("""
                SELECT l.item_id, l.qty_ordered - l.qty_short, o.client_id, o.status, o.order_reference
                  FROM whse_outbound_order_lines l JOIN whse_outbound_orders o ON o.id = l.order_id
                 WHERE l.id = ? AND l.order_id = ? AND l.tenant_id = ?""",
                (rs, i) -> new Ln(rs.getObject(1, UUID.class), rs.getBigDecimal(2), rs.getObject(3, UUID.class), rs.getString(4), rs.getString(5)), lineId, orderId, t);
        if (ln.isEmpty()) throw new ResourceNotFoundException("Order line", lineId.toString());
        Ln l = ln.get(0);
        if (!"PICKING".equals(l.status())) throw new HandyFlowException("Serials can only be recorded while the order is being picked.", HttpStatus.CONFLICT, "ORDER_NOT_PICKING");
        if (!tracked(t, l.itemId())) throw new IllegalArgumentException("This item is not serial tracked.");
        List<String> serials = clean(raw);
        if (serials.isEmpty()) throw new IllegalArgumentException("Scan or enter at least one serial number.");
        long have = count("SELECT count(*) FROM whse_serials WHERE outbound_line_id = ? AND status = 'PICKED'", lineId);
        if (have + serials.size() > l.toShip().longValue()) {
            throw new IllegalArgumentException("That is more serials than units on this line: " + have + " already recorded, " + l.toShip().stripTrailingZeros().toPlainString() + " to ship.");
        }
        for (String s : serials) {
            List<UUID> got = jdbc.query("UPDATE whse_serials SET status = 'PICKED', outbound_order_id = ?, outbound_line_id = ? WHERE tenant_id = ? AND client_id = ? AND item_id = ? AND upper(serial_number) = upper(?) AND status = 'IN_STOCK' RETURNING id",
                    (rs, i) -> rs.getObject(1, UUID.class), orderId, lineId, t, l.clientId(), l.itemId(), s);
            if (got.isEmpty()) throw new HandyFlowException(whyNot(t, l.clientId(), l.itemId(), s), HttpStatus.CONFLICT, "SERIAL_NOT_AVAILABLE");
            event(t, got.get(0), "PICKED", l.ref(), actor);
        }
        return serials.size();
    }

    /** Takes serials back off an order line while it is still being picked. */
    @Transactional
    public int unpick(TenantId tenantId, UUID orderId, UUID lineId, List<String> raw, UUID actor) {
        UUID t = tenantId.getValue();
        List<String> status = jdbc.query("SELECT o.status FROM whse_outbound_order_lines l JOIN whse_outbound_orders o ON o.id = l.order_id WHERE l.id = ? AND l.order_id = ? AND l.tenant_id = ?",
                (rs, i) -> rs.getString(1), lineId, orderId, t);
        if (status.isEmpty()) throw new ResourceNotFoundException("Order line", lineId.toString());
        if (!"PICKING".equals(status.get(0))) throw new HandyFlowException("Serials can only be taken off while the order is being picked.", HttpStatus.CONFLICT, "ORDER_NOT_PICKING");
        List<String> serials = clean(raw);
        if (serials.isEmpty()) throw new IllegalArgumentException("Choose at least one serial number.");
        for (String s : serials) {
            List<UUID> got = jdbc.query("UPDATE whse_serials SET status = 'IN_STOCK', outbound_order_id = NULL, outbound_line_id = NULL WHERE tenant_id = ? AND outbound_line_id = ? AND upper(serial_number) = upper(?) AND status = 'PICKED' RETURNING id",
                    (rs, i) -> rs.getObject(1, UUID.class), t, lineId, s);
            if (got.isEmpty()) throw new IllegalArgumentException("Serial " + s + " is not recorded on this line.");
            event(t, got.get(0), "UNPICKED", orderId.toString(), actor);
        }
        return serials.size();
    }

    /** The order's lines that need serials, with what has been recorded so far. */
    @Transactional(readOnly = true)
    public List<OrderLine> orderLines(TenantId tenantId, UUID orderId) {
        UUID t = tenantId.getValue();
        List<OrderLine> lines = jdbc.query("""
                SELECT l.id, l.item_id, i.sku, i.description, l.qty_ordered - l.qty_short
                  FROM whse_outbound_order_lines l JOIN whse_items i ON i.id = l.item_id
                 WHERE l.order_id = ? AND l.tenant_id = ? AND i.serial_tracked ORDER BY i.sku""",
                (rs, n) -> new OrderLine(rs.getObject(1, UUID.class), rs.getObject(2, UUID.class), rs.getString(3), rs.getString(4), rs.getBigDecimal(5), new ArrayList<>()), orderId, t);
        for (OrderLine l : lines) {
            l.serials().addAll(jdbc.query("SELECT serial_number FROM whse_serials WHERE outbound_line_id = ? ORDER BY serial_number", (rs, i) -> rs.getString(1), l.lineId()));
        }
        return lines;
    }

    /** Refuses to ship an order whose serial-tracked lines do not each have a serial for every unit leaving. */
    @Transactional(readOnly = true)
    public void requireCaptured(TenantId tenantId, UUID orderId) {
        for (OrderLine l : orderLines(tenantId, orderId)) {
            if (l.toShip().signum() > 0 && l.serials().size() != l.toShip().longValue()) {
                throw new HandyFlowException(l.sku() + " needs " + l.toShip().stripTrailingZeros().toPlainString() + " serial number(s) before this order can ship, but "
                        + l.serials().size() + " are recorded.", HttpStatus.CONFLICT, "SERIALS_MISSING");
            }
        }
    }

    /** Picked serials become shipped. */
    @Transactional
    public int markShipped(TenantId tenantId, UUID orderId, UUID actor) {
        UUID t = tenantId.getValue();
        List<UUID> ids = jdbc.query("UPDATE whse_serials SET status = 'SHIPPED', shipped_at = now() WHERE tenant_id = ? AND outbound_order_id = ? AND status = 'PICKED' RETURNING id",
                (rs, i) -> rs.getObject(1, UUID.class), t, orderId);
        for (UUID id : ids) event(t, id, "SHIPPED", orderId.toString(), actor);
        return ids.size();
    }

    /** A cancelled order gives its picked serials back. */
    @Transactional
    public int releasePicked(TenantId tenantId, UUID orderId, UUID actor) {
        UUID t = tenantId.getValue();
        List<UUID> ids = jdbc.query("UPDATE whse_serials SET status = 'IN_STOCK', outbound_order_id = NULL, outbound_line_id = NULL WHERE tenant_id = ? AND outbound_order_id = ? AND status = 'PICKED' RETURNING id",
                (rs, i) -> rs.getObject(1, UUID.class), t, orderId);
        for (UUID id : ids) event(t, id, "UNPICKED", orderId.toString(), actor);
        return ids.size();
    }

    // ── returns ─────────────────────────────────────────────────────────────

    /** Brings returned serials back into stock. Each must have shipped (on the return's order when it names one). */
    @Transactional
    public int acceptReturn(TenantId tenantId, UUID returnId, UUID lineId, BigDecimal totalQty, List<String> raw, UUID actor) {
        UUID t = tenantId.getValue();
        record Hd(UUID clientId, UUID orderId, UUID itemId) {}
        List<Hd> hd = jdbc.query("SELECT r.client_id, r.outbound_order_id, l.item_id FROM whse_returns r JOIN whse_return_lines l ON l.return_id = r.id WHERE r.id = ? AND l.id = ? AND r.tenant_id = ?",
                (rs, i) -> new Hd(rs.getObject(1, UUID.class), rs.getObject(2, UUID.class), rs.getObject(3, UUID.class)), returnId, lineId, t);
        if (hd.isEmpty()) throw new ResourceNotFoundException("Return line", lineId.toString());
        Hd h = hd.get(0);
        List<String> serials = clean(raw);
        if (!tracked(t, h.itemId())) {
            if (!serials.isEmpty()) throw new IllegalArgumentException("This item is not serial tracked, so serial numbers cannot be recorded for it.");
            return 0;
        }
        requireCount(totalQty, serials, "returned");
        for (String s : serials) {
            List<UUID> got = h.orderId() == null
                    ? jdbc.query("UPDATE whse_serials SET status = 'IN_STOCK', outbound_order_id = NULL, outbound_line_id = NULL, shipped_at = NULL, return_count = return_count + 1 WHERE tenant_id = ? AND client_id = ? AND item_id = ? AND upper(serial_number) = upper(?) AND status = 'SHIPPED' RETURNING id",
                            (rs, i) -> rs.getObject(1, UUID.class), t, h.clientId(), h.itemId(), s)
                    : jdbc.query("UPDATE whse_serials SET status = 'IN_STOCK', outbound_order_id = NULL, outbound_line_id = NULL, shipped_at = NULL, return_count = return_count + 1 WHERE tenant_id = ? AND client_id = ? AND item_id = ? AND upper(serial_number) = upper(?) AND status = 'SHIPPED' AND outbound_order_id = ? RETURNING id",
                            (rs, i) -> rs.getObject(1, UUID.class), t, h.clientId(), h.itemId(), s, h.orderId());
            if (got.isEmpty()) {
                throw new HandyFlowException("Serial " + s + " has not shipped" + (h.orderId() == null ? "" : " on this order") + ", so it cannot be returned.", HttpStatus.CONFLICT, "SERIAL_NOT_SHIPPED");
            }
            event(t, got.get(0), "RETURNED", returnId.toString(), actor);
        }
        return serials.size();
    }

    // ── trace ───────────────────────────────────────────────────────────────

    /** One serial with its whole history. */
    @Transactional(readOnly = true)
    public Trace trace(TenantId tenantId, UUID serialId) {
        UUID t = tenantId.getValue();
        List<Trace> rows = jdbc.query("""
                SELECT s.id, s.serial_number, s.status, s.client_id, c.trading_name, s.item_id, i.sku, i.description, s.outbound_order_id, o.order_reference, s.shipped_at, s.return_count
                  FROM whse_serials s JOIN whse_clients c ON c.id = s.client_id JOIN whse_items i ON i.id = s.item_id LEFT JOIN whse_outbound_orders o ON o.id = s.outbound_order_id
                 WHERE s.id = ? AND s.tenant_id = ?""",
                (rs, n) -> new Trace(rs.getObject(1, UUID.class), rs.getString(2), rs.getString(3), rs.getObject(4, UUID.class), rs.getString(5), rs.getObject(6, UUID.class),
                        rs.getString(7), rs.getString(8), rs.getObject(9, UUID.class), rs.getString(10),
                        rs.getTimestamp(11) == null ? null : rs.getTimestamp(11).toInstant(), rs.getInt(12), new ArrayList<>()), serialId, t);
        if (rows.isEmpty()) throw new ResourceNotFoundException("Serial", serialId.toString());
        Trace tr = rows.get(0);
        tr.history().addAll(jdbc.query("SELECT event, reference, at, actor_id FROM whse_serial_events WHERE serial_id = ? AND tenant_id = ? ORDER BY at, id",
                (rs, i) -> new Event(rs.getString(1), rs.getString(2), rs.getTimestamp(3).toInstant(), rs.getObject(4, UUID.class)), serialId, t));
        return tr;
    }

    // ── helpers ─────────────────────────────────────────────────────────────

    /** Trims, checks the format and refuses duplicates (ignoring case). */
    static List<String> clean(List<String> raw) {
        if (raw == null) return List.of();
        if (raw.size() > MAX_PER_CALL) throw new IllegalArgumentException("At most " + MAX_PER_CALL + " serial numbers at a time.");
        List<String> out = new ArrayList<>();
        Set<String> seen = new HashSet<>();
        for (String r : raw) {
            String s = r == null ? "" : r.trim();
            if (s.isEmpty()) continue;
            if (!FORMAT.matcher(s).matches()) throw new IllegalArgumentException("“" + s + "” is not a valid serial number. Use letters, digits and . _ / - only, up to 80 characters.");
            if (!seen.add(s.toUpperCase(Locale.ROOT))) throw new IllegalArgumentException("Serial " + s + " is listed twice.");
            out.add(s);
        }
        return out;
    }

    private static void requireCount(BigDecimal qty, List<String> serials, String verb) {
        if (qty == null || qty.stripTrailingZeros().scale() > 0) throw new IllegalArgumentException("A serial-tracked item can only be handled in whole units.");
        if (qty.longValue() != serials.size()) {
            throw new IllegalArgumentException(qty.longValue() + " unit(s) " + verb + " need " + qty.longValue() + " serial number(s), but " + serials.size() + " were given.");
        }
    }

    private String whyNot(UUID t, UUID clientId, UUID itemId, String serial) {
        List<String> st = jdbc.query("SELECT status FROM whse_serials WHERE tenant_id = ? AND client_id = ? AND item_id = ? AND upper(serial_number) = upper(?)",
                (rs, i) -> rs.getString(1), t, clientId, itemId, serial);
        if (st.isEmpty()) return "Serial " + serial + " is not on file for this item. Receive it first.";
        return "Serial " + serial + " is " + st.get(0).toLowerCase(Locale.ROOT).replace('_', ' ') + ", not available to pick.";
    }

    private List<String> existing(UUID t, UUID clientId, UUID itemId, List<String> serials) {
        if (serials.isEmpty()) return List.of();
        String marks = String.join(",", Collections.nCopies(serials.size(), "?"));
        List<Object> args = new ArrayList<>(List.of(t, clientId, itemId));
        serials.forEach(s -> args.add(s.toUpperCase(Locale.ROOT)));
        return jdbc.query("SELECT serial_number FROM whse_serials WHERE tenant_id = ? AND client_id = ? AND item_id = ? AND upper(serial_number) IN (" + marks + ") ORDER BY serial_number",
                (rs, i) -> rs.getString(1), args.toArray());
    }

    private void event(UUID t, UUID serialId, String event, String reference, UUID actor) {
        jdbc.update("INSERT INTO whse_serial_events (id, tenant_id, serial_id, event, reference, actor_id) VALUES (?,?,?,?,?,?)", UUID.randomUUID(), t, serialId, event, reference, actor);
    }

    private boolean tracked(UUID t, UUID itemId) {
        List<Boolean> r = jdbc.query("SELECT serial_tracked FROM whse_items WHERE id = ? AND tenant_id = ? AND deleted_at IS NULL", (rs, i) -> rs.getBoolean(1), itemId, t);
        if (r.isEmpty()) throw new ResourceNotFoundException("Item", itemId.toString());
        return r.get(0);
    }

    private long count(String sql, Object... args) {
        Long n = jdbc.queryForObject(sql, Long.class, args);
        return n == null ? 0 : n;
    }
}
