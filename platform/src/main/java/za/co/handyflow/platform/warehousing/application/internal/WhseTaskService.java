package za.co.handyflow.platform.warehousing.application.internal;

import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import za.co.handyflow.platform.shared.HandyFlowException;
import za.co.handyflow.platform.shared.ResourceNotFoundException;
import za.co.handyflow.platform.shared.TenantId;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

/**
 * The warehouse task engine: pick tasks (one stop on a pick walk, from an order's allocations) and put-away tasks (received stock from a dock
 * or staging location to a storage location). A person claims a task, does it and completes it. Picking does not move stock (the allocation
 * already reserved it and shipping posts the movement); put-away moves it, with the ledger rows of a transfer.
 */
@Service
@RequiredArgsConstructor
public class WhseTaskService {

    static final String PICK = "PICK";
    static final String PUTAWAY = "PUTAWAY";
    private static final String ORDER = "OUTBOUND_ORDER";
    private static final String TASK = "WHSE_TASK";

    private final JdbcTemplate jdbc;
    private final WhseInventoryService inventoryService;

    public record TaskRow(UUID id, String taskType, String status, int priority, UUID clientId, String clientName, UUID itemId, String sku, String itemDescription,
                          UUID lotId, String lotNumber, LocalDate lotExpiry, UUID fromLocationId, String fromCode, UUID toLocationId, String toCode,
                          BigDecimal qty, BigDecimal qtyDone, Integer pickSequence, UUID referenceId, String referenceNumber, UUID assignedTo, String note) {}

    public record Summary(int openPicks, int inProgressPicks, int shortPicks, int openPutaways, int inProgressPutaways) {}

    private static final String SELECT = """
            SELECT t.id, t.task_type, t.status, t.priority, t.client_id, c.trading_name, t.item_id, it.sku, it.description, t.lot_id, lot.lot_number, lot.expiry_date,
                   t.from_location_id, fl.code, t.to_location_id, tl.code, t.qty, t.qty_done, t.pick_sequence, t.reference_id, t.reference_number, t.assigned_to, t.note
              FROM whse_tasks t
              JOIN whse_clients c ON c.id = t.client_id
              JOIN whse_items it ON it.id = t.item_id
              JOIN whse_locations fl ON fl.id = t.from_location_id
              LEFT JOIN whse_locations tl ON tl.id = t.to_location_id
              LEFT JOIN whse_lots lot ON lot.id = t.lot_id
            """;

    private static TaskRow row(java.sql.ResultSet rs, int n) throws java.sql.SQLException {
        return new TaskRow(rs.getObject(1, UUID.class), rs.getString(2), rs.getString(3), rs.getInt(4), rs.getObject(5, UUID.class), rs.getString(6),
                rs.getObject(7, UUID.class), rs.getString(8), rs.getString(9), rs.getObject(10, UUID.class), rs.getString(11),
                rs.getDate(12) == null ? null : rs.getDate(12).toLocalDate(), rs.getObject(13, UUID.class), rs.getString(14), rs.getObject(15, UUID.class),
                rs.getString(16), rs.getBigDecimal(17), rs.getBigDecimal(18), (Integer) rs.getObject(19), rs.getObject(20, UUID.class), rs.getString(21),
                rs.getObject(22, UUID.class), rs.getString(23));
    }

    // ── picking ─────────────────────────────────────────────────────────────────────────────────────────────────

