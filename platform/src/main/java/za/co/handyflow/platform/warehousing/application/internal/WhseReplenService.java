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
import java.util.List;
import java.util.UUID;

/**
 * Replenishment. A rule gives a pick face (a location) a minimum and a maximum for an item. {@link #sync} looks at every active rule: where the
 * stock available at the face (less what is already on its way in REPLEN tasks) is below the minimum, it creates tasks that move stock from the
 * reserve locations up to the maximum. Dock and staging locations are not reserve (their stock is waiting for put-away). Safe to run repeatedly.
 * The tasks are done from the task list and move the stock with an ordinary transfer.
 */
@Service
@RequiredArgsConstructor
public class WhseReplenService {

    private final JdbcTemplate jdbc;

    public record RuleRow(UUID id, UUID clientId, String clientName, UUID itemId, String sku, String itemDescription, UUID locationId, String locationCode,
                          BigDecimal minQty, BigDecimal maxQty, boolean active, BigDecimal faceAvailable, BigDecimal inbound, BigDecimal reserveAvailable, String status) {}

    @Transactional(readOnly = true)
    public List<RuleRow> rules(TenantId tenantId) {
        UUID t = tenantId.getValue();
        return jdbc.query("""
                SELECT r.id, r.client_id, c.trading_name, r.item_id, i.sku, i.description, r.location_id, l.code, r.min_qty, r.max_qty, r.active,
                       coalesce((SELECT sum(v.qty_on_hand - v.qty_allocated) FROM whse_inventory v WHERE v.tenant_id = r.tenant_id AND v.client_id = r.client_id
                                   AND v.item_id = r.item_id AND v.location_id = r.location_id AND v.status = 'AVAILABLE'), 0),
                       coalesce((SELECT sum(k.qty - k.qty_done) FROM whse_tasks k WHERE k.tenant_id = r.tenant_id AND k.task_type = 'REPLEN' AND k.item_id = r.item_id
                                   AND k.to_location_id = r.location_id AND k.status IN ('OPEN','IN_PROGRESS')), 0),
                       coalesce((SELECT sum(v.qty_on_hand - v.qty_allocated) FROM whse_inventory v JOIN whse_locations vl ON vl.id = v.location_id
                                  WHERE v.tenant_id = r.tenant_id AND v.client_id = r.client_id AND v.item_id = r.item_id AND v.location_id <> r.location_id
                                    AND v.status = 'AVAILABLE' AND vl.deleted_at IS NULL AND vl.location_type NOT IN ('DOCK','STAGING')), 0)
                  FROM whse_replen_rules r JOIN whse_clients c ON c.id = r.client_id JOIN whse_items i ON i.id = r.item_id JOIN whse_locations l ON l.id = r.location_id
                 WHERE r.tenant_id = ? ORDER BY l.code, i.sku""",
                (rs, n) -> {
                    BigDecimal min = rs.getBigDecimal(9), face = rs.getBigDecimal(12), inbound = rs.getBigDecimal(13), reserve = rs.getBigDecimal(14);
                    boolean active = rs.getBoolean(11);
                    BigDecimal uncovered = WhseReplenRules.needsReplenishment(face, inbound, min) && reserve.signum() <= 0 ? BigDecimal.ONE : BigDecimal.ZERO;
                    return new RuleRow(rs.getObject(1, UUID.class), rs.getObject(2, UUID.class), rs.getString(3), rs.getObject(4, UUID.class), rs.getString(5), rs.getString(6),
                            rs.getObject(7, UUID.class), rs.getString(8), min, rs.getBigDecimal(10), active, face, inbound, reserve,
                            active ? WhseReplenRules.status(face, inbound, min, uncovered) : "OFF");
                }, t);
    }

