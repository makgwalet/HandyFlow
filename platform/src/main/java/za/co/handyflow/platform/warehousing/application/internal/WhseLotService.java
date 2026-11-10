package za.co.handyflow.platform.warehousing.application.internal;

import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import za.co.handyflow.platform.shared.HandyFlowException;
import za.co.handyflow.platform.shared.ResourceNotFoundException;
import za.co.handyflow.platform.shared.TenantId;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.List;
import java.util.UUID;

/** Lots: finding or creating the lot a receipt belongs to, listing them, the expiry report, and switching an item's tracking. */
@Service
@RequiredArgsConstructor
public class WhseLotService {

    private final JdbcTemplate jdbc;
    @Value("${handyflow.warehousing.billing-zone:Africa/Johannesburg}")
    private String zoneId;

    public record Tracking(boolean lotTracked, boolean expiryTracked, Integer minShelfLifeDays) {}
    public record LotRow(UUID id, UUID clientId, UUID itemId, String sku, String lotNumber, LocalDate expiryDate, LocalDate manufacturedDate,
                         Long daysToExpiry, String expiryStatus, BigDecimal onHand, BigDecimal available, boolean blocked, String blockedReason) {}
    public record AffectedOrder(UUID orderId, String orderReference, BigDecimal qty) {}
    public record ExpiryRow(UUID inventoryId, UUID clientId, String clientName, UUID itemId, String sku, String description, UUID locationId,
                            String locationCode, String stockStatus, UUID lotId, String lotNumber, LocalDate expiryDate, long daysToExpiry,
                            String expiryStatus, BigDecimal qtyOnHand, BigDecimal qtyAllocated) {}

    LocalDate today() { return LocalDate.now(ZoneId.of(zoneId)); }

    /**
     * The lot a receipt belongs to. For an item that is not lot tracked this returns null and refuses any lot data. Otherwise it finds the
     * lot by number or creates it. A lot's expiry date is fixed once created: receiving more of the same lot with a different date is refused.
     */
    @Transactional
    public UUID resolveForReceipt(TenantId tenantId, UUID clientId, UUID itemId, String lotNumber, LocalDate expiry, LocalDate manufactured, UUID actor) {
        Tracking tr = tracking(tenantId.getValue(), clientId, itemId);
        String lot = WhseLotRules.validateLotInput(tr.lotTracked(), tr.expiryTracked(), lotNumber, expiry, manufactured);
        if (lot == null) return null;
        UUID t = tenantId.getValue();
        List<Object[]> existing = jdbc.query("SELECT id, expiry_date, manufactured_date, blocked FROM whse_lots WHERE tenant_id = ? AND client_id = ? AND item_id = ? AND lot_number = ?",
                (rs, i) -> new Object[]{rs.getObject(1, UUID.class), rs.getDate(2) == null ? null : rs.getDate(2).toLocalDate(),
                        rs.getDate(3) == null ? null : rs.getDate(3).toLocalDate(), rs.getBoolean(4)}, t, clientId, itemId, lot);
        if (!existing.isEmpty()) {
            if (Boolean.TRUE.equals(existing.get(0)[3])) {
                throw new HandyFlowException("Lot " + lot + " is blocked. Stock cannot be received into a blocked lot.", HttpStatus.CONFLICT, "LOT_BLOCKED");
            }
            LocalDate have = (LocalDate) existing.get(0)[1];
            if (tr.expiryTracked() && !expiry.equals(have)) {
                throw new HandyFlowException("Lot " + lot + " is already on file with expiry " + have + ". A lot has one expiry date; check the lot number or the date.",
                        HttpStatus.CONFLICT, "LOT_EXPIRY_MISMATCH");
            }
            return (UUID) existing.get(0)[0];
        }
        UUID id = UUID.randomUUID();
        jdbc.update("INSERT INTO whse_lots (id, tenant_id, client_id, item_id, lot_number, expiry_date, manufactured_date, created_by) VALUES (?,?,?,?,?,?,?,?)",
                id, t, clientId, itemId, lot, expiry, manufactured, actor);
        return id;
    }