    /**
     * Creates the pick tasks of an order that has just been allocated: one per lot allocation and one per allocated line that is not
     * lot tracked. Does nothing when the order already has live pick tasks. An order due today or earlier gets a higher priority.
     * The caller must have flushed the order lines (the lines' locations are read here).
     */
    @Transactional
    public int createPickTasks(TenantId tenantId, UUID orderId) {
        UUID t = tenantId.getValue();
        Integer live = jdbc.queryForObject("SELECT count(*) FROM whse_tasks WHERE tenant_id = ? AND task_type = 'PICK' AND reference_id = ? AND status <> 'CANCELLED'", Integer.class, t, orderId);
        if (live != null && live > 0) return 0;
        String cols = """
                INSERT INTO whse_tasks (id, tenant_id, task_type, priority, client_id, item_id, lot_id, from_location_id, qty, pick_sequence,
                                        reference_type, reference_id, reference_number, order_line_id)
                """;
        String prio = "CASE WHEN o.requested_ship_date IS NOT NULL AND o.requested_ship_date <= current_date THEN 2 ELSE 5 END";
        int n = jdbc.update(cols + "SELECT gen_random_uuid(), a.tenant_id, 'PICK', " + prio + ", o.client_id, ol.item_id, a.lot_id, a.location_id, a.qty, loc.pick_sequence, "
                + "'OUTBOUND_ORDER', o.id, o.order_reference, ol.id "
                + "FROM whse_order_lot_allocations a JOIN whse_outbound_orders o ON o.id = a.order_id JOIN whse_outbound_order_lines ol ON ol.id = a.order_line_id "
                + "JOIN whse_locations loc ON loc.id = a.location_id WHERE a.tenant_id = ? AND a.order_id = ? AND a.status = 'ALLOCATED'", t, orderId);
        n += jdbc.update(cols + "SELECT gen_random_uuid(), ol.tenant_id, 'PICK', " + prio + ", o.client_id, ol.item_id, NULL, ol.location_id, ol.qty_ordered, loc.pick_sequence, "
                + "'OUTBOUND_ORDER', o.id, o.order_reference, ol.id "
                + "FROM whse_outbound_order_lines ol JOIN whse_outbound_orders o ON o.id = ol.order_id JOIN whse_locations loc ON loc.id = ol.location_id "
                + "WHERE ol.tenant_id = ? AND ol.order_id = ? AND ol.location_id IS NOT NULL "
                + "AND NOT EXISTS (SELECT 1 FROM whse_order_lot_allocations a WHERE a.order_line_id = ol.id)", t, orderId);
        return n;
    }

    /** Cancels the order's unfinished pick tasks (open, claimed or short), for a cancellation or a re-allocation. */
    @Transactional
    public int cancelPickTasks(TenantId tenantId, UUID orderId, String reason) {
        return jdbc.update("UPDATE whse_tasks SET status = 'CANCELLED', note = ?, updated_at = now() WHERE tenant_id = ? AND task_type = 'PICK' AND reference_id = ? AND status IN ('OPEN','IN_PROGRESS','SHORT')",
                reason, tenantId.getValue(), orderId);
    }

    /** Pick tasks of the order that are not finished: open, claimed, or short. An order with none (or no tasks at all) may be packed and shipped. */
    @Transactional(readOnly = true)
    public int unfinishedPickTasks(TenantId tenantId, UUID orderId) {
        Integer n = jdbc.queryForObject("SELECT count(*) FROM whse_tasks WHERE tenant_id = ? AND task_type = 'PICK' AND reference_id = ? AND status IN ('OPEN','IN_PROGRESS','SHORT')",
                Integer.class, tenantId.getValue(), orderId);
        return n == null ? 0 : n;
    }

    // ── put-away ────────────────────────────────────────────────────────────────────────────────────────────────

    /**
     * Looks at the stock sitting in dock and staging locations and creates put-away tasks for what is available and not yet covered by an
     * open task, with a suggested destination. Safe to run repeatedly.
     */
    @Transactional
    public int syncPutaway(TenantId tenantId) {
        UUID t = tenantId.getValue();
        List<Object[]> need = jdbc.query("""
                SELECT i.client_id, i.item_id, i.lot_id, i.location_id, i.qty_on_hand - i.qty_allocated
                         - coalesce((SELECT sum(k.qty) FROM whse_tasks k WHERE k.tenant_id = i.tenant_id AND k.task_type = 'PUTAWAY' AND k.status IN ('OPEN','IN_PROGRESS')
                                       AND k.client_id = i.client_id AND k.item_id = i.item_id AND k.from_location_id = i.location_id
                                       AND k.lot_id IS NOT DISTINCT FROM i.lot_id), 0) AS uncovered
                  FROM whse_inventory i JOIN whse_locations loc ON loc.id = i.location_id
                 WHERE i.tenant_id = ? AND i.status = 'AVAILABLE' AND loc.deleted_at IS NULL AND loc.location_type IN ('DOCK','STAGING')
                 ORDER BY i.created_at, i.id""",
                (rs, n) -> new Object[]{rs.getObject(1, UUID.class), rs.getObject(2, UUID.class), rs.getObject(3, UUID.class), rs.getObject(4, UUID.class), rs.getBigDecimal(5)}, t);
        int created = 0;
        for (Object[] r : need) {
            BigDecimal qty = (BigDecimal) r[4];
            if (qty.signum() <= 0) continue;
            UUID client = (UUID) r[0], item = (UUID) r[1], lot = (UUID) r[2], from = (UUID) r[3];
            List<WhsePutawayRules.Suggestion> s = WhsePutawayRules.rank(candidates(t, client, item, from, null), qty, 1);
            jdbc.update("INSERT INTO whse_tasks (id, tenant_id, task_type, client_id, item_id, lot_id, from_location_id, to_location_id, qty, pick_sequence, reference_type) "
                            + "VALUES (?, ?, 'PUTAWAY', ?, ?, ?, ?, ?, ?, (SELECT pick_sequence FROM whse_locations WHERE id = ?), 'PUTAWAY')",
                    UUID.randomUUID(), t, client, item, lot, from, s.isEmpty() ? null : s.get(0).locationId(), qty, from);
            created++;
        }
        return created;
    }

