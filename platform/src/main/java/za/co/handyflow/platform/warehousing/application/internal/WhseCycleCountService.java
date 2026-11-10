package za.co.handyflow.platform.warehousing.application.internal;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import za.co.handyflow.platform.shared.HandyFlowException;
import za.co.handyflow.platform.shared.ResourceNotFoundException;
import za.co.handyflow.platform.shared.SeparationOfDutiesChecker;
import za.co.handyflow.platform.shared.TenantId;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import java.util.UUID;

/**
 * Cycle counts. A session freezes the system quantity of a set of positions; someone counts; every difference is approved
 * by a different person before it posts to the ledger. A line whose position moved after it was frozen is STALE and must
 * be counted again, so a count can never overwrite a legitimate movement. Stock accuracy is the share of lines whose
 * first count matched the system.
 *
 * Line states: OPEN (not counted) -> COUNTED -> MATCHED (no difference) or PENDING_APPROVAL -> APPLIED;
 * RECOUNT (variance rejected) and STALE (position moved) go back to being counted.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class WhseCycleCountService {

    public static final String APPROVE_PERMISSION = "WAREHOUSING_COUNT_APPROVE";
    static final int MAX_LINES = 5000;

    private final JdbcTemplate jdbc;
    private final WhseInventoryService inventoryService;
    private final SeparationOfDutiesChecker duties;

    public record SessionRow(UUID id, String countNumber, UUID clientId, String clientName, UUID locationId, String locationCode,
                             boolean blind, String status, String note, Instant createdAt, Instant submittedAt, Instant closedAt,
                             long lines, long counted, long matched, long pending, long stale) {}
    public record LineRow(UUID id, UUID inventoryId, UUID clientId, String clientName, UUID itemId, String sku, String description,
                          UUID locationId, String locationCode, String stockStatus, BigDecimal expectedQty, BigDecimal countedQty,
                          BigDecimal variance, String state, UUID countedBy, Instant countedAt, String decisionNote, boolean soleException) {}
    public record Detail(SessionRow session, List<LineRow> lines, boolean expectedHidden) {}
    public record SubmitResult(boolean submitted, long staleLines, long linesToApprove, boolean closed) {}
    public record Accuracy(int days, long linesCounted, long linesMatched, BigDecimal accuracyPct) {}

    // ── start ───────────────────────────────────────────────────────────────

    @Transactional
    public UUID create(TenantId tenantId, UUID clientId, UUID locationId, boolean blind, String note, UUID actor) {
        requireActor(actor);
        UUID t = tenantId.getValue();
        if (clientId != null && count("SELECT count(*) FROM whse_clients WHERE id = ? AND tenant_id = ?", clientId, t) == 0)
            throw new ResourceNotFoundException("Client", clientId.toString());
        if (locationId != null && count("SELECT count(*) FROM whse_locations WHERE id = ? AND tenant_id = ?", locationId, t) == 0)
            throw new ResourceNotFoundException("Location", locationId.toString());
        long positions = count(SQL_SCOPE_COUNT, t, clientId, clientId, locationId, locationId);
        if (positions == 0) throw new IllegalArgumentException("There is no stock on hand in that scope to count.");
        if (positions > MAX_LINES) throw new IllegalArgumentException("That scope has " + positions + " positions; count at most " + MAX_LINES + " at a time. Narrow it by client or location.");

        UUID id = UUID.randomUUID();
        long seq = count("SELECT count(*) FROM whse_count_sessions WHERE tenant_id = ?", t) + 1;
        String number = "CC-" + String.format("%05d", seq);
        jdbc.update("INSERT INTO whse_count_sessions (id, tenant_id, count_number, client_id, location_id, blind, note, created_by) VALUES (?,?,?,?,?,?,?,?)",
                id, t, number, clientId, locationId, blind, trim(note), actor);
        jdbc.update(SQL_SNAPSHOT_LINES, id, t, clientId, clientId, locationId, locationId);
        log.info("[Warehousing] Cycle count {} started tenant={} lines={} by={}", number, t, positions, actor);
        return id;
    }

    // ── read ────────────────────────────────────────────────────────────────

    @Transactional(readOnly = true)
    public List<SessionRow> list(TenantId tenantId, String status) {
        return jdbc.query(SQL_SESSIONS + " WHERE s.tenant_id = ? AND (?::text IS NULL OR s.status = ?::text) ORDER BY s.created_at DESC LIMIT 200",
                (rs, i) -> session(rs), tenantId.getValue(), status, status);
    }

    @Transactional(readOnly = true)
    public Detail detail(TenantId tenantId, UUID sessionId) {
        SessionRow s = jdbc.query(SQL_SESSIONS + " WHERE s.tenant_id = ? AND s.id = ?", (rs, i) -> session(rs), tenantId.getValue(), sessionId)
                .stream().findFirst().orElseThrow(() -> new ResourceNotFoundException("Cycle count", sessionId.toString()));
        // While a blind count is being counted, nobody sees the system quantity or the difference.
        boolean hide = s.blind() && "COUNTING".equals(s.status());
        List<LineRow> lines = jdbc.query(SQL_LINES + " WHERE l.session_id = ? AND l.tenant_id = ? ORDER BY it.sku, loc.code, l.stock_status",
                (rs, i) -> {
                    BigDecimal expected = rs.getBigDecimal("expected_qty");
                    BigDecimal counted = rs.getBigDecimal("counted_qty");
                    return new LineRow(rs.getObject("id", UUID.class), rs.getObject("inventory_id", UUID.class), rs.getObject("client_id", UUID.class),
                            rs.getString("client_name"), rs.getObject("item_id", UUID.class), rs.getString("sku"), rs.getString("description"),
                            rs.getObject("location_id", UUID.class), rs.getString("location_code"), rs.getString("stock_status"),
                            hide ? null : expected, counted, hide || counted == null ? null : counted.subtract(expected), rs.getString("state"),
                            rs.getObject("counted_by", UUID.class), ts(rs.getTimestamp("counted_at")), rs.getString("decision_note"), rs.getBoolean("sole_exception"));
                }, sessionId, tenantId.getValue());
        return new Detail(s, lines, hide);
    }

    /** Share of lines counted in the last {@code days} days whose first count matched the system quantity. Null when nothing was counted. */
    @Transactional(readOnly = true)
    public Accuracy accuracy(TenantId tenantId, int days) {
        int d = Math.max(1, Math.min(days, 365));
        return jdbc.queryForObject(SQL_ACCURACY, (rs, i) -> {
            long n = rs.getLong("counted"), m = rs.getLong("matched");
            return new Accuracy(d, n, m, n == 0 ? null : BigDecimal.valueOf(m * 100.0 / n).setScale(1, java.math.RoundingMode.HALF_UP));
        }, tenantId.getValue(), d);
    }

    // ── count ───────────────────────────────────────────────────────────────

    @Transactional
    public void recordCount(TenantId tenantId, UUID sessionId, UUID lineId, BigDecimal qty, UUID actor) {
        requireActor(actor);
        if (qty == null || qty.signum() < 0) throw new IllegalArgumentException("The counted quantity cannot be negative.");
        Line l = lockLine(tenantId, sessionId, lineId);
        if (!"COUNTING".equals(l.sessionStatus)) throw conflict("This count is no longer open for counting.", "COUNT_NOT_OPEN");
        if (!List.of("OPEN", "COUNTED", "RECOUNT", "STALE").contains(l.state)) throw conflict("This line has already been counted.", "LINE_COUNTED");
        if ("STALE".equals(l.state)) {
            // The position moved: take a fresh system quantity and treat this as a new first count.
            jdbc.update("UPDATE whse_count_lines SET expected_qty = (SELECT qty_on_hand FROM whse_inventory WHERE id = inventory_id), expected_at = now(), initial_counted_qty = NULL WHERE id = ?", lineId);
        }
        jdbc.update("UPDATE whse_count_lines SET counted_qty = ?, counted_by = ?, counted_at = now(), state = 'COUNTED', initial_counted_qty = coalesce(initial_counted_qty, ?), version = version + 1 WHERE id = ?",
                qty, actor, qty, lineId);
    }

    /**
     * Sends the counted lines for approval. Lines whose position moved since they were frozen become STALE and the session
     * stays open for recounting. When every difference is zero the session closes on its own.
     */
    @Transactional
    public SubmitResult submit(TenantId tenantId, UUID sessionId, UUID actor) {
        requireActor(actor);
        String status = lockSession(tenantId, sessionId);
        if (!"COUNTING".equals(status)) throw conflict("This count is not open for counting.", "COUNT_NOT_OPEN");
        long uncounted = count("SELECT count(*) FROM whse_count_lines WHERE session_id = ? AND state IN ('OPEN','RECOUNT','STALE')", sessionId);
        if (uncounted > 0) throw new IllegalArgumentException(uncounted + " line(s) still need to be counted.");

        long stale = jdbc.update(SQL_MARK_STALE, sessionId, tenantId.getValue());
        if (stale > 0) return new SubmitResult(false, stale, 0, false);

        jdbc.update("UPDATE whse_count_lines SET state = CASE WHEN counted_qty = expected_qty THEN 'MATCHED' ELSE 'PENDING_APPROVAL' END, version = version + 1 WHERE session_id = ? AND state = 'COUNTED'", sessionId);
        long toApprove = count("SELECT count(*) FROM whse_count_lines WHERE session_id = ? AND state = 'PENDING_APPROVAL'", sessionId);
        boolean closed = toApprove == 0;
        jdbc.update("UPDATE whse_count_sessions SET status = ?, submitted_at = coalesce(submitted_at, now()), closed_at = CASE WHEN ? THEN now() END, closed_by = CASE WHEN ? THEN ?::uuid END, version = version + 1 WHERE id = ?",
                closed ? "CLOSED" : "IN_REVIEW", closed, closed, actor, sessionId);
        return new SubmitResult(true, 0, toApprove, closed);
    }

    // ── decide ──────────────────────────────────────────────────────────────

    /** @return null when applied, otherwise the reason the line was sent back (it became STALE and must be recounted). */
    @Transactional
    public String approve(TenantId tenantId, UUID sessionId, UUID lineId, String note, UUID actor) {
        requireActor(actor);
        Line l = lockLine(tenantId, sessionId, lineId);
        requireDecidable(l);
        boolean sole = actor.equals(l.countedBy) && duties.otherHolders(tenantId, APPROVE_PERMISSION, actor) == 0;
        duties.require(tenantId, APPROVE_PERMISSION, l.countedBy, actor,
                "The person who counted this cannot approve its difference. Ask another warehouse administrator.",
                "WHSE_COUNT_LINE", lineId, "approved a stock count variance they counted themselves");

        long moved = count(SQL_MOVED_SINCE, l.inventoryId, l.expectedAt);
        if (moved > 0) {
            jdbc.update("UPDATE whse_count_lines SET state = 'STALE', counted_qty = NULL, counted_by = NULL, counted_at = NULL, version = version + 1 WHERE id = ?", lineId);
            jdbc.update("UPDATE whse_count_sessions SET status = 'COUNTING', version = version + 1 WHERE id = ?", sessionId);
            return "This position moved after the count started, so the difference is out of date. It has been sent back to be counted again.";
        }
        BigDecimal delta = l.countedQty.subtract(l.expectedQty);
        inventoryService.adjustForCount(tenantId, l.inventoryId, delta, sessionId, l.countNumber,
                "Cycle count " + l.countNumber + (note == null || note.isBlank() ? "" : ": " + note.trim()), actor, "count:" + lineId);
        jdbc.update("UPDATE whse_count_lines SET state = 'APPLIED', decided_by = ?, decided_at = now(), decision_note = ?, sole_exception = ?, version = version + 1 WHERE id = ?",
                actor, trim(note), sole, lineId);
        closeIfDone(sessionId, actor);
        return null;
    }

    @Transactional
    public void reject(TenantId tenantId, UUID sessionId, UUID lineId, String note, UUID actor) {
        requireActor(actor);
        if (note == null || note.trim().length() < 5) throw new IllegalArgumentException("Give a reason for asking for a recount (at least 5 characters).");
        Line l = lockLine(tenantId, sessionId, lineId);
        requireDecidable(l);
        jdbc.update("UPDATE whse_count_lines SET state = 'RECOUNT', counted_qty = NULL, counted_by = NULL, counted_at = NULL, decided_by = ?, decided_at = now(), decision_note = ?, version = version + 1 WHERE id = ?",
                actor, note.trim(), lineId);
        jdbc.update("UPDATE whse_count_sessions SET status = 'COUNTING', version = version + 1 WHERE id = ?", sessionId);
    }

    @Transactional
    public void close(TenantId tenantId, UUID sessionId, UUID actor) {
        requireActor(actor);
        String status = lockSession(tenantId, sessionId);
        if ("CLOSED".equals(status) || "CANCELLED".equals(status)) throw conflict("This count is already " + status.toLowerCase() + ".", "COUNT_FINISHED");
        long open = count("SELECT count(*) FROM whse_count_lines WHERE session_id = ? AND state IN ('OPEN','COUNTED','PENDING_APPROVAL','RECOUNT','STALE')", sessionId);
        if (open > 0) throw new IllegalArgumentException(open + " line(s) are not finished. Count, approve or reject them first, or cancel the count.");
        jdbc.update("UPDATE whse_count_sessions SET status = 'CLOSED', closed_at = now(), closed_by = ?, version = version + 1 WHERE id = ?", actor, sessionId);
    }

    /** Stops a count. Differences already applied stay applied (they are in the ledger); everything else is dropped. */
    @Transactional
    public void cancel(TenantId tenantId, UUID sessionId, UUID actor) {
        requireActor(actor);
        String status = lockSession(tenantId, sessionId);
        if ("CLOSED".equals(status) || "CANCELLED".equals(status)) throw conflict("This count is already " + status.toLowerCase() + ".", "COUNT_FINISHED");
        jdbc.update("UPDATE whse_count_sessions SET status = 'CANCELLED', closed_at = now(), closed_by = ?, version = version + 1 WHERE id = ?", actor, sessionId);
    }

    // ── internals ───────────────────────────────────────────────────────────

    private record Line(UUID inventoryId, String state, UUID countedBy, BigDecimal countedQty, BigDecimal expectedQty, Instant expectedAt,
                        String sessionStatus, String countNumber) {}

    private Line lockLine(TenantId tenantId, UUID sessionId, UUID lineId) {
        List<Line> rows = jdbc.query("""
                SELECT l.inventory_id, l.state, l.counted_by, l.counted_qty, l.expected_qty, l.expected_at, s.status AS session_status, s.count_number
                  FROM whse_count_lines l JOIN whse_count_sessions s ON s.id = l.session_id
                 WHERE l.id = ? AND l.session_id = ? AND l.tenant_id = ? FOR UPDATE OF l""",
                (rs, i) -> new Line(rs.getObject("inventory_id", UUID.class), rs.getString("state"), rs.getObject("counted_by", UUID.class),
                        rs.getBigDecimal("counted_qty"), rs.getBigDecimal("expected_qty"), rs.getTimestamp("expected_at").toInstant(),
                        rs.getString("session_status"), rs.getString("count_number")),
                lineId, sessionId, tenantId.getValue());
        if (rows.isEmpty()) throw new ResourceNotFoundException("Count line", lineId.toString());
        return rows.get(0);
    }

    private String lockSession(TenantId tenantId, UUID sessionId) {
        List<String> rows = jdbc.query("SELECT status FROM whse_count_sessions WHERE id = ? AND tenant_id = ? FOR UPDATE",
                (rs, i) -> rs.getString(1), sessionId, tenantId.getValue());
        if (rows.isEmpty()) throw new ResourceNotFoundException("Cycle count", sessionId.toString());
        return rows.get(0);
    }

    private static void requireDecidable(Line l) {
        if ("CLOSED".equals(l.sessionStatus) || "CANCELLED".equals(l.sessionStatus)) throw conflict("This count is already finished.", "COUNT_FINISHED");
        if (!"PENDING_APPROVAL".equals(l.state)) throw conflict("This line is not waiting for approval.", "LINE_NOT_PENDING");
    }

    private void closeIfDone(UUID sessionId, UUID actor) {
        long open = count("SELECT count(*) FROM whse_count_lines WHERE session_id = ? AND state IN ('OPEN','COUNTED','PENDING_APPROVAL','RECOUNT','STALE')", sessionId);
        if (open == 0) {
            jdbc.update("UPDATE whse_count_sessions SET status = 'CLOSED', closed_at = now(), closed_by = ?, version = version + 1 WHERE id = ? AND status <> 'CANCELLED'", actor, sessionId);
        }
    }

    private SessionRow session(java.sql.ResultSet rs) throws java.sql.SQLException {
        return new SessionRow(rs.getObject("id", UUID.class), rs.getString("count_number"), rs.getObject("client_id", UUID.class), rs.getString("client_name"),
                rs.getObject("location_id", UUID.class), rs.getString("location_code"), rs.getBoolean("blind"), rs.getString("status"), rs.getString("note"),
                ts(rs.getTimestamp("created_at")), ts(rs.getTimestamp("submitted_at")), ts(rs.getTimestamp("closed_at")),
                rs.getLong("lines"), rs.getLong("counted"), rs.getLong("matched"), rs.getLong("pending"), rs.getLong("stale"));
    }

    private static Instant ts(java.sql.Timestamp t) { return t == null ? null : t.toInstant(); }
    private long count(String sql, Object... args) { Long n = jdbc.queryForObject(sql, Long.class, args); return n == null ? 0 : n; }
    private static String trim(String s) { return s == null || s.isBlank() ? null : s.trim(); }
    private static void requireActor(UUID actor) {
        if (actor == null) throw new AccessDeniedException("Your user could not be identified, so this step cannot be recorded.");
    }
    private static HandyFlowException conflict(String message, String code) { return new HandyFlowException(message, HttpStatus.CONFLICT, code); }

    // ── SQL ─────────────────────────────────────────────────────────────────

    static final String SQL_SCOPE_COUNT = """
            SELECT count(*) FROM whse_inventory WHERE tenant_id = ? AND qty_on_hand > 0
               AND (?::uuid IS NULL OR client_id = ?::uuid) AND (?::uuid IS NULL OR location_id = ?::uuid)""";

    static final String SQL_SNAPSHOT_LINES = """
            INSERT INTO whse_count_lines (id, session_id, tenant_id, inventory_id, client_id, item_id, location_id, stock_status, expected_qty, expected_at)
            SELECT gen_random_uuid(), ?::uuid, tenant_id, id, client_id, item_id, location_id, status, qty_on_hand, now()
              FROM whse_inventory WHERE tenant_id = ? AND qty_on_hand > 0
               AND (?::uuid IS NULL OR client_id = ?::uuid) AND (?::uuid IS NULL OR location_id = ?::uuid)""";

    static final String SQL_SESSIONS = """
            SELECT s.id, s.count_number, s.client_id, c.trading_name AS client_name, s.location_id, loc.code AS location_code, s.blind, s.status,
                   s.note, s.created_at, s.submitted_at, s.closed_at,
                   (SELECT count(*) FROM whse_count_lines l WHERE l.session_id = s.id) AS lines,
                   (SELECT count(*) FROM whse_count_lines l WHERE l.session_id = s.id AND l.state NOT IN ('OPEN','RECOUNT','STALE')) AS counted,
                   (SELECT count(*) FROM whse_count_lines l WHERE l.session_id = s.id AND l.state = 'MATCHED') AS matched,
                   (SELECT count(*) FROM whse_count_lines l WHERE l.session_id = s.id AND l.state = 'PENDING_APPROVAL') AS pending,
                   (SELECT count(*) FROM whse_count_lines l WHERE l.session_id = s.id AND l.state = 'STALE') AS stale
              FROM whse_count_sessions s
              LEFT JOIN whse_clients c ON c.id = s.client_id
              LEFT JOIN whse_locations loc ON loc.id = s.location_id""";

    static final String SQL_LINES = """
            SELECT l.id, l.inventory_id, l.client_id, c.trading_name AS client_name, l.item_id, it.sku, it.description, l.location_id,
                   loc.code AS location_code, l.stock_status, l.expected_qty, l.counted_qty, l.state, l.counted_by, l.counted_at,
                   l.decision_note, l.sole_exception
              FROM whse_count_lines l
              JOIN whse_clients c ON c.id = l.client_id AND c.tenant_id = l.tenant_id
              JOIN whse_items it ON it.id = l.item_id AND it.tenant_id = l.tenant_id
              JOIN whse_locations loc ON loc.id = l.location_id AND loc.tenant_id = l.tenant_id""";

    /** A position moved after its line was frozen: any ledger movement newer than the snapshot. */
    static final String SQL_MOVED_SINCE = """
            SELECT count(*) FROM whse_stock_movements m JOIN whse_inventory i
                ON i.tenant_id = m.tenant_id AND i.client_id = m.client_id AND i.item_id = m.item_id AND i.location_id = m.location_id AND i.status = m.status
             WHERE i.id = ? AND m.created_at > ?""";

    static final String SQL_MARK_STALE = """
            UPDATE whse_count_lines l SET state = 'STALE', counted_qty = NULL, counted_by = NULL, counted_at = NULL, version = version + 1
             WHERE l.session_id = ? AND l.tenant_id = ? AND l.state = 'COUNTED'
               AND EXISTS (SELECT 1 FROM whse_stock_movements m JOIN whse_inventory i
                             ON i.tenant_id = m.tenant_id AND i.client_id = m.client_id AND i.item_id = m.item_id AND i.location_id = m.location_id AND i.status = m.status
                            WHERE i.id = l.inventory_id AND m.created_at > l.expected_at)""";

    static final String SQL_ACCURACY = """
            SELECT count(*) AS counted, count(*) FILTER (WHERE initial_counted_qty = expected_qty) AS matched
              FROM whse_count_lines WHERE tenant_id = ? AND initial_counted_qty IS NOT NULL
               AND counted_at >= now() - (? * interval '1 day')""";
}
