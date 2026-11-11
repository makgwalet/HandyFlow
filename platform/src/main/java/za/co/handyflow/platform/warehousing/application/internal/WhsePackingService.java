package za.co.handyflow.platform.warehousing.application.internal;

import lombok.RequiredArgsConstructor;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import za.co.handyflow.platform.shared.HandyFlowException;
import za.co.handyflow.platform.shared.ResourceNotFoundException;
import za.co.handyflow.platform.shared.TenantId;
import org.springframework.http.HttpStatus;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * Packing: an order's units are put into numbered packages (box, pallet, ...), each closed with a weighed gross weight and a printable label.
 * Packing is optional. An order with no packages is marked packed as before; once it has one, everything that will ship must be in a closed
 * package ({@link #requireReadyToPack}). Packages can only be changed while the order is being picked or is packed (not yet shipped).
 */
@Service
@RequiredArgsConstructor
public class WhsePackingService {

    private final JdbcTemplate jdbc;

    public record PackedItem(UUID lineId, String sku, String description, BigDecimal qty) {}
    public record PackageRow(UUID id, int packageNo, String type, Integer lengthMm, Integer widthMm, Integer heightMm, BigDecimal tareKg, BigDecimal grossKg,
                             String status, Instant labelPrintedAt, String weightNote, List<PackedItem> items) {}
    public record LineProgress(UUID lineId, UUID itemId, String sku, String description, BigDecimal toShip, BigDecimal packed, BigDecimal remaining) {}
    public record Summary(UUID orderId, String orderReference, String orderStatus, List<PackageRow> packages, List<LineProgress> lines,
                          BigDecimal totalGrossKg, BigDecimal unpackedUnits, List<String> blockers) {}
    public record LabelData(UUID packageId, int packageNo, int packageCount, String orderReference, String shipToName, String shipToAddress, String clientName,
                            String type, BigDecimal grossKg, Integer lengthMm, Integer widthMm, Integer heightMm, List<PackedItem> items) {}

    // ── reading ─────────────────────────────────────────────────────────────

    @Transactional(readOnly = true)
    public Summary summary(TenantId tenantId, UUID orderId) {
        UUID t = tenantId.getValue();
        List<String[]> head = jdbc.query("SELECT order_reference, status FROM whse_outbound_orders WHERE tenant_id = ? AND id = ?",
                (rs, n) -> new String[]{rs.getString(1), rs.getString(2)}, t, orderId);
        if (head.isEmpty()) throw new ResourceNotFoundException("WhseOutboundOrder", orderId.toString());
        List<LineProgress> lines = lines(t, orderId);
        List<PackageRow> packages = packages(t, orderId);
        BigDecimal total = packages.stream().filter(p -> p.grossKg() != null).map(PackageRow::grossKg).reduce(BigDecimal.ZERO, BigDecimal::add);
        BigDecimal unpacked = lines.stream().map(LineProgress::remaining).reduce(BigDecimal.ZERO, BigDecimal::add);
        List<String> blockers = WhsePackingRules.notReady(packages.size(), (int) packages.stream().filter(p -> "OPEN".equals(p.status())).count(), states(lines));
        return new Summary(orderId, head.get(0)[0], head.get(0)[1], packages, lines, total, unpacked, blockers);
    }

    private List<LineProgress> lines(UUID t, UUID orderId) {
        return jdbc.query("""
                SELECT ol.id, ol.item_id, i.sku, i.description, ol.qty_ordered - ol.qty_short,
                       coalesce((SELECT sum(pi.qty) FROM whse_package_items pi WHERE pi.order_line_id = ol.id), 0)
                  FROM whse_outbound_order_lines ol JOIN whse_items i ON i.id = ol.item_id
                 WHERE ol.tenant_id = ? AND ol.order_id = ? AND ol.qty_ordered > ol.qty_short ORDER BY i.sku, ol.id""",
                (rs, n) -> {
                    BigDecimal toShip = rs.getBigDecimal(5), packed = rs.getBigDecimal(6);
                    return new LineProgress(rs.getObject(1, UUID.class), rs.getObject(2, UUID.class), rs.getString(3), rs.getString(4), toShip, packed,
                            toShip.subtract(packed).max(BigDecimal.ZERO));
                }, t, orderId);
    }

    private static List<WhsePackingRules.LineState> states(List<LineProgress> lines) {
        return lines.stream().map(l -> new WhsePackingRules.LineState(l.sku(), l.toShip(), l.packed())).toList();
    }

    private List<PackageRow> packages(UUID t, UUID orderId) {
        Map<UUID, List<PackedItem>> items = new LinkedHashMap<>();
        jdbc.query("""
                SELECT pi.package_id, pi.order_line_id, i.sku, i.description, pi.qty
                  FROM whse_package_items pi JOIN whse_packages p ON p.id = pi.package_id
                  JOIN whse_outbound_order_lines ol ON ol.id = pi.order_line_id JOIN whse_items i ON i.id = ol.item_id
                 WHERE p.tenant_id = ? AND p.order_id = ? ORDER BY i.sku""",
                rs -> {
                    items.computeIfAbsent(rs.getObject(1, UUID.class), k -> new ArrayList<>())
                            .add(new PackedItem(rs.getObject(2, UUID.class), rs.getString(3), rs.getString(4), rs.getBigDecimal(5)));
                }, t, orderId);
        // Expected net weight per package from the item master (unit weight x quantity), when the weights are known.
        Map<UUID, BigDecimal> expected = new java.util.HashMap<>();
        jdbc.query("""
                SELECT pi.package_id, sum(pi.qty * i.weight_kg)
                  FROM whse_package_items pi JOIN whse_packages p ON p.id = pi.package_id
                  JOIN whse_outbound_order_lines ol ON ol.id = pi.order_line_id JOIN whse_items i ON i.id = ol.item_id
                 WHERE p.tenant_id = ? AND p.order_id = ? AND i.weight_kg IS NOT NULL GROUP BY pi.package_id""",
                rs -> { expected.put(rs.getObject(1, UUID.class), rs.getBigDecimal(2)); }, t, orderId);
        return jdbc.query("""
                SELECT id, package_no, package_type, length_mm, width_mm, height_mm, tare_kg, gross_kg, status, label_printed_at
                  FROM whse_packages WHERE tenant_id = ? AND order_id = ? ORDER BY package_no""",
                (rs, n) -> {
                    UUID id = rs.getObject(1, UUID.class);
                    BigDecimal tare = rs.getBigDecimal(7), gross = rs.getBigDecimal(8);
                    java.sql.Timestamp printed = rs.getTimestamp(10);
                    return new PackageRow(id, rs.getInt(2), rs.getString(3), (Integer) rs.getObject(4), (Integer) rs.getObject(5), (Integer) rs.getObject(6), tare, gross,
                            rs.getString(9), printed == null ? null : printed.toInstant(), WhsePackingRules.weightNote(gross, tare, expected.get(id)),
                            items.getOrDefault(id, List.of()));
                }, t, orderId);
    }

    // ── changing packages ───────────────────────────────────────────────────

    @Transactional
    public UUID createPackage(TenantId tenantId, UUID orderId, String type, Integer lengthMm, Integer widthMm, Integer heightMm, BigDecimal tareKg, UUID actor) {
        requireActor(actor);
        UUID t = tenantId.getValue();
        lockOrder(t, orderId);
        BigDecimal tare = tareKg == null ? BigDecimal.ZERO : tareKg;
        if (tare.signum() < 0 || tare.compareTo(new BigDecimal("100000")) > 0) throw new IllegalArgumentException("The empty weight must be between 0 and 100000 kg.");
        Integer next = jdbc.queryForObject("SELECT coalesce(max(package_no), 0) + 1 FROM whse_packages WHERE order_id = ?", Integer.class, orderId);
        UUID id = UUID.randomUUID();
        jdbc.update("INSERT INTO whse_packages (id, tenant_id, order_id, package_no, package_type, length_mm, width_mm, height_mm, tare_kg, created_by) VALUES (?,?,?,?,?,?,?,?,?,?)",
                id, t, orderId, next, WhsePackingRules.type(type), WhsePackingRules.dimension(lengthMm, "Length"), WhsePackingRules.dimension(widthMm, "Width"),
                WhsePackingRules.dimension(heightMm, "Height"), tare, actor);
        audit(t, actor, "WHSE_PACKAGE_CREATED", "{\"order\":\"" + orderId + "\",\"package\":" + next + "}");
        return id;
    }

    /** Sets how much of a line is in this package (replacing what it had). Zero takes the line out. */
    @Transactional
    public void setItem(TenantId tenantId, UUID packageId, UUID lineId, BigDecimal qty, UUID actor) {
        requireActor(actor);
        UUID t = tenantId.getValue();
        Pkg p = lockPackage(t, packageId);
        requireOpen(p);
        if (qty != null && qty.signum() == 0) {
            jdbc.update("DELETE FROM whse_package_items WHERE package_id = ? AND order_line_id = ?", packageId, lineId);
            return;
        }
        LineProgress line = lines(t, p.orderId).stream().filter(l -> l.lineId().equals(lineId)).findFirst()
                .orElseThrow(() -> new ResourceNotFoundException("WhseOutboundOrderLine", lineId.toString()));
        List<BigDecimal> here = jdbc.query("SELECT qty FROM whse_package_items WHERE package_id = ? AND order_line_id = ?", (rs, n) -> rs.getBigDecimal(1), packageId, lineId);
        WhsePackingRules.checkAdd(new WhsePackingRules.LineState(line.sku(), line.toShip(), line.packed()), here.isEmpty() ? BigDecimal.ZERO : here.get(0), qty);
        if (here.isEmpty()) {
            jdbc.update("INSERT INTO whse_package_items (id, tenant_id, package_id, order_line_id, qty) VALUES (?,?,?,?,?)", UUID.randomUUID(), t, packageId, lineId, qty);
        } else {
            jdbc.update("UPDATE whse_package_items SET qty = ? WHERE package_id = ? AND order_line_id = ?", qty, packageId, lineId);
        }
    }

    @Transactional
    public void closePackage(TenantId tenantId, UUID packageId, BigDecimal grossKg, UUID actor) {
        requireActor(actor);
        UUID t = tenantId.getValue();
        Pkg p = lockPackage(t, packageId);
        requireOpen(p);
        Integer items = jdbc.queryForObject("SELECT count(*) FROM whse_package_items WHERE package_id = ?", Integer.class, packageId);
        WhsePackingRules.checkClose(items == null ? 0 : items, grossKg, p.tareKg());
        jdbc.update("UPDATE whse_packages SET status = 'CLOSED', gross_kg = ?, closed_at = now() WHERE id = ?", grossKg, packageId);
        audit(t, actor, "WHSE_PACKAGE_CLOSED", "{\"order\":\"" + p.orderId() + "\",\"package\":" + p.packageNo() + ",\"grossKg\":" + grossKg.toPlainString() + "}");
    }

    @Transactional
    public void reopenPackage(TenantId tenantId, UUID packageId, UUID actor) {
        requireActor(actor);
        UUID t = tenantId.getValue();
        Pkg p = lockPackage(t, packageId);
        if (!"CLOSED".equals(p.status())) return;
        String orderStatus = jdbc.queryForObject("SELECT status FROM whse_outbound_orders WHERE id = ?", String.class, p.orderId());
        if (!"PICKING".equals(orderStatus)) throw conflict("PACKED_LOCKED", "The order is " + orderStatus.toLowerCase() + ". Packing can only be reopened while it is being picked.");
        jdbc.update("UPDATE whse_packages SET status = 'OPEN', gross_kg = NULL, closed_at = NULL, label_printed_at = NULL WHERE id = ?", packageId);
        audit(t, actor, "WHSE_PACKAGE_REOPENED", "{\"order\":\"" + p.orderId() + "\",\"package\":" + p.packageNo() + "}");
    }

    @Transactional
    public void deletePackage(TenantId tenantId, UUID packageId, UUID actor) {
        requireActor(actor);
        UUID t = tenantId.getValue();
        Pkg p = lockPackage(t, packageId);
        requireOpen(p);
        jdbc.update("DELETE FROM whse_packages WHERE id = ?", packageId);
        audit(t, actor, "WHSE_PACKAGE_DELETED", "{\"order\":\"" + p.orderId() + "\",\"package\":" + p.packageNo() + "}");
    }

    /** Puts everything not yet packed into one new package, for the common single-box order. The package is left open to be weighed. */
    @Transactional
    public UUID packRemaining(TenantId tenantId, UUID orderId, String type, UUID actor) {
        UUID t = tenantId.getValue();
        List<LineProgress> remaining = lines(t, orderId).stream().filter(l -> l.remaining().signum() > 0).toList();
        if (remaining.isEmpty()) throw new IllegalStateException("Everything is already in a package.");
        UUID id = createPackage(tenantId, orderId, type, null, null, null, null, actor);
        for (LineProgress l : remaining) {
            jdbc.update("INSERT INTO whse_package_items (id, tenant_id, package_id, order_line_id, qty) VALUES (?,?,?,?,?)", UUID.randomUUID(), t, id, l.lineId(), l.remaining());
        }
        return id;
    }

    /** Called when the order is marked packed. Does nothing for an order without packages. */
    @Transactional(readOnly = true)
    public void requireReadyToPack(TenantId tenantId, UUID orderId) {
        Summary s = summary(tenantId, orderId);
        if (!s.blockers().isEmpty()) throw new IllegalStateException("Packing is not finished: " + String.join(" ", s.blockers()));
    }

    // ── label ───────────────────────────────────────────────────────────────

    @Transactional
    public LabelData label(TenantId tenantId, UUID packageId) {
        UUID t = tenantId.getValue();
        Pkg p = lockPackage(t, packageId);
        if (!"CLOSED".equals(p.status())) throw conflict("PACKAGE_OPEN", "Close the package (weigh it) before printing its label.");
        Summary s = summary(tenantId, p.orderId());
        PackageRow row = s.packages().stream().filter(r -> r.id().equals(packageId)).findFirst().orElseThrow();
        List<String[]> ship = jdbc.query("""
                SELECT o.ship_to_name, o.ship_to_address, c.trading_name FROM whse_outbound_orders o JOIN whse_clients c ON c.id = o.client_id WHERE o.id = ?""",
                (rs, n) -> new String[]{rs.getString(1), rs.getString(2), rs.getString(3)}, p.orderId());
        String[] sh = ship.isEmpty() ? new String[]{null, null, null} : ship.get(0);
        jdbc.update("UPDATE whse_packages SET label_printed_at = now() WHERE id = ?", packageId);
        return new LabelData(packageId, row.packageNo(), s.packages().size(), s.orderReference(), sh[0], sh[1], sh[2], row.type(), row.grossKg(),
                row.lengthMm(), row.widthMm(), row.heightMm(), row.items());
    }

    // ── helpers ─────────────────────────────────────────────────────────────

    private record Pkg(UUID orderId, int packageNo, String status, BigDecimal tareKg) {}

    private Pkg lockPackage(UUID t, UUID packageId) {
        List<Object[]> r = jdbc.query("SELECT order_id, package_no, status, tare_kg FROM whse_packages WHERE tenant_id = ? AND id = ? FOR UPDATE",
                (rs, n) -> new Object[]{rs.getObject(1, UUID.class), rs.getInt(2), rs.getString(3), rs.getBigDecimal(4)}, t, packageId);
        if (r.isEmpty()) throw new ResourceNotFoundException("WhsePackage", packageId.toString());
        Object[] k = r.get(0);
        return new Pkg((UUID) k[0], (Integer) k[1], (String) k[2], (BigDecimal) k[3]);
    }

    private void requireOpen(Pkg p) {
        if (!"OPEN".equals(p.status())) throw conflict("PACKAGE_CLOSED", "Package " + p.packageNo() + " is closed. Reopen it to change it.");
    }

    private void lockOrder(UUID t, UUID orderId) {
        List<String> st = jdbc.query("SELECT status FROM whse_outbound_orders WHERE tenant_id = ? AND id = ? FOR UPDATE", (rs, n) -> rs.getString(1), t, orderId);
        if (st.isEmpty()) throw new ResourceNotFoundException("WhseOutboundOrder", orderId.toString());
        if (!"PICKING".equals(st.get(0))) throw conflict("ORDER_NOT_PICKING", "Packages can only be made while the order is being picked (this one is " + st.get(0).toLowerCase() + ").");
    }

    private static HandyFlowException conflict(String code, String msg) { return new HandyFlowException(msg, HttpStatus.CONFLICT, code); }

    private void audit(UUID t, UUID actor, String action, String json) {
        jdbc.update("INSERT INTO audit_events (tenant_id, actor_type, actor_id, action, item_type, after_json) VALUES (?, 'STAFF', ?, ?, 'WHSE_PACKAGE', ?::jsonb)", t, actor, action, json);
    }

    private static void requireActor(UUID actor) {
        if (actor == null) throw new AccessDeniedException("Your user could not be identified, so this step cannot be recorded.");
    }
}
