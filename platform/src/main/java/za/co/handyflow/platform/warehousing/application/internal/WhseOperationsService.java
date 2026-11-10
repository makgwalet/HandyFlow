package za.co.handyflow.platform.warehousing.application.internal;

import lombok.RequiredArgsConstructor;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import za.co.handyflow.platform.shared.TenantId;

import java.math.BigDecimal;
import java.sql.Timestamp;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * Read models for the warehouse operations screens: the dashboard summary, the stock list across all clients, the
 * movement history and what is currently reserved. Read-only, tenant-scoped on every query, and cut into calendar days
 * in the same billing zone the storage accrual uses.
 *
 * Not here on purpose: stock value. Items have no cost price, so a value would be invented. It appears when item cost does.
 */
@Service
@RequiredArgsConstructor
public class WhseOperationsService {

    static final int MAX_PAGE_SIZE = 200;
    static final int MAX_TREND_DAYS = 90;

    private final JdbcTemplate jdbc;

    @org.springframework.beans.factory.annotation.Value("${handyflow.warehousing.billing-zone:Africa/Johannesburg}")
    private String billingZone = "Africa/Johannesburg";

    ZoneId zone() { return ZoneId.of(billingZone); }

    // ── result shapes ───────────────────────────────────────────────────────

    public record StatusStock(String status, BigDecimal unitsOnHand, BigDecimal unitsAllocated, long positions) {}
    public record Counts(long expectedInbound, long partiallyReceivedInbound, long overdueInbound,
                         long pendingOutbound, long pickingOutbound, long packedOutbound, long overdueOutbound) {}
    public record TodayActivity(BigDecimal unitsReceived, BigDecimal unitsDispatched, long inboundReceived, long ordersShipped,
                                long adjustments) {}
    public record TopMover(UUID itemId, String sku, String description, String clientName, BigDecimal unitsMoved) {}
    public record Summary(LocalDate date, BigDecimal totalUnitsOnHand, BigDecimal totalUnitsAllocated, BigDecimal totalUnitsAvailable,
                          long stockedPositions, long activeClients, List<StatusStock> byStatus, Counts work, TodayActivity today,
                          long adjustmentsAwaitingApproval, long invoicesAwaitingApproval, List<TopMover> topMovers) {}
    public record TrendPoint(LocalDate day, BigDecimal received, BigDecimal dispatched, BigDecimal adjusted) {}

    public record StockRow(UUID inventoryId, UUID clientId, String clientName, UUID itemId, String sku, String description, String uom,
                           UUID locationId, String locationCode, String zone, String status, BigDecimal qtyOnHand,
                           BigDecimal qtyAllocated, BigDecimal available) {}
    public record MovementRow(UUID id, java.time.Instant at, UUID clientId, String clientName, UUID itemId, String sku, String locationCode,
                              String movementType, String status, BigDecimal qtyChange, BigDecimal qtyBefore, BigDecimal qtyAfter,
                              String referenceType, UUID referenceId, String referenceNumber, String notes, UUID recordedBy) {}
    public record ReservationRow(String referenceType, UUID referenceId, String referenceNumber, UUID clientId, String clientName,
                                 UUID itemId, String sku, String locationCode, BigDecimal qtyReserved) {}
    public record Page<T>(List<T> content, long total, int page, int size) {}

    // ── summary ─────────────────────────────────────────────────────────────

