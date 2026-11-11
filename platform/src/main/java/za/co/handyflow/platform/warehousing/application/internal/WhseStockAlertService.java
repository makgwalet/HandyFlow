package za.co.handyflow.platform.warehousing.application.internal;

import lombok.RequiredArgsConstructor;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import za.co.handyflow.platform.shared.TenantId;
import za.co.handyflow.platform.warehousing.application.internal.WhseStockAlertRules.Figures;
import za.co.handyflow.platform.warehousing.application.internal.WhseStockAlertRules.Finding;
import za.co.handyflow.platform.warehousing.application.internal.WhseStockAlertRules.Kind;
import za.co.handyflow.platform.warehousing.application.internal.WhseStockAlertRules.Levels;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * Stock alerts per warehouse. What is wrong is worked out live from the stock ({@link #conditions}); {@link #list} first brings the saved alerts in line with it,
 * so an alert has a life: raised, acknowledged, resolved by the system when the stock recovers or by a person with a note.
 */
@Service
@RequiredArgsConstructor
public class WhseStockAlertService {

    private final JdbcTemplate jdbc;

    public record Condition(UUID warehouseId, String warehouseCode, UUID clientId, String clientName, UUID itemId, String sku, String description, Kind kind,
                            Figures figures, Levels levels, BigDecimal recommendedOrder) {}

    public record Alert(UUID id, UUID warehouseId, String warehouseCode, UUID clientId, String clientName, UUID itemId, String sku, String description, String kind,
                        String status, java.time.Instant firstSeen, java.time.Instant lastSeen, BigDecimal physical, BigDecimal unavailable, BigDecimal reserved,
                        BigDecimal available, BigDecimal incoming, BigDecimal minQty, String minSource, BigDecimal maxQty, String maxSource, BigDecimal recommendedOrder,
                        java.time.Instant acknowledgedAt, String resolution, String resolutionNote) {}

    public record Threshold(UUID warehouseId, String warehouseCode, UUID itemId, String sku, BigDecimal minQty, BigDecimal maxQty, BigDecimal itemMinQty, BigDecimal itemMaxQty) {}

    /**
     * One row per item and active warehouse that has a threshold from either source. With several warehouses an item's own defaults are checked only where
     * the item is held (or the warehouse has its own threshold for it), so a warehouse is not alerted about items it never stocks; with a single warehouse
     * every item with a threshold is checked.
     */
    static final String SQL_CONDITIONS = """
            WITH wh AS (SELECT id, code FROM whse_warehouses WHERE tenant_id = ? AND active AND deleted_at IS NULL AND (?::uuid IS NULL OR id = ?::uuid)),
                 cnt AS (SELECT count(*) AS n FROM whse_warehouses WHERE tenant_id = ? AND active AND deleted_at IS NULL),
                 pos AS (SELECT l.warehouse_id, inv.item_id,
                                sum(inv.qty_on_hand) AS on_hand,
                                sum(inv.qty_on_hand) FILTER (WHERE inv.status = 'AVAILABLE') AS sellable,
                                sum(inv.qty_allocated) FILTER (WHERE inv.status = 'AVAILABLE') AS allocated
                           FROM whse_inventory inv JOIN whse_locations l ON l.id = inv.location_id AND l.tenant_id = inv.tenant_id
                          WHERE inv.tenant_id = ? GROUP BY l.warehouse_id, inv.item_id),
                 inc AS (SELECT s.warehouse_id, sl.item_id, sum(greatest(sl.expected_qty - sl.received_qty, 0)) AS incoming
                           FROM whse_inbound_shipment_lines sl JOIN whse_inbound_shipments s ON s.id = sl.shipment_id AND s.tenant_id = sl.tenant_id
                          WHERE s.tenant_id = ? AND s.status IN ('EXPECTED', 'PARTIALLY_RECEIVED') GROUP BY s.warehouse_id, sl.item_id)
            SELECT wh.id AS warehouse_id, wh.code AS warehouse_code, it.id AS item_id, it.sku, it.description, it.client_id, c.trading_name AS client_name,
                   it.min_qty AS item_min, it.max_qty AS item_max, t.min_qty AS wh_min, t.max_qty AS wh_max,
                   pos.on_hand, pos.sellable, pos.allocated, inc.incoming
              FROM wh CROSS JOIN whse_items it
              JOIN whse_clients c ON c.id = it.client_id AND c.tenant_id = it.tenant_id
              LEFT JOIN whse_stock_thresholds t ON t.tenant_id = it.tenant_id AND t.warehouse_id = wh.id AND t.item_id = it.id
              LEFT JOIN pos ON pos.warehouse_id = wh.id AND pos.item_id = it.id
              LEFT JOIN inc ON inc.warehouse_id = wh.id AND inc.item_id = it.id
             WHERE it.tenant_id = ? AND it.deleted_at IS NULL AND it.active
               AND (it.min_qty IS NOT NULL OR it.max_qty IS NOT NULL OR t.id IS NOT NULL)
               AND (?::uuid IS NULL OR it.client_id = ?::uuid)
               AND ((SELECT n FROM cnt) = 1 OR pos.item_id IS NOT NULL OR t.id IS NOT NULL)
             ORDER BY wh.code, it.sku""";

    @Transactional(readOnly = true)
    public List<Condition> conditions(TenantId tenantId, UUID warehouseId, UUID clientId) {
        UUID t = tenantId.getValue();
        List<Condition> out = new ArrayList<>();
        jdbc.query(SQL_CONDITIONS, rs -> {
            Levels levels = WhseStockAlertRules.resolve(rs.getBigDecimal("wh_min"), rs.getBigDecimal("wh_max"), rs.getBigDecimal("item_min"), rs.getBigDecimal("item_max"));
            Figures f = Figures.of(rs.getBigDecimal("on_hand"), rs.getBigDecimal("sellable"), rs.getBigDecimal("allocated"), rs.getBigDecimal("incoming"));
            java.util.Optional<Finding> found = WhseStockAlertRules.classify(levels, f);
            if (found.isPresent()) {
                out.add(new Condition((UUID) rs.getObject("warehouse_id"), rs.getString("warehouse_code"), (UUID) rs.getObject("client_id"), rs.getString("client_name"),
                        (UUID) rs.getObject("item_id"), rs.getString("sku"), rs.getString("description"), found.get().kind(), f, levels, found.get().recommendedOrder()));
            }
        }, t, warehouseId, warehouseId, t, t, t, t, clientId, clientId);
        return out;
    }

    /**
     * Brings the saved alerts in line with the stock: a condition with no live alert raises one (unless a person resolved it by hand and it has not cleared since),
     * a live alert whose kind changed is closed and replaced, and a live alert whose condition is gone resolves itself. Returns how many alerts were raised.
     */
    @Transactional
    public int sync(TenantId tenantId) {
        UUID t = tenantId.getValue();
        List<Condition> live = conditions(tenantId, null, null);
        Map<String, Condition> byKey = new HashMap<>();
        for (Condition c : live) byKey.put(c.warehouseId() + "|" + c.itemId(), c);

        record Saved(UUID id, UUID warehouseId, UUID itemId, String kind, String status, boolean suppressed) {}
        List<Saved> saved = jdbc.query("SELECT id, warehouse_id, item_id, kind, status, suppressed FROM whse_stock_alerts WHERE tenant_id = ? AND (status <> 'RESOLVED' OR suppressed)",
                (rs, i) -> new Saved((UUID) rs.getObject(1), (UUID) rs.getObject(2), (UUID) rs.getObject(3), rs.getString(4), rs.getString(5), rs.getBoolean(6)), t);

        java.util.Set<String> handled = new java.util.HashSet<>();
        for (Saved s : saved) {
            String key = s.warehouseId() + "|" + s.itemId();
            Condition c = byKey.get(key);
            boolean live1 = !"RESOLVED".equals(s.status());
            if (!live1) { // a hand-resolved alert: it stops suppressing once its condition has cleared or changed
                if (c == null || !c.kind().name().equals(s.kind())) jdbc.update("UPDATE whse_stock_alerts SET suppressed = FALSE WHERE id = ?", s.id());
                else handled.add(key);
                continue;
            }
            if (c != null && c.kind().name().equals(s.kind())) {
                jdbc.update("UPDATE whse_stock_alerts SET last_seen = now(), available = ?, min_qty = ?, max_qty = ?, recommended_order = ? WHERE id = ?",
                        c.figures().available(), c.levels().min(), c.levels().max(), c.recommendedOrder(), s.id());
                handled.add(key);
            } else {
                jdbc.update("UPDATE whse_stock_alerts SET status = 'RESOLVED', resolved_at = now(), resolution = ? WHERE id = ?", c == null ? "CLEARED" : "CHANGED", s.id());
            }
        }
        int raised = 0;
        for (Condition c : live) {
            if (handled.contains(c.warehouseId() + "|" + c.itemId())) continue;
            raised += jdbc.update("INSERT INTO whse_stock_alerts (id, tenant_id, warehouse_id, item_id, client_id, kind, available, min_qty, max_qty, recommended_order) VALUES (?,?,?,?,?,?,?,?,?,?)",
                    UUID.randomUUID(), t, c.warehouseId(), c.itemId(), c.clientId(), c.kind().name(), c.figures().available(), c.levels().min(), c.levels().max(), c.recommendedOrder());
        }
        return raised;
    }

    /** The live alerts, after syncing. status null means open and acknowledged; "RESOLVED" shows the closed ones, newest first. */
    @Transactional
    public List<Alert> list(TenantId tenantId, UUID warehouseId, UUID clientId, String status, String kind) {
        sync(tenantId);
        UUID t = tenantId.getValue();
        Map<String, Condition> byKey = new HashMap<>();
        for (Condition c : conditions(tenantId, warehouseId, clientId)) byKey.put(c.warehouseId() + "|" + c.itemId(), c);
        boolean resolved = "RESOLVED".equalsIgnoreCase(status);
        return jdbc.query("SELECT a.id, a.warehouse_id, w.code AS warehouse_code, a.client_id, c.trading_name AS client_name, a.item_id, it.sku, it.description, a.kind, a.status, a.first_seen, a.last_seen, "
                        + "a.available, a.min_qty, a.max_qty, a.recommended_order, a.acknowledged_at, a.resolution, a.resolution_note "
                        + "FROM whse_stock_alerts a JOIN whse_warehouses w ON w.id = a.warehouse_id AND w.tenant_id = a.tenant_id JOIN whse_items it ON it.id = a.item_id "
                        + "JOIN whse_clients c ON c.id = a.client_id WHERE a.tenant_id = ? AND (?::uuid IS NULL OR a.warehouse_id = ?::uuid) AND (?::uuid IS NULL OR a.client_id = ?::uuid) "
                        + "AND (?::text IS NULL OR a.kind = ?::text) AND " + (resolved ? "a.status = 'RESOLVED'" : "a.status <> 'RESOLVED'")
                        + (resolved ? " ORDER BY a.resolved_at DESC LIMIT 200" : " ORDER BY a.kind, w.code, it.sku"),
                (rs, i) -> {
                    Condition c = byKey.get(rs.getObject("warehouse_id") + "|" + rs.getObject("item_id"));
                    Figures f = c == null ? null : c.figures();
                    Levels l = c == null ? null : c.levels();
                    return new Alert((UUID) rs.getObject("id"), (UUID) rs.getObject("warehouse_id"), rs.getString("warehouse_code"), (UUID) rs.getObject("client_id"), rs.getString("client_name"),
                            (UUID) rs.getObject("item_id"), rs.getString("sku"), rs.getString("description"), rs.getString("kind"), rs.getString("status"),
                            rs.getTimestamp("first_seen").toInstant(), rs.getTimestamp("last_seen").toInstant(),
                            f == null ? null : f.physical(), f == null ? null : f.unavailable(), f == null ? null : f.reserved(), rs.getBigDecimal("available"), f == null ? null : f.incoming(),
                            rs.getBigDecimal("min_qty"), l == null || l.minSource() == null ? null : l.minSource().name(),
                            rs.getBigDecimal("max_qty"), l == null || l.maxSource() == null ? null : l.maxSource().name(),
                            rs.getBigDecimal("recommended_order"), rs.getTimestamp("acknowledged_at") == null ? null : rs.getTimestamp("acknowledged_at").toInstant(),
                            rs.getString("resolution"), rs.getString("resolution_note"));
                }, t, warehouseId, warehouseId, clientId, clientId, kind == null || kind.isBlank() ? null : kind.strip().toUpperCase(), kind == null || kind.isBlank() ? null : kind.strip().toUpperCase());
    }

    @Transactional
    public void acknowledge(TenantId tenantId, UUID alertId, UUID actor) {
        requireActor(actor);
        int n = jdbc.update("UPDATE whse_stock_alerts SET status = 'ACKNOWLEDGED', acknowledged_by = ?, acknowledged_at = now() WHERE tenant_id = ? AND id = ? AND status = 'OPEN'", actor, tenantId.getValue(), alertId);
        if (n == 0) throw new IllegalArgumentException("That alert was not found, or it is not open.");
        audit(tenantId, actor, "WHSE_STOCK_ALERT_ACKNOWLEDGED", alertId, null);
    }

    /** A person closes an alert with a note. It is not raised again until the condition has cleared and come back. */
    @Transactional
    public void resolve(TenantId tenantId, UUID alertId, String note, UUID actor) {
        requireActor(actor);
        String n = note == null ? "" : note.strip();
        if (n.length() < 5) throw new IllegalArgumentException("Say why in at least 5 characters (for example \"stock on its way\").");
        if (n.length() > 300) throw new IllegalArgumentException("The note can be at most 300 characters.");
        int changed = jdbc.update("UPDATE whse_stock_alerts SET status = 'RESOLVED', resolved_by = ?, resolved_at = now(), resolution = 'MANUAL', resolution_note = ?, suppressed = TRUE WHERE tenant_id = ? AND id = ? AND status <> 'RESOLVED'",
                actor, n, tenantId.getValue(), alertId);
        if (changed == 0) throw new IllegalArgumentException("That alert was not found, or it is already resolved.");
        audit(tenantId, actor, "WHSE_STOCK_ALERT_RESOLVED", alertId, n);
    }

    @Transactional(readOnly = true)
    public List<Threshold> thresholds(TenantId tenantId, UUID warehouseId, UUID itemId) {
        return jdbc.query("SELECT t.warehouse_id, w.code, t.item_id, it.sku, t.min_qty, t.max_qty, it.min_qty AS item_min, it.max_qty AS item_max FROM whse_stock_thresholds t "
                        + "JOIN whse_warehouses w ON w.id = t.warehouse_id AND w.tenant_id = t.tenant_id JOIN whse_items it ON it.id = t.item_id "
                        + "WHERE t.tenant_id = ? AND (?::uuid IS NULL OR t.warehouse_id = ?::uuid) AND (?::uuid IS NULL OR t.item_id = ?::uuid) ORDER BY w.code, it.sku",
                (rs, i) -> new Threshold((UUID) rs.getObject(1), rs.getString(2), (UUID) rs.getObject(3), rs.getString(4), rs.getBigDecimal(5), rs.getBigDecimal(6), rs.getBigDecimal(7), rs.getBigDecimal(8)),
                tenantId.getValue(), warehouseId, warehouseId, itemId, itemId);
    }

    /** Sets (or replaces) a warehouse's own minimum and/or maximum for an item. A level left blank falls back to the item's default. */
    @Transactional
    public void setThreshold(TenantId tenantId, UUID warehouseId, UUID itemId, BigDecimal min, BigDecimal max, UUID actor) {
        requireActor(actor);
        String problem = WhseStockAlertRules.problem(min, max);
        if (problem != null) throw new IllegalArgumentException(problem);
        UUID t = tenantId.getValue();
        Integer w = jdbc.queryForObject("SELECT count(*) FROM whse_warehouses WHERE tenant_id = ? AND id = ? AND active AND deleted_at IS NULL", Integer.class, t, warehouseId);
        if (w == null || w == 0) throw new IllegalArgumentException("That warehouse does not exist or is not active.");
        List<BigDecimal[]> item = jdbc.query("SELECT min_qty, max_qty FROM whse_items WHERE tenant_id = ? AND id = ? AND deleted_at IS NULL",
                (rs, i) -> new BigDecimal[]{rs.getBigDecimal(1), rs.getBigDecimal(2)}, t, itemId);
        if (item.isEmpty()) throw new IllegalArgumentException("That item was not found.");
        String after = WhseStockAlertRules.problemAfterDefaults(min, max, item.get(0)[0], item.get(0)[1]);
        if (after != null) throw new IllegalArgumentException(after);
        jdbc.update("INSERT INTO whse_stock_thresholds (id, tenant_id, warehouse_id, item_id, min_qty, max_qty, updated_by) VALUES (?,?,?,?,?,?,?) "
                        + "ON CONFLICT (tenant_id, warehouse_id, item_id) DO UPDATE SET min_qty = EXCLUDED.min_qty, max_qty = EXCLUDED.max_qty, updated_at = now(), updated_by = EXCLUDED.updated_by",
                UUID.randomUUID(), t, warehouseId, itemId, min, max, actor);
        audit(tenantId, actor, "WHSE_STOCK_THRESHOLD_SET", itemId, (min == null ? "" : "min " + min.toPlainString()) + (min != null && max != null ? ", " : "") + (max == null ? "" : "max " + max.toPlainString()) + " in warehouse " + warehouseId);
    }

    /** Removes the warehouse's own thresholds for an item, so the item's defaults apply again. */
    @Transactional
    public void clearThreshold(TenantId tenantId, UUID warehouseId, UUID itemId, UUID actor) {
        requireActor(actor);
        int n = jdbc.update("DELETE FROM whse_stock_thresholds WHERE tenant_id = ? AND warehouse_id = ? AND item_id = ?", tenantId.getValue(), warehouseId, itemId);
        if (n == 0) throw new IllegalArgumentException("That warehouse has no threshold of its own for the item.");
        audit(tenantId, actor, "WHSE_STOCK_THRESHOLD_CLEARED", itemId, "warehouse " + warehouseId);
    }

    private static void requireActor(UUID actor) {
        if (actor == null) throw new AccessDeniedException("Your user could not be identified, so this change cannot be recorded.");
    }

    private void audit(TenantId tenantId, UUID actor, String action, UUID itemId, String detail) {
        jdbc.update("INSERT INTO audit_events (tenant_id, actor_type, actor_id, action, item_type, item_id, after_json) VALUES (?, 'STAFF', ?, ?, 'WHSE_STOCK_ALERT', ?, jsonb_build_object('detail', ?::text))",
                tenantId.getValue(), actor, action, itemId, detail);
    }
}
