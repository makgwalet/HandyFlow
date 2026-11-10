package za.co.handyflow.platform.warehousing.application.internal;

import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import za.co.handyflow.platform.shared.TenantId;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.List;
import java.util.UUID;

/**
 * Allocation and shipping for outbound lines of lot-tracked items. Each line is allocated first-expiry-first-out across lots, each piece is
 * recorded, and shipping posts one PICK movement per lot so the ledger shows exactly which batches left.
 */
@Service
@RequiredArgsConstructor
public class WhseLotAllocationService {

    private static final String REF_TYPE = "OUTBOUND_ORDER";

    private final JdbcTemplate jdbc;
    private final WhseInventoryService inventoryService;
    @Value("${handyflow.warehousing.billing-zone:Africa/Johannesburg}")
    private String zoneId;

    public record AllocationRow(UUID id, UUID orderLineId, UUID inventoryId, UUID lotId, String lotNumber, java.time.LocalDate expiryDate,
                                UUID locationId, String locationCode, BigDecimal qty, String status) {}

    @Transactional(readOnly = true)
    public boolean isLotTracked(UUID tenant, UUID itemId) {
        Boolean b = jdbc.queryForObject("SELECT lot_tracked FROM whse_items WHERE id = ? AND tenant_id = ?", Boolean.class, itemId, tenant);
        return Boolean.TRUE.equals(b);
    }

    /** Allocates one line first-expiry-first-out. Returns the location of the first piece, kept on the line for display. */
    @Transactional
    public UUID allocate(TenantId tenantId, UUID orderId, String orderRef, UUID lineId, UUID clientId, UUID itemId, BigDecimal qty, UUID user) {
        UUID t = tenantId.getValue();
        Integer minShelf = jdbc.queryForObject("SELECT min_shelf_life_days FROM whse_items WHERE id = ? AND tenant_id = ?", Integer.class, itemId, t);
        List<WhseFefo.Candidate> candidates = jdbc.query("""
                SELECT i.id, l.id AS lot_id, l.lot_number, i.location_id, l.expiry_date, l.blocked, i.qty_on_hand - i.qty_allocated AS available
                  FROM whse_inventory i JOIN whse_lots l ON l.id = i.lot_id
                 WHERE i.tenant_id = ? AND i.client_id = ? AND i.item_id = ? AND i.status = 'AVAILABLE' AND i.qty_on_hand - i.qty_allocated > 0""",
                (rs, n) -> new WhseFefo.Candidate(rs.getObject(1, UUID.class), rs.getObject(2, UUID.class), rs.getString(3), rs.getObject(4, UUID.class),
                        rs.getDate(5) == null ? null : rs.getDate(5).toLocalDate(), rs.getBoolean(6), rs.getBigDecimal(7)),
                t, clientId, itemId);
        List<WhseFefo.Pick> picks;
        try {
            picks = WhseFefo.plan(candidates, qty, LocalDate.now(ZoneId.of(zoneId)), minShelf);
        } catch (IllegalStateException e) {
            throw new IllegalStateException("Item " + itemId + ": " + e.getMessage());
        }
        for (WhseFefo.Pick p : picks) {
            inventoryService.allocateById(tenantId, p.source().inventoryId(), p.qty(), REF_TYPE, orderId, orderRef, user);
            jdbc.update("INSERT INTO whse_order_lot_allocations (id, tenant_id, order_id, order_line_id, inventory_id, lot_id, location_id, qty) VALUES (?,?,?,?,?,?,?,?)",
                    UUID.randomUUID(), t, orderId, lineId, p.source().inventoryId(), p.source().lotId(), p.source().locationId(), p.qty());
        }
        return picks.get(0).source().locationId();
    }

    /**
     * Ships every open lot allocation of the order: one PICK movement per lot. Refuses a blocked lot or one that has expired since it was
     * allocated; the order then needs its lots re-allocated.
     */
    @Transactional
    public void fulfil(TenantId tenantId, UUID orderId, String orderRef, UUID user) {
        UUID t = tenantId.getValue();
        List<Object[]> rows = jdbc.query("""
                SELECT a.id, a.inventory_id, a.qty, l.lot_number, l.blocked, l.expiry_date
                  FROM whse_order_lot_allocations a JOIN whse_lots l ON l.id = a.lot_id
                 WHERE a.tenant_id = ? AND a.order_id = ? AND a.status = 'ALLOCATED' ORDER BY a.created_at, a.id""",
                (rs, n) -> new Object[]{rs.getObject(1, UUID.class), rs.getObject(2, UUID.class), rs.getBigDecimal(3), rs.getString(4), rs.getBoolean(5),
                        rs.getDate(6) == null ? null : rs.getDate(6).toLocalDate()}, t, orderId);
        LocalDate today = today();
        for (Object[] r : rows) {
            if ((Boolean) r[4]) throw new IllegalStateException("Lot " + r[3] + " is blocked and cannot be shipped. Re-allocate the order's lots.");
            LocalDate exp = (LocalDate) r[5];
            if (exp != null && exp.isBefore(today)) throw new IllegalStateException("Lot " + r[3] + " expired on " + exp + " and cannot be shipped. Re-allocate the order's lots.");
        }
        for (Object[] r : rows) {
            inventoryService.fulfillById(tenantId, (UUID) r[1], (BigDecimal) r[2], REF_TYPE, orderId, orderRef, user);
            jdbc.update("UPDATE whse_order_lot_allocations SET status = 'SHIPPED', closed_at = now() WHERE id = ?", (UUID) r[0]);
        }
    }

    /** Gives back every open lot allocation of the order, for a cancellation or a re-allocation. */
    @Transactional
    public void release(TenantId tenantId, UUID orderId, String orderRef, UUID user) {
        UUID t = tenantId.getValue();
        List<Object[]> rows = jdbc.query("SELECT id, inventory_id, qty FROM whse_order_lot_allocations WHERE tenant_id = ? AND order_id = ? AND status = 'ALLOCATED' ORDER BY created_at, id",
                (rs, n) -> new Object[]{rs.getObject(1, UUID.class), rs.getObject(2, UUID.class), rs.getBigDecimal(3)}, t, orderId);
        for (Object[] r : rows) {
            inventoryService.deallocateById(tenantId, (UUID) r[1], (BigDecimal) r[2], REF_TYPE, orderId, orderRef, user);
            jdbc.update("UPDATE whse_order_lot_allocations SET status = 'RELEASED', closed_at = now() WHERE id = ?", (UUID) r[0]);
        }
    }

    @Transactional(readOnly = true)
    public List<AllocationRow> forOrder(TenantId tenantId, UUID orderId) {
        return jdbc.query("""
                SELECT a.id, a.order_line_id, a.inventory_id, a.lot_id, l.lot_number, l.expiry_date, a.location_id, loc.code, a.qty, a.status
                  FROM whse_order_lot_allocations a JOIN whse_lots l ON l.id = a.lot_id JOIN whse_locations loc ON loc.id = a.location_id
                 WHERE a.tenant_id = ? AND a.order_id = ? ORDER BY a.created_at, a.id""",
                (rs, n) -> new AllocationRow(rs.getObject(1, UUID.class), rs.getObject(2, UUID.class), rs.getObject(3, UUID.class), rs.getObject(4, UUID.class),
                        rs.getString(5), rs.getDate(6) == null ? null : rs.getDate(6).toLocalDate(), rs.getObject(7, UUID.class), rs.getString(8),
                        rs.getBigDecimal(9), rs.getString(10)),
                tenantId.getValue(), orderId);
    }

    LocalDate today() { return LocalDate.now(ZoneId.of(zoneId)); }
}