    @Transactional(readOnly = true)
    public List<WhsePutawayRules.Suggestion> suggestions(TenantId tenantId, UUID taskId) {
        TaskRow task = get(tenantId, taskId);
        if (!PUTAWAY.equals(task.taskType())) throw new IllegalArgumentException("Only a put-away task has destination suggestions.");
        return WhsePutawayRules.rank(candidates(tenantId.getValue(), task.clientId(), task.itemId(), task.fromLocationId(), null), task.qty().subtract(task.qtyDone()), 5);
    }

    private List<WhsePutawayRules.Candidate> candidates(UUID tenant, UUID client, UUID item, UUID fromLocation, UUID onlyLocation) {
        return jdbc.query("""
                SELECT loc.id, loc.code, loc.location_type, loc.active, loc.capacity_units, loc.pick_sequence,
                       coalesce(sum(inv.qty_on_hand), 0) AS on_hand,
                       coalesce(bool_or(inv.item_id = ? AND inv.client_id = ? AND inv.qty_on_hand > 0), false) AS same_item,
                       coalesce(bool_or(inv.client_id <> ? AND inv.qty_on_hand > 0), false) AS other_client
                  FROM whse_locations loc
                  LEFT JOIN whse_inventory inv ON inv.location_id = loc.id AND inv.tenant_id = loc.tenant_id
                 WHERE loc.tenant_id = ? AND loc.deleted_at IS NULL AND loc.id <> ? AND (?::uuid IS NULL OR loc.id = ?::uuid)
                 GROUP BY loc.id""",
                (rs, n) -> new WhsePutawayRules.Candidate(rs.getObject(1, UUID.class), rs.getString(2), rs.getString(3), rs.getBoolean(4), rs.getBigDecimal(5),
                        rs.getBigDecimal(7), (Integer) rs.getObject(6), rs.getBoolean(8), rs.getBoolean(9)),
                item, client, client, tenant, fromLocation, onlyLocation, onlyLocation);
    }

    // ── the worklist ────────────────────────────────────────────────────────────────────────────────────────────

    /** Tasks in work order: highest priority first, then the walking order, then oldest. {@code mine} narrows to the user's claimed tasks. */
    @Transactional(readOnly = true)
    public List<TaskRow> list(TenantId tenantId, String type, List<String> statuses, UUID mineUser, UUID orderId, int limit) {
        StringBuilder sql = new StringBuilder(SELECT).append(" WHERE t.tenant_id = ?");
        List<Object> args = new ArrayList<>();
        args.add(tenantId.getValue());
        if (type != null) { sql.append(" AND t.task_type = ?"); args.add(type); }
        if (statuses != null && !statuses.isEmpty()) {
            sql.append(" AND t.status IN (").append(String.join(",", java.util.Collections.nCopies(statuses.size(), "?"))).append(")");
            args.addAll(statuses);
        }
        if (mineUser != null) { sql.append(" AND t.assigned_to = ? AND t.status = 'IN_PROGRESS'"); args.add(mineUser); }
        if (orderId != null) { sql.append(" AND t.reference_id = ?"); args.add(orderId); }
        sql.append(" ORDER BY t.priority, t.pick_sequence NULLS LAST, t.created_at, t.id LIMIT ?");
        args.add(Math.max(1, Math.min(limit, 500)));
        return jdbc.query(sql.toString(), WhseTaskService::row, args.toArray());
    }

