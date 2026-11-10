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
import za.co.handyflow.platform.shared.SeparationOfDutiesChecker;
import za.co.handyflow.platform.shared.TenantId;
import za.co.handyflow.platform.warehousing.domain.model.WhseInboundShipment;
import za.co.handyflow.platform.warehousing.domain.model.WhseInboundShipmentLine;
import za.co.handyflow.platform.warehousing.domain.model.WhseInventory;
import za.co.handyflow.platform.warehousing.domain.repository.WhseInboundShipmentLineRepository;
import za.co.handyflow.platform.warehousing.domain.repository.WhseInboundShipmentRepository;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

/**
 * Receiving with inspection. A receipt splits what arrived into good, damaged and held-for-inspection stock, each posted in its own
 * stock status. Held stock is cleared by an inspection by a different person. A delivery with a shortfall, an overage, damage or an
 * item that was not on the advice is closed with a signed-off note.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class WhseReceivingService {

    public static final String QC_PERMISSION = "WAREHOUSING_QC_RELEASE";

    private final JdbcTemplate jdbc;
    private final WhseInboundShipmentRepository shipmentRepository;
    private final WhseInboundShipmentLineRepository lineRepository;
    private final WhseInventoryService inventoryService;
    private final SeparationOfDutiesChecker duties;
    private final WhseLotService lotService;

    public record ReceiptRow(UUID id, UUID lineId, UUID itemId, String sku, UUID locationId, String locationCode, BigDecimal qtyGood,
                             BigDecimal qtyDamaged, BigDecimal qtyHeld, BigDecimal heldPending, String reason, UUID receivedBy, Instant receivedAt,
                             String lotNumber, LocalDate expiryDate) {}
    public record LineDiscrepancy(UUID lineId, UUID itemId, String sku, String description, boolean unexpected, BigDecimal expected,
                                  BigDecimal received, BigDecimal good, BigDecimal damaged, BigDecimal heldPending,
                                  BigDecimal shortQty, BigDecimal overQty, List<String> types) {}
    public record DiscrepancyReport(UUID shipmentId, String status, boolean closed, String closeNote, boolean needsSignOff,
                                    List<LineDiscrepancy> lines) {}
    public record QueueRow(UUID receiptId, UUID shipmentId, String shipmentReference, UUID clientId, String clientName, UUID itemId, String sku,
                           String description, UUID locationId, String locationCode, BigDecimal heldPending, String reason, UUID receivedBy,
                           Instant receivedAt, String lotNumber, LocalDate expiryDate) {}

    // ── receive ─────────────────────────────────────────────────────────────

    @Transactional
    public UUID receive(TenantId tenantId, UUID shipmentId, UUID lineId, BigDecimal good, BigDecimal damaged, BigDecimal held,
                        UUID locationId, String reason, String lotNumber, LocalDate expiryDate, LocalDate manufacturedDate,
                        String idempotencyKey, UUID actor) {
        requireActor(actor);
        BigDecimal g = nz(good), d = nz(damaged), h = nz(held);
        if (g.signum() < 0 || d.signum() < 0 || h.signum() < 0) throw new IllegalArgumentException("Quantities cannot be negative.");
        BigDecimal total = g.add(d).add(h);
        if (total.signum() == 0) throw new IllegalArgumentException("Enter a quantity to receive.");
        if (d.add(h).signum() > 0 && (reason == null || reason.trim().length() < 5)) {
            throw new IllegalArgumentException("Say what is wrong with the damaged or held stock (at least 5 characters).");
        }
        if (locationId == null) throw new IllegalArgumentException("Every receipt must be put away somewhere.");
        UUID t = tenantId.getValue();

        String key = idempotencyKey == null || idempotencyKey.isBlank() ? null : idempotencyKey.trim();
        if (key != null) {
            List<UUID> existing = jdbc.query("SELECT id FROM whse_receipt_events WHERE tenant_id = ? AND idempotency_key = ?",
                    (rs, i) -> rs.getObject(1, UUID.class), t, key);
            if (!existing.isEmpty()) return existing.get(0);
        }

        WhseInboundShipment shipment = shipmentRepository.findByTenantAndId(t, shipmentId)
                .orElseThrow(() -> new ResourceNotFoundException("WhseInboundShipment", shipmentId.toString()));
        if ("CANCELLED".equals(shipment.getStatus()) || closed(t, shipmentId)) {
            throw conflict("This shipment is " + ("CANCELLED".equals(shipment.getStatus()) ? "cancelled" : "closed") + " and cannot be received against.", "SHIPMENT_FINISHED");
        }
        WhseInboundShipmentLine line = lineRepository.findByTenantAndId(t, lineId)
                .filter(l -> l.getShipmentId().equals(shipmentId))
                .orElseThrow(() -> new ResourceNotFoundException("WhseInboundShipmentLine", lineId.toString()));
        if (count("SELECT count(*) FROM whse_locations WHERE id = ? AND tenant_id = ? AND deleted_at IS NULL", locationId, t) == 0) {
            throw new ResourceNotFoundException("Location", locationId.toString());
        }

        // For a lot-tracked item this finds or creates the lot (and refuses a missing lot number or expiry date); otherwise it is null.
        UUID lotId = lotService.resolveForReceipt(tenantId, shipment.getClientId(), line.getItemId(), lotNumber, expiryDate, manufacturedDate, actor);

        UUID eventId = UUID.randomUUID();
        jdbc.update("INSERT INTO whse_receipt_events (id, tenant_id, shipment_id, line_id, client_id, item_id, location_id, qty_good, qty_damaged, qty_held, reason, received_by, idempotency_key, lot_id) VALUES (?,?,?,?,?,?,?,?,?,?,?,?,?,?)",
                eventId, t, shipmentId, lineId, shipment.getClientId(), line.getItemId(), locationId, g, d, h,
                d.add(h).signum() > 0 ? reason.trim() : null, actor, key, lotId);

        post(tenantId, shipment, line, locationId, lotId, g, WhseInventory.STATUS_AVAILABLE, eventId, "G", null, actor);
        post(tenantId, shipment, line, locationId, lotId, d, WhseInventory.STATUS_DAMAGED, eventId, "D", reason, actor);
        post(tenantId, shipment, line, locationId, lotId, h, WhseInventory.STATUS_QUARANTINE, eventId, "H", reason, actor);

        line.receive(total, locationId);
        lineRepository.save(line);
        rollStatus(tenantId, shipment);
        return eventId;
    }

    /** An item arrived that was not on the advice. It becomes a line with nothing expected, flagged as unexpected. */
    @Transactional
    public UUID addUnexpectedLine(TenantId tenantId, UUID shipmentId, UUID itemId, String note, UUID actor) {
        requireActor(actor);
        UUID t = tenantId.getValue();
        WhseInboundShipment shipment = shipmentRepository.findByTenantAndId(t, shipmentId)
                .orElseThrow(() -> new ResourceNotFoundException("WhseInboundShipment", shipmentId.toString()));
        if ("CANCELLED".equals(shipment.getStatus()) || closed(t, shipmentId)) throw conflict("This shipment is finished.", "SHIPMENT_FINISHED");
        if (count("SELECT count(*) FROM whse_items WHERE id = ? AND tenant_id = ? AND client_id = ? AND deleted_at IS NULL", itemId, t, shipment.getClientId()) == 0) {
            throw new ResourceNotFoundException("Item", itemId.toString());
        }
        if (count("SELECT count(*) FROM whse_inbound_shipment_lines WHERE shipment_id = ? AND item_id = ?", shipmentId, itemId) > 0) {
            throw conflict("That item is already on this shipment. Receive it against its line.", "ITEM_ALREADY_ON_SHIPMENT");
        }
        UUID id = UUID.randomUUID();
        jdbc.update("INSERT INTO whse_inbound_shipment_lines (id, tenant_id, shipment_id, item_id, expected_qty, received_qty, notes, unexpected) VALUES (?,?,?,?,0,0,?,TRUE)",
                id, t, shipmentId, itemId, note == null || note.isBlank() ? "Not on the advice" : note.trim());
        return id;
    }

    // ── inspect ─────────────────────────────────────────────────────────────

    /** Clears held stock: PASS releases it to available, FAIL moves it to damaged. Needs a person other than the receiver. */
    @Transactional
    public void inspect(TenantId tenantId, UUID receiptId, String outcome, BigDecimal qty, String reason, UUID actor) {
        requireActor(actor);
        if (!"PASS".equals(outcome) && !"FAIL".equals(outcome)) throw new IllegalArgumentException("The outcome must be PASS or FAIL.");
        if (qty == null || qty.signum() <= 0) throw new IllegalArgumentException("Enter the quantity inspected.");
        if (reason == null || reason.trim().length() < 5) throw new IllegalArgumentException("Give a reason (at least 5 characters).");
        UUID t = tenantId.getValue();

        record Ev(UUID shipmentId, UUID clientId, UUID itemId, UUID locationId, BigDecimal held, UUID receivedBy, String ref, UUID lotId) {}
        List<Ev> rows = jdbc.query("""
                SELECT e.shipment_id, e.client_id, e.item_id, e.location_id, e.qty_held, e.received_by, s.reference_number, e.lot_id
                  FROM whse_receipt_events e JOIN whse_inbound_shipments s ON s.id = e.shipment_id
                 WHERE e.id = ? AND e.tenant_id = ? FOR UPDATE OF e""",
                (rs, i) -> new Ev(rs.getObject(1, UUID.class), rs.getObject(2, UUID.class), rs.getObject(3, UUID.class), rs.getObject(4, UUID.class),
                        rs.getBigDecimal(5), rs.getObject(6, UUID.class), rs.getString(7), rs.getObject(8, UUID.class)), receiptId, t);
        if (rows.isEmpty()) throw new ResourceNotFoundException("Receipt", receiptId.toString());
        Ev ev = rows.get(0);
        BigDecimal inspected = jdbc.queryForObject("SELECT coalesce(sum(qty), 0) FROM whse_receipt_inspections WHERE receipt_id = ?", BigDecimal.class, receiptId);
        BigDecimal pending = ev.held().subtract(inspected == null ? BigDecimal.ZERO : inspected);
        if (pending.signum() <= 0) throw conflict("Nothing from this receipt is waiting for inspection.", "NOTHING_HELD");
        if (qty.compareTo(pending) > 0) throw new IllegalArgumentException("Only " + pending.stripTrailingZeros().toPlainString() + " is still waiting for inspection.");

        boolean sole = actor.equals(ev.receivedBy()) && duties.otherHolders(tenantId, QC_PERMISSION, actor) == 0;
        duties.require(tenantId, QC_PERMISSION, ev.receivedBy(), actor,
                "The person who received this stock cannot inspect it. Ask another warehouse administrator.",
                "WHSE_RECEIPT", receiptId, "inspected stock they received themselves");

        UUID inspectionId = UUID.randomUUID();
        String to = "PASS".equals(outcome) ? WhseInventory.STATUS_AVAILABLE : WhseInventory.STATUS_DAMAGED;
        inventoryService.changeStatus(tenantId, ev.clientId(), ev.itemId(), ev.locationId(), ev.lotId(), WhseInventory.STATUS_QUARANTINE, to, qty,
                "Receiving inspection " + outcome.toLowerCase() + ": " + reason.trim(), actor, "insp:" + inspectionId);
        jdbc.update("INSERT INTO whse_receipt_inspections (id, tenant_id, receipt_id, outcome, qty, reason, inspected_by, sole_exception) VALUES (?,?,?,?,?,?,?,?)",
                inspectionId, t, receiptId, outcome, qty, reason.trim(), actor, sole);
    }

    // ── close ───────────────────────────────────────────────────────────────

    /** Signs off a delivery. A note is required when it is short, over, damaged or carries an unexpected item. */
    @Transactional
    public void close(TenantId tenantId, UUID shipmentId, String note, UUID actor) {
        requireActor(actor);
        UUID t = tenantId.getValue();
        WhseInboundShipment shipment = shipmentRepository.findByTenantAndId(t, shipmentId)
                .orElseThrow(() -> new ResourceNotFoundException("WhseInboundShipment", shipmentId.toString()));
        if ("CANCELLED".equals(shipment.getStatus())) throw conflict("A cancelled shipment cannot be closed.", "SHIPMENT_FINISHED");
        if (closed(t, shipmentId)) throw conflict("This shipment is already closed.", "SHIPMENT_FINISHED");
        if (count("SELECT count(*) FROM whse_receipt_events WHERE shipment_id = ?", shipmentId) == 0
                && count("SELECT count(*) FROM whse_inbound_shipment_lines WHERE shipment_id = ? AND received_qty > 0", shipmentId) == 0) {
            throw new IllegalArgumentException("Nothing has been received yet. Cancel the shipment instead.");
        }
        List<LineDiscrepancy> lines = lineReport(t, shipmentId, true);
        boolean needs = lines.stream().anyMatch(l -> WhseReceivingRules.needsSignOff(l.types()));
        if (needs && (note == null || note.trim().length() < 5)) {
            throw new IllegalArgumentException("This delivery has discrepancies. Add a note explaining them (at least 5 characters).");
        }
        if (!shipment.isTerminal()) {
            shipment.markReceived();
            shipmentRepository.save(shipment);
        }
        jdbc.update("UPDATE whse_inbound_shipments SET closed_by = ?, closed_at = now(), close_note = ?, closed_with_discrepancy = ? WHERE id = ? AND tenant_id = ?",
                actor, note == null || note.isBlank() ? null : note.trim(), needs, shipmentId, t);
        String name = null;
        try { name = za.co.handyflow.platform.shared.TenantContext.getCurrentUserName(); } catch (RuntimeException ignored) { /* no named user */ }
        jdbc.update("INSERT INTO audit_events (tenant_id, actor_type, actor_id, actor_name, action, item_type, item_id, after_json) VALUES (?, 'STAFF', ?, ?, ?, ?, ?, ?::jsonb)",
                t, actor, name, "WHSE_INBOUND_CLOSED", "WHSE_INBOUND_SHIPMENT", shipmentId, "{\"withDiscrepancy\":" + needs + "}");
    }

    // ── read ────────────────────────────────────────────────────────────────

    @Transactional(readOnly = true)
    public List<ReceiptRow> receipts(TenantId tenantId, UUID shipmentId) {
        return jdbc.query(SQL_RECEIPTS + " WHERE e.tenant_id = ? AND e.shipment_id = ? ORDER BY e.received_at",
                (rs, i) -> new ReceiptRow(rs.getObject("id", UUID.class), rs.getObject("line_id", UUID.class), rs.getObject("item_id", UUID.class), rs.getString("sku"),
                        rs.getObject("location_id", UUID.class), rs.getString("location_code"), rs.getBigDecimal("qty_good"), rs.getBigDecimal("qty_damaged"),
                        rs.getBigDecimal("qty_held"), rs.getBigDecimal("held_pending"), rs.getString("reason"), rs.getObject("received_by", UUID.class),
                        rs.getTimestamp("received_at").toInstant(), rs.getString("lot_number"),
                        rs.getDate("expiry_date") == null ? null : rs.getDate("expiry_date").toLocalDate()),
                tenantId.getValue(), shipmentId);
    }

    @Transactional(readOnly = true)
    public DiscrepancyReport discrepancies(TenantId tenantId, UUID shipmentId) {
        UUID t = tenantId.getValue();
        record H(String status, boolean closed, String note) {}
        List<H> h = jdbc.query("SELECT status, closed_at IS NOT NULL AS closed, close_note FROM whse_inbound_shipments WHERE id = ? AND tenant_id = ?",
                (rs, i) -> new H(rs.getString(1), rs.getBoolean(2), rs.getString(3)), shipmentId, t);
        if (h.isEmpty()) throw new ResourceNotFoundException("WhseInboundShipment", shipmentId.toString());
        boolean finalised = h.get(0).closed() || "RECEIVED".equals(h.get(0).status());
        List<LineDiscrepancy> lines = lineReport(t, shipmentId, finalised);
        boolean needs = lines.stream().anyMatch(l -> WhseReceivingRules.needsSignOff(l.types())) && !h.get(0).closed();
        return new DiscrepancyReport(shipmentId, h.get(0).status(), h.get(0).closed(), h.get(0).note(), needs, lines);
    }

    /** Held stock waiting for someone to pass or fail it, oldest first. */
    @Transactional(readOnly = true)
    public List<QueueRow> inspectionQueue(TenantId tenantId) {
        return jdbc.query("""
                SELECT * FROM (
                  SELECT e.id AS receipt_id, e.shipment_id, s.reference_number, e.client_id, c.trading_name AS client_name, e.item_id, it.sku, it.description,
                         e.location_id, loc.code AS location_code, e.reason, e.received_by, e.received_at, lot.lot_number, lot.expiry_date,
                         e.qty_held - coalesce((SELECT sum(i.qty) FROM whse_receipt_inspections i WHERE i.receipt_id = e.id), 0) AS pending
                    FROM whse_receipt_events e
                    JOIN whse_inbound_shipments s ON s.id = e.shipment_id
                    JOIN whse_clients c ON c.id = e.client_id
                    JOIN whse_items it ON it.id = e.item_id
                    JOIN whse_locations loc ON loc.id = e.location_id
                    LEFT JOIN whse_lots lot ON lot.id = e.lot_id
                   WHERE e.tenant_id = ? AND e.qty_held > 0) q
                 WHERE pending > 0 ORDER BY received_at LIMIT 500""",
                (rs, i) -> new QueueRow(rs.getObject("receipt_id", UUID.class), rs.getObject("shipment_id", UUID.class), rs.getString("reference_number"),
                        rs.getObject("client_id", UUID.class), rs.getString("client_name"), rs.getObject("item_id", UUID.class), rs.getString("sku"),
                        rs.getString("description"), rs.getObject("location_id", UUID.class), rs.getString("location_code"), rs.getBigDecimal("pending"),
                        rs.getString("reason"), rs.getObject("received_by", UUID.class), rs.getTimestamp("received_at").toInstant(),
                        rs.getString("lot_number"), rs.getDate("expiry_date") == null ? null : rs.getDate("expiry_date").toLocalDate()),
                tenantId.getValue());
    }

    // ── internals ───────────────────────────────────────────────────────────

    private List<LineDiscrepancy> lineReport(UUID tenant, UUID shipmentId, boolean finalised) {
        return jdbc.query(SQL_LINE_REPORT, (rs, i) -> {
            BigDecimal expected = rs.getBigDecimal("expected_qty"), received = rs.getBigDecimal("received_qty");
            BigDecimal good = rs.getBigDecimal("good"), dmg = rs.getBigDecimal("damaged"), held = rs.getBigDecimal("held");
            BigDecimal failed = rs.getBigDecimal("failed"), inspected = rs.getBigDecimal("inspected");
            boolean unexpected = rs.getBoolean("unexpected");
            // Receipts made before inspection existed carry no event; the remainder of the line is good stock.
            BigDecimal tracked = good.add(dmg).add(held);
            BigDecimal legacy = received.subtract(tracked);
            BigDecimal goodTotal = legacy.signum() > 0 ? good.add(legacy) : good;
            BigDecimal damagedTotal = dmg.add(failed);
            BigDecimal heldPending = held.subtract(inspected);
            // held stock that passed counts as good
            goodTotal = goodTotal.add(inspected.subtract(failed));
            List<String> types = WhseReceivingRules.classify(expected, received, damagedTotal, heldPending, unexpected, finalised);
            return new LineDiscrepancy(rs.getObject("line_id", UUID.class), rs.getObject("item_id", UUID.class), rs.getString("sku"), rs.getString("description"),
                    unexpected, expected, received, goodTotal, damagedTotal, heldPending,
                    finalised ? WhseReceivingRules.shortQty(expected, received) : BigDecimal.ZERO, WhseReceivingRules.overQty(expected, received), types);
        }, shipmentId, tenant);
    }

    private void post(TenantId tenantId, WhseInboundShipment shipment, WhseInboundShipmentLine line, UUID locationId, UUID lotId, BigDecimal qty,
                      String status, UUID eventId, String suffix, String note, UUID actor) {
        if (qty.signum() == 0) return;
        inventoryService.receiveStockInStatus(tenantId, shipment.getClientId(), line.getItemId(), locationId, lotId, qty, status, "RECEIPT",
                "INBOUND_SHIPMENT", shipment.getId(), shipment.getReferenceNumber(), note == null ? null : note.trim(), actor, "rcpt:" + eventId + ":" + suffix);
    }

    private void rollStatus(TenantId tenantId, WhseInboundShipment shipment) {
        if (shipment.isTerminal()) return;
        List<WhseInboundShipmentLine> lines = lineRepository.findByShipment(tenantId.getValue(), shipment.getId());
        boolean all = !lines.isEmpty() && lines.stream().allMatch(WhseInboundShipmentLine::isFullyReceived);
        if (all) shipment.markReceived(); else shipment.markPartiallyReceived();
        shipmentRepository.save(shipment);
    }

    private boolean closed(UUID tenant, UUID shipmentId) {
        return count("SELECT count(*) FROM whse_inbound_shipments WHERE id = ? AND tenant_id = ? AND closed_at IS NOT NULL", shipmentId, tenant) > 0;
    }

    private long count(String sql, Object... args) { Long n = jdbc.queryForObject(sql, Long.class, args); return n == null ? 0 : n; }
    private static BigDecimal nz(BigDecimal v) { return v == null ? BigDecimal.ZERO : v; }
    private static void requireActor(UUID actor) {
        if (actor == null) throw new AccessDeniedException("Your user could not be identified, so this step cannot be recorded.");
    }
    private static HandyFlowException conflict(String message, String code) { return new HandyFlowException(message, HttpStatus.CONFLICT, code); }

    static final String SQL_RECEIPTS = """
            SELECT e.id, e.line_id, e.item_id, it.sku, e.location_id, loc.code AS location_code, e.qty_good, e.qty_damaged, e.qty_held,
                   e.qty_held - coalesce((SELECT sum(i.qty) FROM whse_receipt_inspections i WHERE i.receipt_id = e.id), 0) AS held_pending,
                   e.reason, e.received_by, e.received_at, lot.lot_number, lot.expiry_date
              FROM whse_receipt_events e
              JOIN whse_items it ON it.id = e.item_id
              JOIN whse_locations loc ON loc.id = e.location_id
              LEFT JOIN whse_lots lot ON lot.id = e.lot_id""";

    static final String SQL_LINE_REPORT = """
            SELECT l.id AS line_id, l.item_id, it.sku, it.description, l.unexpected, l.expected_qty, l.received_qty,
                   coalesce(sum(e.qty_good), 0) AS good, coalesce(sum(e.qty_damaged), 0) AS damaged, coalesce(sum(e.qty_held), 0) AS held,
                   coalesce((SELECT sum(i.qty) FROM whse_receipt_inspections i JOIN whse_receipt_events e2 ON e2.id = i.receipt_id WHERE e2.line_id = l.id), 0) AS inspected,
                   coalesce((SELECT sum(i.qty) FROM whse_receipt_inspections i JOIN whse_receipt_events e2 ON e2.id = i.receipt_id WHERE e2.line_id = l.id AND i.outcome = 'FAIL'), 0) AS failed
              FROM whse_inbound_shipment_lines l
              JOIN whse_items it ON it.id = l.item_id
              LEFT JOIN whse_receipt_events e ON e.line_id = l.id
             WHERE l.shipment_id = ? AND l.tenant_id = ?
             GROUP BY l.id, it.sku, it.description
             ORDER BY it.sku""";
}