    @Transactional(readOnly = true)
    public Summary summary(TenantId tenantId) {
        UUID t = tenantId.getValue();
        LocalDate today = LocalDate.now(zone());
        Timestamp from = Timestamp.from(today.atStartOfDay(zone()).toInstant());
        Timestamp to = Timestamp.from(today.plusDays(1).atStartOfDay(zone()).toInstant());

        List<StatusStock> byStatus = jdbc.query(SQL_STOCK_BY_STATUS,
                (rs, i) -> new StatusStock(rs.getString("status"), rs.getBigDecimal("on_hand"), rs.getBigDecimal("allocated"), rs.getLong("positions")), t);
        BigDecimal onHand = byStatus.stream().map(StatusStock::unitsOnHand).reduce(BigDecimal.ZERO, BigDecimal::add);
        BigDecimal allocated = byStatus.stream().map(StatusStock::unitsAllocated).reduce(BigDecimal.ZERO, BigDecimal::add);
        BigDecimal availableUnits = byStatus.stream().filter(s -> "AVAILABLE".equals(s.status()))
                .map(s -> s.unitsOnHand().subtract(s.unitsAllocated())).reduce(BigDecimal.ZERO, BigDecimal::add);
        long positions = byStatus.stream().mapToLong(StatusStock::positions).sum();

        long clients = count("SELECT count(*) FROM whse_clients WHERE tenant_id = ? AND deleted_at IS NULL AND status = 'ACTIVE'", t);
        Map<String, Long> inbound = statusCounts("SELECT status, count(*) AS n FROM whse_inbound_shipments WHERE tenant_id = ? AND status IN ('EXPECTED','PARTIALLY_RECEIVED') GROUP BY status", t);
        Map<String, Long> outbound = statusCounts("SELECT status, count(*) AS n FROM whse_outbound_orders WHERE tenant_id = ? AND status IN ('PENDING','PICKING','PACKED') GROUP BY status", t);
        long overdueIn = count("SELECT count(*) FROM whse_inbound_shipments WHERE tenant_id = ? AND status IN ('EXPECTED','PARTIALLY_RECEIVED') AND expected_date < ?", t, java.sql.Date.valueOf(today));
        long overdueOut = count("SELECT count(*) FROM whse_outbound_orders WHERE tenant_id = ? AND status IN ('PENDING','PICKING','PACKED') AND requested_ship_date < ?", t, java.sql.Date.valueOf(today));

        TodayActivity activity = jdbc.queryForObject(SQL_TODAY,
                (rs, i) -> new TodayActivity(rs.getBigDecimal("received"), rs.getBigDecimal("dispatched"), 0, 0, rs.getLong("adjustments")),
                t, from, to);
        long receivedToday = count("SELECT count(*) FROM whse_inbound_shipments WHERE tenant_id = ? AND received_date = ?", t, java.sql.Date.valueOf(today));
        long shippedToday = count("SELECT count(*) FROM whse_outbound_orders WHERE tenant_id = ? AND shipped_date = ?", t, java.sql.Date.valueOf(today));
        activity = new TodayActivity(activity.unitsReceived(), activity.unitsDispatched(), receivedToday, shippedToday, activity.adjustments());

        long adjPending = count("SELECT count(*) FROM whse_adjustment_requests WHERE tenant_id = ? AND status = 'PENDING'", t);
        long invPending = count("SELECT count(*) FROM whse_billing_invoices WHERE tenant_id = ? AND status = 'DRAFT'", t);

        Timestamp since = Timestamp.from(today.minusDays(29).atStartOfDay(zone()).toInstant());
        List<TopMover> movers = jdbc.query(SQL_TOP_MOVERS,
                (rs, i) -> new TopMover(rs.getObject("item_id", UUID.class), rs.getString("sku"), rs.getString("description"),
                        rs.getString("client_name"), rs.getBigDecimal("moved")), t, since, to);

        return new Summary(today, onHand, allocated, availableUnits, positions, clients, byStatus,
                new Counts(inbound.getOrDefault("EXPECTED", 0L), inbound.getOrDefault("PARTIALLY_RECEIVED", 0L), overdueIn,
                        outbound.getOrDefault("PENDING", 0L), outbound.getOrDefault("PICKING", 0L), outbound.getOrDefault("PACKED", 0L), overdueOut),
                activity, adjPending, invPending, movers);
    }

