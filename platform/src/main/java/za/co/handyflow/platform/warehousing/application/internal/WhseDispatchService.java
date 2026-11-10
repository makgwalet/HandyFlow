package za.co.handyflow.platform.warehousing.application.internal;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import za.co.handyflow.platform.shared.ResourceNotFoundException;
import za.co.handyflow.platform.shared.TenantId;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * Dispatch: loading, manifest, proof of delivery.
 * <p>
 * A manifest puts PACKED orders on one vehicle and driver. OPEN (orders can be added and removed) -> LOADING (the truck is
 * being loaded; vehicle and driver are required) -> DISPATCHED. Dispatching ships every order on the manifest in one
 * transaction (stock leaves exactly as a single ship does), so either all of them go or none do. An order on a manifest cannot be
 * shipped on its own. After shipping, each order gets a proof-of-delivery record: who received it and when. A signed POD file can be
 * attached to the order as evidence. "Delivered" is a flag on the order, not a status, so everything that reads SHIPPED is unaffected.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class WhseDispatchService {

    static final int MIN_REASON = 5;

    private final JdbcTemplate jdbc;
    private final WhseOutboundOrderService orderService;
    private final WhseNumberGenerator numbers;

    @org.springframework.beans.factory.annotation.Value("${handyflow.warehousing.billing-zone:Africa/Johannesburg}")
    private String zone = "Africa/Johannesburg";

    public record ManifestRow(UUID id, String manifestNumber, String carrier, String vehicleRegistration, String driverName, String notes,
                              String status, Instant createdAt, Instant loadingStartedAt, Instant dispatchedAt, String cancelReason, int orderCount) {}
    public record LineRow(String sku, String description, BigDecimal qty) {}
    public record OrderRow(UUID orderId, String orderReference, UUID clientId, String clientName, String shipToName, String shipToAddress,
                           String status, Instant deliveredAt, String podReceivedBy, String podNote, List<LineRow> lines) {
        public BigDecimal units() { return lines.stream().map(LineRow::qty).reduce(BigDecimal.ZERO, BigDecimal::add); }
    }
    public record ManifestDetail(ManifestRow manifest, List<OrderRow> orders) {}
    public record ReadyOrder(UUID orderId, String orderReference, UUID clientId, String clientName, String shipToName) {}
    public record Delivery(UUID orderId, UUID manifestId, String manifestNumber, Instant deliveredAt, String receivedBy, String note) {}

    // ── manifests ───────────────────────────────────────────────────────────

    @Transactional
    public UUID create(TenantId tenantId, String carrier, String vehicleRegistration, String driverName, String notes, UUID actor) {
        requireActor(actor);
        UUID id = UUID.randomUUID();
        jdbc.update("INSERT INTO whse_dispatch_manifests (id, tenant_id, manifest_number, carrier, vehicle_registration, driver_name, notes, created_by) VALUES (?,?,?,?,?,?,?,?)",
                id, tenantId.getValue(), numbers.nextManifestNumber(tenantId), blank(carrier), upper(vehicleRegistration), blank(driverName), blank(notes), actor);
        return id;
    }

    /** Orders that are packed and not yet on a manifest. */
    @Transactional(readOnly = true)
    public List<ReadyOrder> readyOrders(TenantId tenantId) {
        return jdbc.query("""
                SELECT o.id, o.order_reference, o.client_id, c.trading_name, o.ship_to_name
                  FROM whse_outbound_orders o JOIN whse_clients c ON c.id = o.client_id
                 WHERE o.tenant_id = ? AND o.status = 'PACKED' AND o.manifest_id IS NULL
                 ORDER BY o.requested_ship_date NULLS LAST, o.created_at""",
                (rs, i) -> new ReadyOrder(rs.getObject(1, UUID.class), rs.getString(2), rs.getObject(3, UUID.class), rs.getString(4), rs.getString(5)),
                tenantId.getValue());
    }

    @Transactional(readOnly = true)
    public List<ManifestRow> list(TenantId tenantId, String status) {
        return jdbc.query("""
                SELECT m.id, m.manifest_number, m.carrier, m.vehicle_registration, m.driver_name, m.notes, m.status, m.created_at,
                       m.loading_started_at, m.dispatched_at, m.cancel_reason,
                       (SELECT count(*) FROM whse_outbound_orders o WHERE o.manifest_id = m.id) AS orders
                  FROM whse_dispatch_manifests m WHERE m.tenant_id = ? AND (? IS NULL OR m.status = ?)
                 ORDER BY m.created_at DESC LIMIT 200""", MANIFEST_MAPPER, tenantId.getValue(), status, status);
    }

    @Transactional(readOnly = true)
    public ManifestDetail get(TenantId tenantId, UUID manifestId) {
        List<ManifestRow> rows = jdbc.query("""
                SELECT m.id, m.manifest_number, m.carrier, m.vehicle_registration, m.driver_name, m.notes, m.status, m.created_at,
                       m.loading_started_at, m.dispatched_at, m.cancel_reason,
                       (SELECT count(*) FROM whse_outbound_orders o WHERE o.manifest_id = m.id) AS orders
                  FROM whse_dispatch_manifests m WHERE m.tenant_id = ? AND m.id = ?""", MANIFEST_MAPPER, tenantId.getValue(), manifestId);
        if (rows.isEmpty()) throw new ResourceNotFoundException("Dispatch manifest", manifestId.toString());
        Map<UUID, List<LineRow>> lines = new LinkedHashMap<>();
        jdbc.query("""
                SELECT l.order_id, it.sku, it.description, l.qty_ordered - l.qty_short
                  FROM whse_outbound_order_lines l
                  JOIN whse_items it ON it.id = l.item_id
                  JOIN whse_outbound_orders o ON o.id = l.order_id
                 WHERE o.tenant_id = ? AND o.manifest_id = ? ORDER BY it.sku""",
                rs -> { lines.computeIfAbsent(rs.getObject(1, UUID.class), k -> new ArrayList<>())
                        .add(new LineRow(rs.getString(2), rs.getString(3), rs.getBigDecimal(4))); },
                tenantId.getValue(), manifestId);
        List<OrderRow> orders = jdbc.query("""
                SELECT o.id, o.order_reference, o.client_id, c.trading_name, o.ship_to_name, o.ship_to_address, o.status,
                       o.delivered_at, o.pod_received_by, o.pod_note
                  FROM whse_outbound_orders o JOIN whse_clients c ON c.id = o.client_id
                 WHERE o.tenant_id = ? AND o.manifest_id = ? ORDER BY o.order_reference NULLS LAST, o.created_at""",
                (rs, i) -> {
                    UUID id = rs.getObject(1, UUID.class);
                    java.sql.Timestamp d = rs.getTimestamp(8);
                    return new OrderRow(id, rs.getString(2), rs.getObject(3, UUID.class), rs.getString(4), rs.getString(5), rs.getString(6),
                            rs.getString(7), d == null ? null : d.toInstant(), rs.getString(9), rs.getString(10), lines.getOrDefault(id, List.of()));
                }, tenantId.getValue(), manifestId);
        return new ManifestDetail(rows.get(0), orders);
    }

    @Transactional
    public void addOrder(TenantId tenantId, UUID manifestId, UUID orderId) {
        Manifest m = lock(tenantId, manifestId);
        if (!m.canChangeOrders()) throw new IllegalStateException("Orders can only be added while the manifest is open or loading.");
        int n = jdbc.update("UPDATE whse_outbound_orders SET manifest_id = ?, updated_at = now() WHERE tenant_id = ? AND id = ? AND status = 'PACKED' AND manifest_id IS NULL",
                manifestId, tenantId.getValue(), orderId);
        if (n == 0) throw new IllegalStateException("That order is not packed, does not exist, or is already on a manifest.");
    }

    @Transactional
    public void removeOrder(TenantId tenantId, UUID manifestId, UUID orderId) {
        Manifest m = lock(tenantId, manifestId);
        if (!m.canChangeOrders()) throw new IllegalStateException("Orders can only be taken off while the manifest is open or loading.");
        int n = jdbc.update("UPDATE whse_outbound_orders SET manifest_id = NULL, updated_at = now() WHERE tenant_id = ? AND id = ? AND manifest_id = ?",
                tenantId.getValue(), orderId, manifestId);
        if (n == 0) throw new ResourceNotFoundException("Order on this manifest", orderId.toString());
    }

    @Transactional
    public void startLoading(TenantId tenantId, UUID manifestId, UUID actor) {
        requireActor(actor);
        Manifest m = lock(tenantId, manifestId);
        if (!"OPEN".equals(m.status)) throw new IllegalStateException("Only an open manifest can start loading.");
        if (m.vehicle == null || m.driver == null) throw new IllegalArgumentException("Record the vehicle registration and the driver before loading starts.");
        if (orderCount(manifestId) == 0) throw new IllegalStateException("Put at least one packed order on the manifest before loading.");
        jdbc.update("UPDATE whse_dispatch_manifests SET status = 'LOADING', loading_started_at = now(), loading_started_by = ?, version = version + 1 WHERE id = ?", actor, manifestId);
    }

    /** Ships every order on the manifest, all or none, and closes the manifest as DISPATCHED. */
    @Transactional
    public void dispatch(TenantId tenantId, UUID manifestId, UUID actor) {
        requireActor(actor);
        Manifest m = lock(tenantId, manifestId);
        if (!"LOADING".equals(m.status)) throw new IllegalStateException("A manifest must be loading before it can be dispatched.");
        List<Object[]> orders = jdbc.query("SELECT id, order_reference FROM whse_outbound_orders WHERE tenant_id = ? AND manifest_id = ? ORDER BY created_at",
                (rs, i) -> new Object[]{rs.getObject(1, UUID.class), rs.getString(2)}, tenantId.getValue(), manifestId);
        if (orders.isEmpty()) throw new IllegalStateException("There are no orders on this manifest.");
        for (Object[] o : orders) {
            try {
                orderService.markShipped(tenantId, (UUID) o[0], m.carrier, m.number, actor);
            } catch (IllegalStateException e) {
                // Roll the whole dispatch back, naming the order so it can be fixed or taken off.
                throw new IllegalStateException("Order " + (o[1] == null ? o[0] : o[1]) + " cannot be dispatched: " + e.getMessage(), e);
            }
        }
        jdbc.update("UPDATE whse_dispatch_manifests SET status = 'DISPATCHED', dispatched_at = now(), dispatched_by = ?, version = version + 1 WHERE id = ?", actor, manifestId);
        log.info("[Warehousing] Manifest {} dispatched tenant={} orders={} by={}", m.number, tenantId.getValue(), orders.size(), actor);
    }

    @Transactional
    public void cancel(TenantId tenantId, UUID manifestId, String reason, UUID actor) {
        requireActor(actor);
        if (reason == null || reason.trim().length() < MIN_REASON) throw new IllegalArgumentException("Give a reason (at least " + MIN_REASON + " characters).");
        Manifest m = lock(tenantId, manifestId);
        if (!m.canChangeOrders()) throw new IllegalStateException("Only an open or loading manifest can be cancelled.");
        jdbc.update("UPDATE whse_outbound_orders SET manifest_id = NULL, updated_at = now() WHERE tenant_id = ? AND manifest_id = ?", tenantId.getValue(), manifestId);
        jdbc.update("UPDATE whse_dispatch_manifests SET status = 'CANCELLED', cancelled_at = now(), cancelled_by = ?, cancel_reason = ?, version = version + 1 WHERE id = ?",
                actor, reason.trim(), manifestId);
    }

    /** The manifest an order is on, or null. An order on a manifest is shipped by dispatching the manifest, not on its own. */
    @Transactional(readOnly = true)
    public UUID manifestOf(TenantId tenantId, UUID orderId) {
        List<UUID> rows = jdbc.query("SELECT manifest_id FROM whse_outbound_orders WHERE tenant_id = ? AND id = ?",
                (rs, i) -> rs.getObject(1, UUID.class), tenantId.getValue(), orderId);
        return rows.isEmpty() ? null : rows.get(0);
    }

    // ── proof of delivery ───────────────────────────────────────────────────

    @Transactional
    public void recordDelivery(TenantId tenantId, UUID orderId, String receivedBy, Instant deliveredAt, String note, UUID actor) {
        requireActor(actor);
        if (receivedBy == null || receivedBy.trim().length() < 2) throw new IllegalArgumentException("Say who received the goods.");
        if (deliveredAt == null) throw new IllegalArgumentException("Say when it was delivered.");
        if (deliveredAt.isAfter(Instant.now().plusSeconds(300))) throw new IllegalArgumentException("The delivery time cannot be in the future.");
        List<Object[]> rows = jdbc.query("SELECT status, shipped_date, delivered_at FROM whse_outbound_orders WHERE tenant_id = ? AND id = ? FOR UPDATE",
                (rs, i) -> new Object[]{rs.getString(1), rs.getDate(2), rs.getTimestamp(3)}, tenantId.getValue(), orderId);
        if (rows.isEmpty()) throw new ResourceNotFoundException("WhseOutboundOrder", orderId.toString());
        Object[] r = rows.get(0);
        if (!"SHIPPED".equals(r[0])) throw new IllegalStateException("Only a shipped order can be marked delivered.");
        if (r[2] != null) throw new IllegalStateException("This order's delivery has already been recorded.");
        if (r[1] != null) {
            LocalDate shipped = ((java.sql.Date) r[1]).toLocalDate();
            if (deliveredAt.isBefore(shipped.atStartOfDay(ZoneId.of(zone)).toInstant())) {
                throw new IllegalArgumentException("It cannot have been delivered before it shipped on " + shipped + ".");
            }
        }
        jdbc.update("UPDATE whse_outbound_orders SET delivered_at = ?, delivered_by = ?, pod_received_by = ?, pod_note = ?, updated_at = now() WHERE id = ?",
                java.sql.Timestamp.from(deliveredAt), actor, receivedBy.trim(), blank(note), orderId);
        String name = null;
        try { name = za.co.handyflow.platform.shared.TenantContext.getCurrentUserName(); } catch (RuntimeException ignored) { /* no named user */ }
        jdbc.update("INSERT INTO audit_events (tenant_id, actor_type, actor_id, actor_name, action, item_type, item_id, after_json) VALUES (?, 'STAFF', ?, ?, 'WHSE_DELIVERY_RECORDED', 'WHSE_OUTBOUND_ORDER', ?, ?::jsonb)",
                tenantId.getValue(), actor, name, orderId, "{\"receivedBy\":" + quote(receivedBy.trim()) + ",\"deliveredAt\":\"" + deliveredAt + "\"}");
    }

    @Transactional(readOnly = true)
    public Delivery deliveryOf(TenantId tenantId, UUID orderId) {
        List<Delivery> rows = jdbc.query("""
                SELECT o.id, o.manifest_id, m.manifest_number, o.delivered_at, o.pod_received_by, o.pod_note
                  FROM whse_outbound_orders o LEFT JOIN whse_dispatch_manifests m ON m.id = o.manifest_id
                 WHERE o.tenant_id = ? AND o.id = ?""",
                (rs, i) -> {
                    java.sql.Timestamp d = rs.getTimestamp(4);
                    return new Delivery(rs.getObject(1, UUID.class), rs.getObject(2, UUID.class), rs.getString(3), d == null ? null : d.toInstant(),
                            rs.getString(5), rs.getString(6));
                }, tenantId.getValue(), orderId);
        if (rows.isEmpty()) throw new ResourceNotFoundException("WhseOutboundOrder", orderId.toString());
        return rows.get(0);
    }

    // ── internals ───────────────────────────────────────────────────────────

    private static final org.springframework.jdbc.core.RowMapper<ManifestRow> MANIFEST_MAPPER = (rs, i) -> {
        java.sql.Timestamp loading = rs.getTimestamp("loading_started_at"), dispatched = rs.getTimestamp("dispatched_at");
        return new ManifestRow(rs.getObject("id", UUID.class), rs.getString("manifest_number"), rs.getString("carrier"),
                rs.getString("vehicle_registration"), rs.getString("driver_name"), rs.getString("notes"), rs.getString("status"),
                rs.getTimestamp("created_at").toInstant(), loading == null ? null : loading.toInstant(),
                dispatched == null ? null : dispatched.toInstant(), rs.getString("cancel_reason"), rs.getInt("orders"));
    };

    record Manifest(String number, String status, String carrier, String vehicle, String driver) {
        boolean canChangeOrders() { return "OPEN".equals(status) || "LOADING".equals(status); }
    }

    private Manifest lock(TenantId tenantId, UUID id) {
        List<Manifest> rows = jdbc.query("SELECT manifest_number, status, carrier, vehicle_registration, driver_name FROM whse_dispatch_manifests WHERE tenant_id = ? AND id = ? FOR UPDATE",
                (rs, i) -> new Manifest(rs.getString(1), rs.getString(2), rs.getString(3), rs.getString(4), rs.getString(5)), tenantId.getValue(), id);
        if (rows.isEmpty()) throw new ResourceNotFoundException("Dispatch manifest", id.toString());
        return rows.get(0);
    }

    private int orderCount(UUID manifestId) {
        Integer n = jdbc.queryForObject("SELECT count(*) FROM whse_outbound_orders WHERE manifest_id = ?", Integer.class, manifestId);
        return n == null ? 0 : n;
    }

    private static void requireActor(UUID actor) {
        if (actor == null) throw new AccessDeniedException("Your user could not be identified, so this step cannot be recorded.");
    }

    private static String blank(String s) { return s == null || s.isBlank() ? null : s.trim(); }
    private static String upper(String s) { return s == null || s.isBlank() ? null : s.trim().toUpperCase(java.util.Locale.ROOT); }

    private static String quote(String s) {
        return "\"" + s.replace("\\", "\\\\").replace("\"", "\\\"").replace("\n", " ").replace("\r", " ") + "\"";
    }
}
