package za.co.handyflow.platform.warehousing.application.internal;

import lombok.RequiredArgsConstructor;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import za.co.handyflow.platform.shared.ResourceNotFoundException;
import za.co.handyflow.platform.shared.TenantId;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

/**
 * Slotting suggestions from what was actually picked (done pick tasks over a period), and the MOVE tasks that carry them out. A suggestion is only
 * ever a move into an empty storage location, see {@link WhseSlottingRules}. Nothing moves until someone does the task.
 */
@Service
@RequiredArgsConstructor
public class WhseSlottingService {

    public static final int DEFAULT_DAYS = 90;
    public static final int DEFAULT_MIN_GAIN = 5;

    private final JdbcTemplate jdbc;

    public record Suggestion(UUID clientId, String clientName, UUID itemId, String sku, String description, String abc, BigDecimal picked,
                             UUID fromLocationId, String fromCode, UUID toLocationId, String toCode, BigDecimal qty, int improvement) {}
    public record Overview(int days, int itemsWithStock, int classA, int classB, int classC, List<Suggestion> suggestions) {}

    @Transactional(readOnly = true)
    public Overview overview(TenantId tenantId, int days, int minGain, int limit) {
        if (days < 7 || days > 730) throw new IllegalArgumentException("Look back between 7 and 730 days.");
        if (minGain < 1 || minGain > 10_000) throw new IllegalArgumentException("The improvement must be at least 1 place in the pick walk.");
        UUID t = tenantId.getValue();
        Map<UUID, BigDecimal> picked = new HashMap<>();
        jdbc.query("SELECT item_id, sum(qty_done) FROM whse_tasks WHERE tenant_id = ? AND task_type = 'PICK' AND status IN ('DONE','SHORT') AND completed_at >= now() - (? * interval '1 day') GROUP BY item_id",
                rs -> { picked.put(rs.getObject(1, UUID.class), rs.getBigDecimal(2)); }, t, days);

        // Positions of available stock in storage locations; an item is only moved from a location where it sits as one position.
        List<WhseSlottingRules.Placed> rows = jdbc.query("""
                SELECT i.client_id, i.item_id, i.lot_id, i.location_id, loc.code, loc.pick_sequence, i.qty_on_hand - i.qty_allocated
                  FROM whse_inventory i JOIN whse_locations loc ON loc.id = i.location_id
                 WHERE i.tenant_id = ? AND i.status = 'AVAILABLE' AND i.qty_on_hand - i.qty_allocated > 0 AND loc.deleted_at IS NULL AND loc.active = true
                   AND loc.location_type IN ('BIN','SHELF','PALLET','FLOOR')""",
                (rs, n) -> new WhseSlottingRules.Placed(rs.getObject(1, UUID.class), rs.getObject(2, UUID.class), rs.getObject(3, UUID.class), rs.getObject(4, UUID.class),
                        rs.getString(5), (Integer) rs.getObject(6), rs.getBigDecimal(7), BigDecimal.ZERO, "C"), t);
        Map<String, Integer> perPlace = new HashMap<>();
        for (WhseSlottingRules.Placed p : rows) perPlace.merge(p.itemId() + "/" + p.locationId(), 1, Integer::sum);
        // An item's main place is where it has the most available stock.
        Map<UUID, WhseSlottingRules.Placed> main = new LinkedHashMap<>();
        for (WhseSlottingRules.Placed p : rows) {
            WhseSlottingRules.Placed cur = main.get(p.itemId());
            if (cur == null || p.available().compareTo(cur.available()) > 0) main.put(p.itemId(), p);
        }
        Set<UUID> items = main.keySet();
        Map<UUID, String> abc = WhseSlottingRules.classify(picked, items);
        List<WhseSlottingRules.Placed> placed = new ArrayList<>();
        for (WhseSlottingRules.Placed p : main.values()) {
            if (perPlace.get(p.itemId() + "/" + p.locationId()) != 1) continue;
            placed.add(new WhseSlottingRules.Placed(p.clientId(), p.itemId(), p.lotId(), p.locationId(), p.locationCode(), p.pickSequence(), p.available(),
                    picked.getOrDefault(p.itemId(), BigDecimal.ZERO), abc.get(p.itemId())));
        }
        List<WhseSlottingRules.Empty> empties = jdbc.query("""
                SELECT loc.id, loc.code, loc.pick_sequence, loc.capacity_units
                  FROM whse_locations loc
                 WHERE loc.tenant_id = ? AND loc.deleted_at IS NULL AND loc.active = true AND loc.location_type IN ('BIN','SHELF','PALLET','FLOOR') AND loc.pick_sequence IS NOT NULL
                   AND NOT EXISTS (SELECT 1 FROM whse_inventory v WHERE v.location_id = loc.id AND v.qty_on_hand > 0)
                   AND NOT EXISTS (SELECT 1 FROM whse_tasks k WHERE k.to_location_id = loc.id AND k.status IN ('OPEN','IN_PROGRESS'))""",
                (rs, n) -> new WhseSlottingRules.Empty(rs.getObject(1, UUID.class), rs.getString(2), (Integer) rs.getObject(3), rs.getBigDecimal(4)), t);

        Map<UUID, String[]> names = new HashMap<>();
        jdbc.query("SELECT i.id, i.sku, i.description, c.trading_name FROM whse_items i JOIN whse_clients c ON c.id = i.client_id WHERE i.tenant_id = ?",
                rs -> { names.put(rs.getObject(1, UUID.class), new String[]{rs.getString(2), rs.getString(3), rs.getString(4)}); }, t);
        List<Suggestion> out = new ArrayList<>();
        for (WhseSlottingRules.Move m : WhseSlottingRules.plan(placed, empties, minGain)) {
            if (out.size() >= Math.max(1, Math.min(limit, 200))) break;
            String[] nm = names.getOrDefault(m.from().itemId(), new String[]{"?", "", ""});
            out.add(new Suggestion(m.from().clientId(), nm[2], m.from().itemId(), nm[0], nm[1], m.from().abc(), m.from().picked(), m.from().locationId(), m.from().locationCode(),
                    m.to().locationId(), m.to().code(), m.from().available(), m.improvement()));
        }
        int a = 0, b = 0, c = 0;
        for (String k : abc.values()) { if ("A".equals(k)) a++; else if ("B".equals(k)) b++; else c++; }
        return new Overview(days, items.size(), a, b, c, out);
    }