    /** Units received, dispatched and adjusted per calendar day for the last {@code days} days, oldest first, every day present. */
    @Transactional(readOnly = true)
    public List<TrendPoint> trend(TenantId tenantId, int days) {
        int n = Math.max(1, Math.min(days, MAX_TREND_DAYS));
        LocalDate today = LocalDate.now(zone());
        LocalDate first = today.minusDays(n - 1L);
        Timestamp from = Timestamp.from(first.atStartOfDay(zone()).toInstant());
        Timestamp to = Timestamp.from(today.plusDays(1).atStartOfDay(zone()).toInstant());
        Map<LocalDate, BigDecimal[]> byDay = new LinkedHashMap<>();
        for (int i = 0; i < n; i++) byDay.put(first.plusDays(i), new BigDecimal[]{BigDecimal.ZERO, BigDecimal.ZERO, BigDecimal.ZERO});
        jdbc.query(SQL_TREND, rs -> {
            LocalDate d = rs.getDate("day").toLocalDate();
            BigDecimal[] v = byDay.get(d);
            if (v != null) { v[0] = rs.getBigDecimal("received"); v[1] = rs.getBigDecimal("dispatched"); v[2] = rs.getBigDecimal("adjusted"); }
        }, billingZone, tenantId.getValue(), from, to);
        List<TrendPoint> out = new ArrayList<>();
        byDay.forEach((d, v) -> out.add(new TrendPoint(d, v[0], v[1], v[2])));
        return out;
    }

    // ── lists ───────────────────────────────────────────────────────────────

    /** Stock across every client. Filters are optional; {@code q} matches SKU or description. */
    @Transactional(readOnly = true)
    public Page<StockRow> stock(TenantId tenantId, UUID clientId, String status, String q, boolean includeEmpty, int page, int size) {
        int s = clampSize(size), p = Math.max(0, page);
        String like = q == null || q.isBlank() ? null : "%" + q.trim().toLowerCase().replace("%", "\\%").replace("_", "\\_") + "%";
        Object[] filter = {tenantId.getValue(), clientId, clientId, status, status, like, like, like, includeEmpty};
        long total = jdbc.queryForObject("SELECT count(*) FROM (" + SQL_STOCK_BASE + ") x", Long.class, filter);
        List<StockRow> rows = jdbc.query("SELECT * FROM (" + SQL_STOCK_BASE + ") x ORDER BY sku, location_code, status LIMIT ? OFFSET ?",
                (rs, i) -> new StockRow(rs.getObject("inventory_id", UUID.class), rs.getObject("client_id", UUID.class), rs.getString("client_name"),
                        rs.getObject("item_id", UUID.class), rs.getString("sku"), rs.getString("description"), rs.getString("uom"),
                        rs.getObject("location_id", UUID.class), rs.getString("location_code"), rs.getString("zone"), rs.getString("status"),
                        rs.getBigDecimal("qty_on_hand"), rs.getBigDecimal("qty_allocated"), rs.getBigDecimal("available")),
                concat(filter, s, (long) p * s));
        return new Page<>(rows, total, p, s);
    }

    @Transactional(readOnly = true)
    public Page<MovementRow> movements(TenantId tenantId, UUID clientId, UUID itemId, String type, LocalDate from, LocalDate to, int page, int size) {
        int s = clampSize(size), p = Math.max(0, page);
        Timestamp fromTs = from == null ? null : Timestamp.from(from.atStartOfDay(zone()).toInstant());
        Timestamp toTs = to == null ? null : Timestamp.from(to.plusDays(1).atStartOfDay(zone()).toInstant());
        Object[] filter = {tenantId.getValue(), clientId, clientId, itemId, itemId, type, type, fromTs, fromTs, toTs, toTs};
        long total = jdbc.queryForObject("SELECT count(*) FROM (" + SQL_MOVEMENTS_BASE + ") x", Long.class, filter);
        List<MovementRow> rows = jdbc.query("SELECT * FROM (" + SQL_MOVEMENTS_BASE + ") x ORDER BY created_at DESC, id DESC LIMIT ? OFFSET ?",
                (rs, i) -> new MovementRow(rs.getObject("id", UUID.class), rs.getTimestamp("created_at").toInstant(),
                        rs.getObject("client_id", UUID.class), rs.getString("client_name"), rs.getObject("item_id", UUID.class), rs.getString("sku"),
                        rs.getString("location_code"), rs.getString("movement_type"), rs.getString("status"), rs.getBigDecimal("qty_change"),
                        rs.getBigDecimal("qty_before"), rs.getBigDecimal("qty_after"), rs.getString("reference_type"),
                        rs.getObject("reference_id", UUID.class), rs.getString("reference_number"), rs.getString("notes"),
                        rs.getObject("recorded_by_user_id", UUID.class)),
                concat(filter, s, (long) p * s));
        return new Page<>(rows, total, p, s);
    }