    /** Creates the rule, or changes the levels (and switches it on or off) when the item already has one at that location. */
    @Transactional
    public UUID saveRule(TenantId tenantId, UUID clientId, UUID itemId, UUID locationId, BigDecimal min, BigDecimal max, boolean active, UUID actor) {
        requireActor(actor);
        WhseReplenRules.checkLevels(min, max);
        UUID t = tenantId.getValue();
        Integer okItem = jdbc.queryForObject("SELECT count(*) FROM whse_items WHERE tenant_id = ? AND id = ? AND client_id = ? AND deleted_at IS NULL", Integer.class, t, itemId, clientId);
        if (okItem == null || okItem == 0) throw new ResourceNotFoundException("WhseItem", itemId.toString());
        List<String> loc = jdbc.query("SELECT location_type FROM whse_locations WHERE tenant_id = ? AND id = ? AND deleted_at IS NULL", (rs, n) -> rs.getString(1), t, locationId);
        if (loc.isEmpty()) throw new ResourceNotFoundException("WhseLocation", locationId.toString());
        if ("DOCK".equals(loc.get(0)) || "STAGING".equals(loc.get(0))) throw new IllegalArgumentException("A dock or staging location cannot be a pick face.");
        List<UUID> existing = jdbc.query("SELECT id FROM whse_replen_rules WHERE tenant_id = ? AND client_id = ? AND item_id = ? AND location_id = ?",
                (rs, n) -> rs.getObject(1, UUID.class), t, clientId, itemId, locationId);
        UUID id;
        if (existing.isEmpty()) {
            id = UUID.randomUUID();
            try {
                jdbc.update("INSERT INTO whse_replen_rules (id, tenant_id, client_id, item_id, location_id, min_qty, max_qty, active, created_by) VALUES (?,?,?,?,?,?,?,?,?)",
                        id, t, clientId, itemId, locationId, min, max, active, actor);
            } catch (DuplicateKeyException e) {
                throw new IllegalStateException("That item already has a rule at this location.");
            }
        } else {
            id = existing.get(0);
            jdbc.update("UPDATE whse_replen_rules SET min_qty = ?, max_qty = ?, active = ?, updated_at = now() WHERE id = ?", min, max, active, id);
        }
        audit(t, actor, "WHSE_REPLEN_RULE_SAVED", "{\"item\":\"" + itemId + "\",\"location\":\"" + locationId + "\",\"min\":" + min.toPlainString() + ",\"max\":" + max.toPlainString() + ",\"active\":" + active + "}");
        return id;
    }

    @Transactional
    public void deleteRule(TenantId tenantId, UUID id, UUID actor) {
        requireActor(actor);
        int n = jdbc.update("DELETE FROM whse_replen_rules WHERE tenant_id = ? AND id = ?", tenantId.getValue(), id);
        if (n == 0) throw new ResourceNotFoundException("WhseReplenRule", id.toString());
        audit(tenantId.getValue(), actor, "WHSE_REPLEN_RULE_DELETED", "{\"rule\":\"" + id + "\"}");
    }

    /** Creates replenishment tasks for every face below its minimum. Returns how many tasks were created. */
    @Transactional
    public int sync(TenantId tenantId) {
        UUID t = tenantId.getValue();
        int created = 0;
        for (RuleRow r : rules(tenantId)) {
            if (!r.active() || !WhseReplenRules.needsReplenishment(r.faceAvailable(), r.inbound(), r.minQty())) continue;
            List<WhseReplenRules.Source> sources = jdbc.query("""
                    SELECT v.location_id, v.lot_id, lot.expiry_date, loc.pick_sequence, v.qty_on_hand - v.qty_allocated
                      FROM whse_inventory v JOIN whse_locations loc ON loc.id = v.location_id LEFT JOIN whse_lots lot ON lot.id = v.lot_id
                     WHERE v.tenant_id = ? AND v.client_id = ? AND v.item_id = ? AND v.location_id <> ? AND v.status = 'AVAILABLE'
                       AND loc.deleted_at IS NULL AND loc.location_type NOT IN ('DOCK','STAGING') AND v.qty_on_hand - v.qty_allocated > 0
                       AND (lot.id IS NULL OR (NOT lot.blocked AND (lot.expiry_date IS NULL OR lot.expiry_date >= current_date)))""",
                    (rs, n) -> new WhseReplenRules.Source(rs.getObject(1, UUID.class), rs.getObject(2, UUID.class),
                            rs.getDate(3) == null ? null : rs.getDate(3).toLocalDate(), (Integer) rs.getObject(4), rs.getBigDecimal(5)),
                    t, r.clientId(), r.itemId(), r.locationId());
            WhseReplenRules.Plan plan = WhseReplenRules.plan(r.faceAvailable(), r.inbound(), r.minQty(), r.maxQty(), sources);
            for (WhseReplenRules.Move m : plan.moves()) {
                created += jdbc.update("""
                        INSERT INTO whse_tasks (id, tenant_id, task_type, priority, client_id, item_id, lot_id, from_location_id, to_location_id, qty, pick_sequence,
                                                reference_type, note)
                        VALUES (?, ?, 'REPLEN', 4, ?, ?, ?, ?, ?, ?, (SELECT pick_sequence FROM whse_locations WHERE id = ?), 'REPLEN', NULL)
                        ON CONFLICT DO NOTHING""",
                        UUID.randomUUID(), t, r.clientId(), r.itemId(), m.source().lotId(), m.source().locationId(), r.locationId(), m.qty(), m.source().locationId());
            }
        }
        return created;
    }

    private void audit(UUID t, UUID actor, String action, String json) {
        jdbc.update("INSERT INTO audit_events (tenant_id, actor_type, actor_id, action, item_type, after_json) VALUES (?, 'STAFF', ?, ?, 'WHSE_REPLEN_RULE', ?::jsonb)", t, actor, action, json);
    }

    private static void requireActor(UUID actor) {
        if (actor == null) throw new AccessDeniedException("Your user could not be identified, so this step cannot be recorded.");
    }
}