    @Transactional(readOnly = true)
    public TaskRow get(TenantId tenantId, UUID id) {
        List<TaskRow> rows = jdbc.query(SELECT + " WHERE t.tenant_id = ? AND t.id = ?", WhseTaskService::row, tenantId.getValue(), id);
        if (rows.isEmpty()) throw new ResourceNotFoundException("WhseTask", id.toString());
        return rows.get(0);
    }

    @Transactional(readOnly = true)
    public Summary summary(TenantId tenantId) {
        int[] c = new int[5];
        jdbc.query("SELECT task_type, status, count(*) FROM whse_tasks WHERE tenant_id = ? AND status IN ('OPEN','IN_PROGRESS','SHORT') GROUP BY task_type, status",
                rs -> {
                    String type = rs.getString(1), status = rs.getString(2);
                    int n = rs.getInt(3);
                    if (PICK.equals(type)) { if ("OPEN".equals(status)) c[0] = n; else if ("IN_PROGRESS".equals(status)) c[1] = n; else c[2] = n; }
                    else { if ("OPEN".equals(status)) c[3] = n; else if ("IN_PROGRESS".equals(status)) c[4] = n; }
                }, tenantId.getValue());
        return new Summary(c[0], c[1], c[2], c[3], c[4]);
    }

    // ── doing a task ────────────────────────────────────────────────────────────────────────────────────────────

    /** Takes an open task. Claiming one you already hold is fine; one held by someone else is refused. */
    @Transactional
    public TaskRow claim(TenantId tenantId, UUID id, UUID user) {
        requireUser(user);
        int n = jdbc.update("UPDATE whse_tasks SET status = 'IN_PROGRESS', assigned_to = ?, assigned_at = now(), updated_at = now() "
                + "WHERE tenant_id = ? AND id = ? AND (status = 'OPEN' OR (status = 'IN_PROGRESS' AND assigned_to = ?))", user, tenantId.getValue(), id, user);
        if (n == 0) {
            TaskRow t = get(tenantId, id);
            if ("IN_PROGRESS".equals(t.status())) throw new HandyFlowException("Someone else has already taken this task.", HttpStatus.CONFLICT, "TASK_TAKEN");
            throw new HandyFlowException("This task is " + t.status().toLowerCase().replace('_', ' ') + " and cannot be claimed.", HttpStatus.CONFLICT, "TASK_NOT_OPEN");
        }
        return get(tenantId, id);
    }

    /** Hands a claimed task back. Its holder can, and so can a manager ({@code asManager}). */
    @Transactional
    public TaskRow release(TenantId tenantId, UUID id, UUID user, boolean asManager) {
        requireUser(user);
        int n = jdbc.update("UPDATE whse_tasks SET status = 'OPEN', assigned_to = NULL, assigned_at = NULL, updated_at = now() "
                + "WHERE tenant_id = ? AND id = ? AND status = 'IN_PROGRESS' AND (assigned_to = ? OR ?)", tenantId.getValue(), id, user, asManager);
        if (n == 0) throw new HandyFlowException("Only the person holding a task can hand it back.", HttpStatus.CONFLICT, "TASK_NOT_HELD");
        return get(tenantId, id);
    }