    /** What is reserved right now, per order and position: allocations less what was released or picked. */
    @Transactional(readOnly = true)
    public List<ReservationRow> reservations(TenantId tenantId, UUID clientId) {
        return jdbc.query(SQL_RESERVATIONS,
                (rs, i) -> new ReservationRow(rs.getString("reference_type"), rs.getObject("reference_id", UUID.class), rs.getString("reference_number"),
                        rs.getObject("client_id", UUID.class), rs.getString("client_name"), rs.getObject("item_id", UUID.class), rs.getString("sku"),
                        rs.getString("location_code"), rs.getBigDecimal("reserved")),
                tenantId.getValue(), clientId, clientId);
    }

    // ── helpers ─────────────────────────────────────────────────────────────

    private long count(String sql, Object... args) {
        Long n = jdbc.queryForObject(sql, Long.class, args);
        return n == null ? 0 : n;
    }

    private Map<String, Long> statusCounts(String sql, Object... args) {
        Map<String, Long> out = new LinkedHashMap<>();
        jdbc.query(sql, rs -> { out.put(rs.getString("status"), rs.getLong("n")); }, args);
        return out;
    }

    static int clampSize(int size) { return size <= 0 ? 50 : Math.min(size, MAX_PAGE_SIZE); }

    private static Object[] concat(Object[] a, Object... more) {
        Object[] out = java.util.Arrays.copyOf(a, a.length + more.length);
        System.arraycopy(more, 0, out, a.length, more.length);
        return out;
    }

    // ── SQL ─────────────────────────────────────────────────────────────────

    static final String SQL_STOCK_BY_STATUS = """
            SELECT status, coalesce(sum(qty_on_hand), 0) AS on_hand, coalesce(sum(qty_allocated), 0) AS allocated, count(*) AS positions
              FROM whse_inventory WHERE tenant_id = ? AND qty_on_hand > 0 GROUP BY status ORDER BY status""";

    static final String SQL_TODAY = """
            SELECT coalesce(sum(CASE WHEN movement_type IN ('RECEIPT','RETURN') THEN qty_change END), 0) AS received,
                   coalesce(sum(CASE WHEN movement_type IN ('PICK','SHIP') THEN -qty_change END), 0) AS dispatched,
                   count(*) FILTER (WHERE movement_type IN ('ADJUSTMENT','COUNT_VARIANCE','LOSS','FOUND')) AS adjustments
              FROM whse_stock_movements WHERE tenant_id = ? AND created_at >= ? AND created_at < ?""";

    static final String SQL_TOP_MOVERS = """
            SELECT m.item_id, i.sku, i.description, c.trading_name AS client_name, sum(abs(m.qty_change)) AS moved
              FROM whse_stock_movements m
              JOIN whse_items i ON i.id = m.item_id AND i.tenant_id = m.tenant_id
              JOIN whse_clients c ON c.id = m.client_id AND c.tenant_id = m.tenant_id
             WHERE m.tenant_id = ? AND m.movement_type IN ('PICK','SHIP') AND m.created_at >= ? AND m.created_at < ?
             GROUP BY m.item_id, i.sku, i.description, c.trading_name ORDER BY moved DESC, i.sku LIMIT 5""";