    /** An existing lot, for flows that must not invent one (returns). Null when the item is not lot tracked. */
    @Transactional(readOnly = true)
    public UUID requireExisting(TenantId tenantId, UUID clientId, UUID itemId, String lotNumber) {
        Tracking tr = tracking(tenantId.getValue(), clientId, itemId);
        String lot = WhseLotRules.validateLotInput(tr.lotTracked(), false, lotNumber, null, null);
        if (lot == null) return null;
        List<UUID> ids = jdbc.query("SELECT id FROM whse_lots WHERE tenant_id = ? AND client_id = ? AND item_id = ? AND lot_number = ?",
                (rs, i) -> rs.getObject(1, UUID.class), tenantId.getValue(), clientId, itemId, lot);
        if (ids.isEmpty()) throw new IllegalArgumentException("There is no lot " + lot + " for that item. Check the lot number on the goods.");
        return ids.get(0);
    }

    @Transactional(readOnly = true)
    public List<LotRow> list(TenantId tenantId, UUID clientId, UUID itemId) {
        LocalDate today = today();
        return jdbc.query("""
                SELECT l.id, l.client_id, l.item_id, it.sku, l.lot_number, l.expiry_date, l.manufactured_date, l.blocked, l.blocked_reason,
                       coalesce(sum(i.qty_on_hand), 0) AS on_hand,
                       coalesce(sum(i.qty_on_hand - i.qty_allocated) FILTER (WHERE i.status = 'AVAILABLE'), 0) AS available
                  FROM whse_lots l JOIN whse_items it ON it.id = l.item_id
                  LEFT JOIN whse_inventory i ON i.lot_id = l.id
                 WHERE l.tenant_id = ? AND (?::uuid IS NULL OR l.client_id = ?::uuid) AND (?::uuid IS NULL OR l.item_id = ?::uuid)
                 GROUP BY l.id, it.sku
                 ORDER BY it.sku, l.expiry_date NULLS LAST, l.lot_number LIMIT 500""",
                (rs, i) -> {
                    LocalDate exp = rs.getDate("expiry_date") == null ? null : rs.getDate("expiry_date").toLocalDate();
                    return new LotRow(rs.getObject("id", UUID.class), rs.getObject("client_id", UUID.class), rs.getObject("item_id", UUID.class), rs.getString("sku"),
                            rs.getString("lot_number"), exp, rs.getDate("manufactured_date") == null ? null : rs.getDate("manufactured_date").toLocalDate(),
                            WhseLotRules.daysToExpiry(exp, today), WhseLotRules.expiryStatus(exp, today, 30), rs.getBigDecimal("on_hand"), rs.getBigDecimal("available"), rs.getBoolean("blocked"), rs.getString("blocked_reason"));
                }, tenantId.getValue(), clientId, clientId, itemId, itemId);
    }

