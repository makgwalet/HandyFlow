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
        return allocate(tenantId, orderId, orderRef, lineId, clientId, itemId, qty, user, java.util.Set.of());
    }

    /** As above, never taking from the given positions (inventory ids), for stock that could not be found where it was expected. */
    @Transactional
    public UUID allocate(TenantId tenantId, UUID orderId, String orderRef, UUID lineId, UUID clientId, UUID itemId, BigDecimal qty, UUID user,
                         java.util.Set<UUID> excludeInventoryIds) {
        List<WhseFefo.Candidate> candidates = candidates(tenantId.getValue(), clientId, itemId, excludeInventoryIds);
        List<WhseFefo.Pick> picks;
        try {
            picks = WhseFefo.plan(candidates, qty, LocalDate.now(ZoneId.of(zoneId)), minShelfLife(tenantId.getValue(), itemId));
        } catch (IllegalStateException e) {
            throw new IllegalStateException("Item " + itemId + ": " + e.getMessage());
        }
        record(tenantId, orderId, orderRef, lineId, user, picks);
        return picks.get(0).source().locationId();
    }

    /** First expiry first like {@link #allocate}, but takes what is sellable and reports the rest as unallocated instead of failing. */
    @Transactional
    public Split allocateUpTo(TenantId tenantId, UUID orderId, String orderRef, UUID lineId, UUID clientId, UUID itemId, BigDecimal qty, UUID user) {
        List<WhseFefo.Pick> picks = WhseFefo.planUpTo(candidates(tenantId.getValue(), clientId, itemId, java.util.Set.of()), qty,
                LocalDate.now(ZoneId.of(zoneId)), minShelfLife(tenantId.getValue(), itemId));
        if (picks.isEmpty()) return new Split(null, BigDecimal.ZERO, qty);
        record(tenantId, orderId, orderRef, lineId, user, picks);
        BigDecimal got = picks.stream().map(WhseFefo.Pick::qty).reduce(BigDecimal.ZERO, BigDecimal::add);
        return new Split(picks.get(0).source().locationId(), got, qty.subtract(got));
    }

    private Integer minShelfLife(UUID t, UUID itemId) {
        return jdbc.queryForObject("SELECT min_shelf_life_days FROM whse_items WHERE id = ? AND tenant_id = ?", Integer.class, itemId, t);
    }

    private List<WhseFefo.Candidate> candidates(UUID t, UUID clientId, UUID itemId, java.util.Set<UUID> excludeInventoryIds) {
        List<WhseFefo.Candidate> candidates = jdbc.query("""
                SELECT i.id, l.id AS lot_id, l.lot_number, i.location_id, l.expiry_date, l.blocked, i.qty_on_hand - i.qty_allocated AS available
                  FROM whse_inventory i JOIN whse_lots l ON l.id = i.lot_id
                 WHERE i.tenant_id = ? AND i.client_id = ? AND i.item_id = ? AND i.status = 'AVAILABLE' AND i.qty_on_hand - i.qty_allocated > 0""",
                (rs, n) -> new WhseFefo.Candidate(rs.getObject(1, UUID.class), rs.getObject(2, UUID.class), rs.getString(3), rs.getObject(4, UUID.class),
                        rs.getDate(5) == null ? null : rs.getDate(5).toLocalDate(), rs.getBoolean(6), rs.getBigDecimal(7)),
                t, clientId, itemId);
        return excludeInventoryIds.isEmpty() ? candidates : candidates.stream().filter(c -> !excludeInventoryIds.contains(c.inventoryId())).toList();
    }

    private void record(TenantId tenantId, UUID orderId, String orderRef, UUID lineId, UUID user, List<WhseFefo.Pick> picks) {
        for (WhseFefo.Pick p : picks) {
            inventoryService.allocateById(tenantId, p.source().inventoryId(), p.qty(), REF_TYPE, orderId, orderRef, user);
            jdbc.update("INSERT INTO whse_order_lot_allocations (id, tenant_id, order_id, order_line_id, inventory_id, lot_id, location_id, qty) VALUES (?,?,?,?,?,?,?,?)",
                    UUID.randomUUID(), tenantId.getValue(), orderId, lineId, p.source().inventoryId(), p.source().lotId(), p.source().locationId(), p.qty());
        }
    }

    public record Split(UUID firstLocationId, BigDecimal allocated, BigDecimal unallocated) {}

    /** True when the line has allocation rows of its own (a split line); such a line ships and cancels through them, not through its single location. */
    @Transactional(readOnly = true)
    public boolean hasAllocations(UUID tenant, UUID lineId) {
        Integer n = jdbc.queryForObject("SELECT count(*) FROM whse_order_lot_allocations WHERE tenant_id = ? AND order_line_id = ?", Integer.class, tenant, lineId);
        return n != null && n > 0;
    }

    /**
     * Allocates a line of an item that is not lot tracked across the locations that hold it (see {@link WhseSplitPlan}), never taking from the
     * excluded positions. With {@code allowPartial} false, stock that cannot cover the quantity is an error and nothing is allocated; with it true,
     * what is there is allocated and the rest is returned as unallocated.
     */
    @Transactional
    public Split allocateSplit(TenantId tenantId, UUID orderId, String orderRef, UUID lineId, UUID clientId, UUID itemId, BigDecimal qty, UUID user,
                               boolean allowPartial, java.util.Set<UUID> excludeInventoryIds) {
        UUID t = tenantId.getValue();
        List<WhseSplitPlan.Source> sources = jdbc.query("""
                SELECT i.id, i.location_id, loc.pick_sequence, i.qty_on_hand - i.qty_allocated
                  FROM whse_inventory i JOIN whse_locations loc ON loc.id = i.location_id
                 WHERE i.tenant_id = ? AND i.client_id = ? AND i.item_id = ? AND i.lot_id IS NULL AND i.status = 'AVAILABLE'
                   AND loc.deleted_at IS NULL AND i.qty_on_hand - i.qty_allocated > 0""",
                (rs, n) -> new WhseSplitPlan.Source(rs.getObject(1, UUID.class), rs.getObject(2, UUID.class), (Integer) rs.getObject(3), rs.getBigDecimal(4)),
                t, clientId, itemId);
        sources = sources.stream().filter(s -> !excludeInventoryIds.contains(s.inventoryId())).toList();
        WhseSplitPlan.Result plan = WhseSplitPlan.plan(sources, qty);
        if (plan.unallocated().signum() > 0 && (!allowPartial || plan.pieces().isEmpty())) {
            if (!allowPartial) {
                throw new IllegalStateException("Insufficient available stock for item " + itemId + " — required " + qty.stripTrailingZeros().toPlainString()
                        + ", only " + plan.allocated().stripTrailingZeros().toPlainString() + " available across all locations");
            }
            return new Split(null, BigDecimal.ZERO, qty);
        }
        for (WhseSplitPlan.Piece p : plan.pieces()) {
            inventoryService.allocateById(tenantId, p.source().inventoryId(), p.qty(), REF_TYPE, orderId, orderRef, user);
            jdbc.update("INSERT INTO whse_order_lot_allocations (id, tenant_id, order_id, order_line_id, inventory_id, lot_id, location_id, qty) VALUES (?,?,?,?,?,NULL,?,?)",
                    UUID.randomUUID(), t, orderId, lineId, p.source().inventoryId(), p.source().locationId(), p.qty());
        }
        return new Split(plan.pieces().get(0).source().locationId(), plan.allocated(), plan.unallocated());
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
                  FROM whse_order_lot_allocations a LEFT JOIN whse_lots l ON l.id = a.lot_id
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
                  FROM whse_order_lot_allocations a LEFT JOIN whse_lots l ON l.id = a.lot_id JOIN whse_locations loc ON loc.id = a.location_id
                 WHERE a.tenant_id = ? AND a.order_id = ? ORDER BY a.created_at, a.id""",
                (rs, n) -> new AllocationRow(rs.getObject(1, UUID.class), rs.getObject(2, UUID.class), rs.getObject(3, UUID.class), rs.getObject(4, UUID.class),
                        rs.getString(5), rs.getDate(6) == null ? null : rs.getDate(6).toLocalDate(), rs.getObject(7, UUID.class), rs.getString(8),
                        rs.getBigDecimal(9), rs.getString(10)),
                tenantId.getValue(), orderId);
    }

    LocalDate today() { return LocalDate.now(ZoneId.of(zoneId)); }
}