    static final String SQL_TREND = """
            SELECT (created_at AT TIME ZONE ?)::date AS day,
                   coalesce(sum(CASE WHEN movement_type IN ('RECEIPT','RETURN') THEN qty_change END), 0) AS received,
                   coalesce(sum(CASE WHEN movement_type IN ('PICK','SHIP') THEN -qty_change END), 0) AS dispatched,
                   coalesce(sum(CASE WHEN movement_type IN ('ADJUSTMENT','COUNT_VARIANCE','LOSS','FOUND') THEN qty_change END), 0) AS adjusted
              FROM whse_stock_movements WHERE tenant_id = ? AND created_at >= ? AND created_at < ?
             GROUP BY 1 ORDER BY 1""";

    static final String SQL_STOCK_BASE = """
            SELECT inv.id AS inventory_id, inv.client_id, c.trading_name AS client_name, inv.item_id, it.sku, it.description, it.uom,
                   inv.location_id, l.code AS location_code, l.zone, inv.status, inv.qty_on_hand, inv.qty_allocated,
                   CASE WHEN inv.status = 'AVAILABLE' THEN inv.qty_on_hand - inv.qty_allocated ELSE 0 END AS available
              FROM whse_inventory inv
              JOIN whse_clients c ON c.id = inv.client_id AND c.tenant_id = inv.tenant_id
              JOIN whse_items it ON it.id = inv.item_id AND it.tenant_id = inv.tenant_id
              JOIN whse_locations l ON l.id = inv.location_id AND l.tenant_id = inv.tenant_id
             WHERE inv.tenant_id = ?
               AND (?::uuid IS NULL OR inv.client_id = ?::uuid)
               AND (?::text IS NULL OR inv.status = ?::text)
               AND (?::text IS NULL OR lower(it.sku) LIKE ?::text ESCAPE '\\' OR lower(it.description) LIKE ?::text ESCAPE '\\')
               AND (? OR inv.qty_on_hand > 0)""";

    static final String SQL_MOVEMENTS_BASE = """
            SELECT m.id, m.created_at, m.client_id, c.trading_name AS client_name, m.item_id, it.sku, l.code AS location_code,
                   m.movement_type, m.status, m.qty_change, m.qty_before, m.qty_after, m.reference_type, m.reference_id,
                   m.reference_number, m.notes, m.recorded_by_user_id
              FROM whse_stock_movements m
              JOIN whse_clients c ON c.id = m.client_id AND c.tenant_id = m.tenant_id
              JOIN whse_items it ON it.id = m.item_id AND it.tenant_id = m.tenant_id
              JOIN whse_locations l ON l.id = m.location_id AND l.tenant_id = m.tenant_id
             WHERE m.tenant_id = ?
               AND (?::uuid IS NULL OR m.client_id = ?::uuid)
               AND (?::uuid IS NULL OR m.item_id = ?::uuid)
               AND (?::text IS NULL OR m.movement_type = ?::text)
               AND (?::timestamptz IS NULL OR m.created_at >= ?::timestamptz)
               AND (?::timestamptz IS NULL OR m.created_at < ?::timestamptz)""";

    static final String SQL_RESERVATIONS = """
            SELECT e.reference_type, e.reference_id, e.reference_number, e.client_id, c.trading_name AS client_name, e.item_id, it.sku,
                   l.code AS location_code,
                   sum(CASE WHEN e.event_type = 'ALLOCATE' THEN e.qty ELSE -e.qty END) AS reserved
              FROM whse_allocation_events e
              JOIN whse_clients c ON c.id = e.client_id AND c.tenant_id = e.tenant_id
              JOIN whse_items it ON it.id = e.item_id AND it.tenant_id = e.tenant_id
              JOIN whse_locations l ON l.id = e.location_id AND l.tenant_id = e.tenant_id
             WHERE e.tenant_id = ? AND (?::uuid IS NULL OR e.client_id = ?::uuid)
             GROUP BY e.reference_type, e.reference_id, e.reference_number, e.client_id, c.trading_name, e.item_id, it.sku, l.code
            HAVING sum(CASE WHEN e.event_type = 'ALLOCATE' THEN e.qty ELSE -e.qty END) > 0
             ORDER BY e.reference_number NULLS LAST, it.sku""";
}