    /** Stock with an expiry date that has passed or falls within {@code days}, soonest first. Expired stock is always included. */
    @Transactional(readOnly = true)
    public List<ExpiryRow> expiryReport(TenantId tenantId, UUID clientId, int days) {
        int d = Math.max(0, Math.min(days, 730));
        LocalDate today = today();
        return jdbc.query("""
                SELECT i.id AS inventory_id, i.client_id, c.trading_name AS client_name, i.item_id, it.sku, it.description, i.location_id, loc.code AS location_code,
                       i.status, l.id AS lot_id, l.lot_number, l.expiry_date, i.qty_on_hand, i.qty_allocated
                  FROM whse_inventory i
                  JOIN whse_lots l ON l.id = i.lot_id
                  JOIN whse_items it ON it.id = i.item_id
                  JOIN whse_clients c ON c.id = i.client_id
                  JOIN whse_locations loc ON loc.id = i.location_id
                 WHERE i.tenant_id = ? AND i.qty_on_hand > 0 AND l.expiry_date IS NOT NULL AND l.expiry_date <= ?
                   AND (?::uuid IS NULL OR i.client_id = ?::uuid)
                 ORDER BY l.expiry_date, it.sku, loc.code LIMIT 1000""",
                (rs, i) -> {
                    LocalDate exp = rs.getDate("expiry_date").toLocalDate();
                    return new ExpiryRow(rs.getObject("inventory_id", UUID.class), rs.getObject("client_id", UUID.class), rs.getString("client_name"),
                            rs.getObject("item_id", UUID.class), rs.getString("sku"), rs.getString("description"), rs.getObject("location_id", UUID.class),
                            rs.getString("location_code"), rs.getString("status"), rs.getObject("lot_id", UUID.class), rs.getString("lot_number"), exp,
                            WhseLotRules.daysToExpiry(exp, today), WhseLotRules.expiryStatus(exp, today, d), rs.getBigDecimal("qty_on_hand"), rs.getBigDecimal("qty_allocated"));
                }, tenantId.getValue(), java.sql.Date.valueOf(today.plusDays(d)), clientId, clientId);
    }

    /**
     * Blocks a lot, for a recall or a quality hold. A blocked lot is never allocated, cannot be shipped and takes no further receipts.
     * Returns the open orders that still hold stock of the lot, which need their lots re-allocated.
     */
    @Transactional
    public List<AffectedOrder> block(TenantId tenantId, UUID lotId, String reason, UUID actor) {
        if (actor == null) throw new AccessDeniedException("Your user could not be identified, so this step cannot be recorded.");
        if (reason == null || reason.trim().length() < 5) throw new IllegalArgumentException("Give a reason for blocking the lot (at least 5 characters).");
        UUID t = tenantId.getValue();
        int n = jdbc.update("UPDATE whse_lots SET blocked = TRUE, blocked_reason = ?, blocked_at = now(), blocked_by = ? WHERE id = ? AND tenant_id = ?", reason.trim(), actor, lotId, t);
        if (n == 0) throw new ResourceNotFoundException("Lot", lotId.toString());
        audit(t, actor, lotId, "WHSE_LOT_BLOCKED", "{\"reason\":" + quote(reason.trim()) + "}");
        return jdbc.query("""
                SELECT o.id, o.order_reference, sum(a.qty) FROM whse_order_lot_allocations a JOIN whse_outbound_orders o ON o.id = a.order_id
                 WHERE a.tenant_id = ? AND a.lot_id = ? AND a.status = 'ALLOCATED' GROUP BY o.id, o.order_reference ORDER BY o.order_reference""",
                (rs, i) -> new AffectedOrder(rs.getObject(1, UUID.class), rs.getString(2), rs.getBigDecimal(3)), t, lotId);
    }

    @Transactional
    public void unblock(TenantId tenantId, UUID lotId, String reason, UUID actor) {
        if (actor == null) throw new AccessDeniedException("Your user could not be identified, so this step cannot be recorded.");
        if (reason == null || reason.trim().length() < 5) throw new IllegalArgumentException("Give a reason for releasing the lot (at least 5 characters).");
        UUID t = tenantId.getValue();
        int n = jdbc.update("UPDATE whse_lots SET blocked = FALSE, blocked_reason = NULL, blocked_at = NULL, blocked_by = NULL WHERE id = ? AND tenant_id = ? AND blocked", lotId, t);
        if (n == 0) throw new HandyFlowException("That lot is not blocked.", HttpStatus.CONFLICT, "LOT_NOT_BLOCKED");
        audit(t, actor, lotId, "WHSE_LOT_UNBLOCKED", "{\"reason\":" + quote(reason.trim()) + "}");
    }

    private void audit(UUID tenant, UUID actor, UUID lotId, String action, String json) {
        String name = null;
        try { name = za.co.handyflow.platform.shared.TenantContext.getCurrentUserName(); } catch (RuntimeException ignored) { /* no named user */ }
        jdbc.update("INSERT INTO audit_events (tenant_id, actor_type, actor_id, actor_name, action, item_type, item_id, after_json) VALUES (?, 'STAFF', ?, ?, ?, ?, ?, ?::jsonb)",
                tenant, actor, name, action, "WHSE_LOT", lotId, json);
    }