    /** Creates the MOVE task for a suggestion, after checking again that it still holds: one available position at the source, an empty storage destination. */
    @Transactional
    public UUID createMove(TenantId tenantId, UUID clientId, UUID itemId, UUID fromLocationId, UUID toLocationId, UUID actor) {
        if (actor == null) throw new AccessDeniedException("Your user could not be identified, so this step cannot be recorded.");
        UUID t = tenantId.getValue();
        if (fromLocationId.equals(toLocationId)) throw new IllegalArgumentException("The stock is already at that location.");
        List<Object[]> from = jdbc.query("SELECT lot_id, qty_on_hand - qty_allocated FROM whse_inventory WHERE tenant_id = ? AND client_id = ? AND item_id = ? AND location_id = ? AND status = 'AVAILABLE' AND qty_on_hand - qty_allocated > 0 FOR UPDATE",
                (rs, n) -> new Object[]{rs.getObject(1, UUID.class), rs.getBigDecimal(2)}, t, clientId, itemId, fromLocationId);
        if (from.isEmpty()) throw new IllegalStateException("There is no available stock of that item at the source location any more.");
        if (from.size() > 1) throw new IllegalStateException("That item sits there as several positions (lots), so it cannot be moved as one.");
        List<Object[]> dest = jdbc.query("SELECT location_type, capacity_units, active FROM whse_locations WHERE tenant_id = ? AND id = ? AND deleted_at IS NULL FOR UPDATE",
                (rs, n) -> new Object[]{rs.getString(1), rs.getBigDecimal(2), rs.getBoolean(3)}, t, toLocationId);
        if (dest.isEmpty()) throw new ResourceNotFoundException("WhseLocation", toLocationId.toString());
        if (!(Boolean) dest.get(0)[2] || !WhsePutawayRules.STORAGE_TYPES.contains((String) dest.get(0)[0])) throw new IllegalArgumentException("The destination is not an active storage location.");
        BigDecimal qty = (BigDecimal) from.get(0)[1];
        BigDecimal cap = (BigDecimal) dest.get(0)[1];
        if (cap != null && cap.compareTo(qty) < 0) throw new IllegalArgumentException("The destination has room for " + cap.stripTrailingZeros().toPlainString() + ", not " + qty.stripTrailingZeros().toPlainString() + ".");
        Integer busy = jdbc.queryForObject("SELECT (SELECT count(*) FROM whse_inventory WHERE location_id = ? AND qty_on_hand > 0) + (SELECT count(*) FROM whse_tasks WHERE to_location_id = ? AND status IN ('OPEN','IN_PROGRESS'))",
                Integer.class, toLocationId, toLocationId);
        if (busy != null && busy > 0) throw new IllegalStateException("The destination is no longer empty.");
        UUID id = UUID.randomUUID();
        try {
            jdbc.update("INSERT INTO whse_tasks (id, tenant_id, task_type, priority, client_id, item_id, lot_id, from_location_id, to_location_id, qty, pick_sequence, reference_type) "
                            + "VALUES (?, ?, 'MOVE', 6, ?, ?, ?, ?, ?, ?, (SELECT pick_sequence FROM whse_locations WHERE id = ?), 'SLOTTING')",
                    id, t, clientId, itemId, from.get(0)[0], fromLocationId, toLocationId, qty, fromLocationId);
        } catch (DuplicateKeyException e) {
            throw new IllegalStateException("That move is already waiting to be done.");
        }
        jdbc.update("INSERT INTO audit_events (tenant_id, actor_type, actor_id, action, item_type, after_json) VALUES (?, 'STAFF', ?, 'WHSE_SLOTTING_MOVE_CREATED', 'WHSE_TASK', ?::jsonb)",
                t, actor, "{\"task\":\"" + id + "\",\"item\":\"" + itemId + "\",\"from\":\"" + fromLocationId + "\",\"to\":\"" + toLocationId + "\"}");
        return id;
    }
}