    /**
     * Completes a task. A pick: the quantity found (all of it, or less with a reason, which makes the task SHORT); picking moves no stock.
     * A put-away: the quantity moved and where to (the suggestion when none is given); the stock moves with a transfer.
     * The task must be open or held by the same user; the row is locked while it is completed.
     */
    @Transactional
    public TaskRow complete(TenantId tenantId, UUID id, BigDecimal qty, String reason, UUID toLocationId, UUID user) {
        requireUser(user);
        UUID t = tenantId.getValue();
        List<Object[]> locked = jdbc.query("SELECT task_type, status, assigned_to, client_id, item_id, lot_id, from_location_id, to_location_id, qty, reference_id, reference_number, order_line_id "
                        + "FROM whse_tasks WHERE tenant_id = ? AND id = ? FOR UPDATE",
                (rs, n) -> new Object[]{rs.getString(1), rs.getString(2), rs.getObject(3, UUID.class), rs.getObject(4, UUID.class), rs.getObject(5, UUID.class), rs.getObject(6, UUID.class),
                        rs.getObject(7, UUID.class), rs.getObject(8, UUID.class), rs.getBigDecimal(9), rs.getObject(10, UUID.class), rs.getString(11), rs.getObject(12, UUID.class)}, t, id);
        if (locked.isEmpty()) throw new ResourceNotFoundException("WhseTask", id.toString());
        Object[] k = locked.get(0);
        String type = (String) k[0], status = (String) k[1];
        UUID holder = (UUID) k[2];
        BigDecimal planned = (BigDecimal) k[8];
        if (!"OPEN".equals(status) && !"IN_PROGRESS".equals(status)) {
            throw new HandyFlowException("This task is already " + status.toLowerCase().replace('_', ' ') + ".", HttpStatus.CONFLICT, "TASK_FINISHED");
        }
        if ("IN_PROGRESS".equals(status) && holder != null && !holder.equals(user)) {
            throw new HandyFlowException("Someone else is working on this task.", HttpStatus.CONFLICT, "TASK_TAKEN");
        }
        if (qty == null || qty.signum() <= 0) throw new IllegalArgumentException("Enter the quantity done (above zero). To say nothing could be found, cancel the task or re-allocate the order.");
        if (qty.compareTo(planned) > 0) throw new IllegalArgumentException("The quantity done cannot be more than the " + plain(planned) + " the task asks for.");

        if (PICK.equals(type)) {
            boolean shortPick = qty.compareTo(planned) < 0;
            if (shortPick && (reason == null || reason.trim().length() < 5)) {
                throw new IllegalArgumentException("Picking less than " + plain(planned) + " needs a reason (at least 5 characters).");
            }
            jdbc.update("UPDATE whse_tasks SET status = ?, qty_done = ?, note = ?, completed_by = ?, completed_at = now(), assigned_to = coalesce(assigned_to, ?), updated_at = now() WHERE id = ?",
                    shortPick ? "SHORT" : "DONE", qty, shortPick ? reason.trim() : null, user, user, id);
            UUID lineId = (UUID) k[11];
            if (lineId != null) {
                jdbc.update("UPDATE whse_outbound_order_lines SET qty_picked = (SELECT coalesce(sum(qty_done), 0) FROM whse_tasks WHERE order_line_id = ? AND task_type = 'PICK' AND status IN ('DONE','SHORT')) WHERE id = ?",
                        lineId, lineId);
            }
        } else {
            UUID dest = toLocationId != null ? toLocationId : (UUID) k[7];
            if (dest == null) throw new IllegalArgumentException("No destination was suggested for this put-away. Choose a location.");
            UUID from = (UUID) k[6], client = (UUID) k[3], item = (UUID) k[4], lot = (UUID) k[5];
            List<WhsePutawayRules.Candidate> cand = candidates(t, client, item, from, dest);
            if (cand.isEmpty()) throw new ResourceNotFoundException("WhseLocation", dest.toString());
            String problem = WhsePutawayRules.problem(cand.get(0), qty);
            if (problem != null) throw new IllegalArgumentException(problem);
            inventoryService.transfer(tenantId, client, item, from, dest, lot, "AVAILABLE", qty, "PUTAWAY", TASK, id, "Put-away", null, user, "task:" + id);
            jdbc.update("UPDATE whse_tasks SET status = 'DONE', qty_done = ?, to_location_id = ?, completed_by = ?, completed_at = now(), assigned_to = coalesce(assigned_to, ?), updated_at = now() WHERE id = ?",
                    qty, dest, user, user, id);
        }
        return get(tenantId, id);
    }

    /** Cancels an unfinished task with a reason. */
    @Transactional
    public TaskRow cancel(TenantId tenantId, UUID id, String reason, UUID user) {
        requireUser(user);
        if (reason == null || reason.trim().length() < 5) throw new IllegalArgumentException("Give a reason for cancelling the task (at least 5 characters).");
        int n = jdbc.update("UPDATE whse_tasks SET status = 'CANCELLED', note = ?, updated_at = now() WHERE tenant_id = ? AND id = ? AND status IN ('OPEN','IN_PROGRESS','SHORT')",
                reason.trim(), tenantId.getValue(), id);
        if (n == 0) {
            get(tenantId, id);
            throw new HandyFlowException("Only a task that is not finished can be cancelled.", HttpStatus.CONFLICT, "TASK_FINISHED");
        }
        return get(tenantId, id);
    }

    private static void requireUser(UUID user) {
        if (user == null) throw new org.springframework.security.access.AccessDeniedException("Your user could not be identified, so this step cannot be recorded.");
    }

    private static String plain(BigDecimal v) { return v.stripTrailingZeros().toPlainString(); }
}