    private static String quote(String s) { return "\"" + s.replace("\\", "\\\\").replace("\"", "\\\"") + "\""; }

    /** Switches an item's lot / expiry tracking. Refused while any stock of the item is on hand, because existing stock has no lots. */
    @Transactional
    public Tracking setTracking(TenantId tenantId, UUID itemId, boolean lotTracked, boolean expiryTracked, Integer minShelfLifeDays, UUID actor) {
        if (actor == null) throw new AccessDeniedException("Your user could not be identified, so this step cannot be recorded.");
        UUID t = tenantId.getValue();
        if (count("SELECT count(*) FROM whse_items WHERE id = ? AND tenant_id = ? AND deleted_at IS NULL", itemId, t) == 0) throw new ResourceNotFoundException("Item", itemId.toString());
        if (expiryTracked && !lotTracked) throw new IllegalArgumentException("Expiry tracking needs lot tracking.");
        if (minShelfLifeDays != null && (!expiryTracked || minShelfLifeDays < 0)) throw new IllegalArgumentException("A minimum shelf life needs expiry tracking and cannot be negative.");
        Tracking now = jdbc.query("SELECT lot_tracked, expiry_tracked, min_shelf_life_days FROM whse_items WHERE id = ?",
                (rs, i) -> new Tracking(rs.getBoolean(1), rs.getBoolean(2), (Integer) rs.getObject(3)), itemId).get(0);
        boolean structural = now.lotTracked() != lotTracked || now.expiryTracked() != expiryTracked;
        if (structural && count("SELECT count(*) FROM whse_inventory WHERE item_id = ? AND qty_on_hand > 0", itemId) > 0) {
            throw new HandyFlowException("This item has stock on hand, so lot tracking cannot be changed. Count it out first, or start tracking when stock is zero.",
                    HttpStatus.CONFLICT, "ITEM_HAS_STOCK");
        }
        jdbc.update("UPDATE whse_items SET lot_tracked = ?, expiry_tracked = ?, min_shelf_life_days = ?, updated_at = now() WHERE id = ? AND tenant_id = ?",
                lotTracked, expiryTracked, minShelfLifeDays, itemId, t);
        String name = null;
        try { name = za.co.handyflow.platform.shared.TenantContext.getCurrentUserName(); } catch (RuntimeException ignored) { /* no named user */ }
        jdbc.update("INSERT INTO audit_events (tenant_id, actor_type, actor_id, actor_name, action, item_type, item_id, after_json) VALUES (?, 'STAFF', ?, ?, ?, ?, ?, ?::jsonb)",
                t, actor, name, "WHSE_ITEM_TRACKING_CHANGED", "WHSE_ITEM", itemId,
                "{\"lotTracked\":" + lotTracked + ",\"expiryTracked\":" + expiryTracked + ",\"minShelfLifeDays\":" + minShelfLifeDays + "}");
        return new Tracking(lotTracked, expiryTracked, minShelfLifeDays);
    }

    private Tracking tracking(UUID tenant, UUID clientId, UUID itemId) {
        List<Tracking> r = jdbc.query("SELECT lot_tracked, expiry_tracked, min_shelf_life_days FROM whse_items WHERE id = ? AND tenant_id = ? AND client_id = ? AND deleted_at IS NULL",
                (rs, i) -> new Tracking(rs.getBoolean(1), rs.getBoolean(2), (Integer) rs.getObject(3)), itemId, tenant, clientId);
        if (r.isEmpty()) throw new ResourceNotFoundException("Item", itemId.toString());
        return r.get(0);
    }

    private long count(String sql, Object... args) { Long n = jdbc.queryForObject(sql, Long.class, args); return n == null ? 0 : n; }
}
