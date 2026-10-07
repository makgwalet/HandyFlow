// security/application/internal/GuardDirectoryService.java
package za.co.handyflow.platform.security.application.internal;

import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import za.co.handyflow.platform.security.domain.model.Guard;
import za.co.handyflow.platform.security.domain.repository.GuardRepository;
import za.co.handyflow.platform.security.dto.GuardDirectoryDtos.Counts;
import za.co.handyflow.platform.security.dto.GuardDirectoryDtos.Result;
import za.co.handyflow.platform.security.dto.GuardDirectoryDtos.Row;
import za.co.handyflow.platform.security.dto.GuardResponse;
import za.co.handyflow.platform.shared.HandyFlowException;
import za.co.handyflow.platform.shared.TenantId;

import java.sql.Timestamp;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.function.Function;
import java.util.stream.Collectors;

/**
 * The guards list with server-side search, filters, sorting and paging, a compliance roll-up per row, and counts for the
 * whole filtered set. Filters and sort keys are checked against fixed lists and only ever reach the SQL as those fixed
 * fragments; user text is always a bound parameter.
 */
@Service
@RequiredArgsConstructor
public class GuardDirectoryService {

    static final int EXPIRING_DAYS = 30;
    static final Set<String> STATUSES = Set.of("ACTIVE", "ON_LEAVE", "SUSPENDED", "UNDER_INVESTIGATION", "TERMINATED");
    static final Set<String> GRADES = Set.of("A", "B", "C", "D", "E");
    /** ATTENTION is "expired or expiring", the one most screens want. */
    static final Set<String> PSIRA_FILTERS = Set.of("EXPIRED", "EXPIRING", "ATTENTION", "VALID", "NONE");
    static final Set<String> SCREENING_FILTERS = Set.of("CLEARED", "PENDING", "FLAGGED", "UNSCREENED");
    private static final ZoneId SAST = ZoneId.of("Africa/Johannesburg");

    private final JdbcTemplate jdbc;
    private final GuardRepository guards;
    private final GuardService guardService;

    public record Query(String search, String status, String grade, String psira, String screening, UUID branchId,
                        String sort, boolean desc, int page, int size) {}

    @Transactional(readOnly = true)
    public Result search(TenantId tenantId, Query q) { return search(tenantId, q, LocalDate.now(SAST)); }

    @Transactional(readOnly = true)
    Result search(TenantId tenantId, Query q, LocalDate today) {
        validate(q);
        int size = Math.min(Math.max(q.size(), 1), 100);
        int page = Math.max(q.page(), 0);
        String psiraState = psiraStateSql(today);

        // Everything except the status filter, so the counts describe the whole set the status pills choose between.
        List<Object> baseParams = new ArrayList<>();
        StringBuilder base = new StringBuilder(" WHERE g.tenant_id = ? AND g.deleted_at IS NULL");
        baseParams.add(tenantId.getValue());
        if (q.search() != null && !q.search().isBlank()) {
            String like = "%" + escapeLike(q.search().trim().toLowerCase()) + "%";
            base.append(" AND (LOWER(g.first_name) LIKE ? OR LOWER(g.last_name) LIKE ? OR LOWER(g.first_name || ' ' || g.last_name) LIKE ?"
                    + " OR LOWER(COALESCE(g.psira_number, '')) LIKE ? OR LOWER(COALESCE(g.employee_code, '')) LIKE ? OR COALESCE(g.phone, '') LIKE ?)");
            for (int i = 0; i < 6; i++) baseParams.add(like);
        }
        if (notBlank(q.grade())) { base.append(" AND g.grade = ?"); baseParams.add(q.grade()); }
        if (q.branchId() != null) { base.append(" AND g.primary_branch_id = ?"); baseParams.add(q.branchId()); }
        if (notBlank(q.psira())) {
            if ("ATTENTION".equals(q.psira())) base.append(" AND ").append(psiraState).append(" IN ('EXPIRED', 'EXPIRING')");
            else { base.append(" AND ").append(psiraState).append(" = ?"); baseParams.add(q.psira()); }
        }
        if (notBlank(q.screening())) { base.append(" AND COALESCE(g.screening_status, 'UNSCREENED') = ?"); baseParams.add(q.screening()); }

        // Counts
        Map<String, Long> byStatus = new LinkedHashMap<>();
        for (String s : List.of("ACTIVE", "ON_LEAVE", "SUSPENDED", "UNDER_INVESTIGATION", "TERMINATED")) byStatus.put(s, 0L);
        jdbc.query("SELECT COALESCE(g.status, 'ACTIVE') AS st, COUNT(*) AS n FROM security_guards g" + base + " GROUP BY 1",
                rs -> { byStatus.merge(rs.getString("st"), rs.getLong("n"), Long::sum); }, baseParams.toArray());
        long all = byStatus.values().stream().mapToLong(Long::longValue).sum();
        long[] comp = jdbc.queryForObject("SELECT"
                + " COUNT(*) FILTER (WHERE " + psiraState + " = 'EXPIRED'),"
                + " COUNT(*) FILTER (WHERE " + psiraState + " = 'EXPIRING'),"
                + " COUNT(*) FILTER (WHERE COALESCE(g.screening_status, 'UNSCREENED') = 'FLAGGED'),"
                + " COUNT(*) FILTER (WHERE COALESCE(g.screening_status, 'UNSCREENED') = 'PENDING'),"
                + " COUNT(*) FILTER (WHERE COALESCE(g.screening_status, 'UNSCREENED') = 'UNSCREENED')"
                + " FROM security_guards g" + base,
                (rs, n) -> new long[]{rs.getLong(1), rs.getLong(2), rs.getLong(3), rs.getLong(4), rs.getLong(5)}, baseParams.toArray());
        Counts counts = new Counts(all, byStatus, comp[0], comp[1], comp[2], comp[3], comp[4]);

        // Page
        List<Object> pageParams = new ArrayList<>(baseParams);
        String statusClause = "";
        if (notBlank(q.status())) { statusClause = " AND COALESCE(g.status, 'ACTIVE') = ?"; pageParams.add(q.status()); }
        long total = notBlank(q.status()) ? byStatus.getOrDefault(q.status(), 0L) : all;

        String inner = "SELECT g.id, " + psiraState + " AS psira_state, (g.psira_expiry_date - DATE '" + today + "') AS psira_days,"
                + " COALESCE(g.screening_status, 'UNSCREENED') AS screening, LOWER(g.last_name) AS ln, LOWER(g.first_name) AS fn,"
                + " g.grade AS grade_key, COALESCE(g.status, 'ACTIVE') AS status_key, g.psira_expiry_date AS psira_key,"
                + " (SELECT MAX(s.start_at) AT TIME ZONE 'UTC' FROM security_shifts s WHERE s.guard_id = g.id AND s.tenant_id = g.tenant_id"
                + "   AND s.deleted_at IS NULL AND s.status IN ('ACTIVE', 'COMPLETED')) AS last_shift,"
                + " (SELECT MAX(l.scanned_at) AT TIME ZONE 'UTC' FROM security_checkpoint_logs l WHERE l.guard_id = g.id AND l.tenant_id = g.tenant_id) AS last_scan"
                + " FROM security_guards g" + base + statusClause;
        String sql = "SELECT * FROM (" + inner + ") x ORDER BY " + orderBy(q.sort(), q.desc()) + " LIMIT ? OFFSET ?";
        pageParams.add(size);
        pageParams.add((long) page * size);

        List<Meta> meta = jdbc.query(sql, (rs, n) -> {
            int days = rs.getInt("psira_days");
            Integer daysLeft = rs.wasNull() ? null : days;
            Timestamp shift = rs.getTimestamp("last_shift"), scan = rs.getTimestamp("last_scan");
            return new Meta((UUID) rs.getObject("id"), rs.getString("psira_state"), daysLeft, rs.getString("screening"),
                    latest(shift == null ? null : shift.toInstant(), scan == null ? null : scan.toInstant()));
        }, pageParams.toArray());

        Map<UUID, Guard> byId = guards.findAllById(meta.stream().map(Meta::id).toList()).stream()
                .collect(Collectors.toMap(Guard::getId, Function.identity()));
        List<Row> rows = new ArrayList<>();
        for (Meta m : meta) {
            Guard g = byId.get(m.id());
            if (g == null) continue;
            GuardResponse resp = guardService.toResponse(g);
            rows.add(new Row(resp, m.psiraState(), m.psiraDaysLeft(), m.screening(), m.lastActivity()));
        }
        return new Result(rows, total, page, size, counts);
    }

    private record Meta(UUID id, String psiraState, Integer psiraDaysLeft, String screening, Instant lastActivity) {}

    /** EXPIRED, EXPIRING, VALID or NONE. The dates come from LocalDate, never from the request, so they are safe to inline. */
    static String psiraStateSql(LocalDate today) {
        return "(CASE WHEN g.psira_expiry_date IS NULL THEN 'NONE'"
                + " WHEN g.psira_expiry_date < DATE '" + today + "' THEN 'EXPIRED'"
                + " WHEN g.psira_expiry_date <= DATE '" + today.plusDays(EXPIRING_DAYS) + "' THEN 'EXPIRING'"
                + " ELSE 'VALID' END)";
    }

    /** One of a fixed set of ORDER BY fragments; anything unknown sorts by name. Always ends on the name and id so paging is stable. */
    static String orderBy(String sort, boolean desc) {
        String dir = desc ? "DESC" : "ASC";
        String nulls = "NULLS LAST"; // guards with no date or no activity always sink to the bottom, whichever way it is sorted
        String key = switch (sort == null ? "name" : sort) {
            case "grade"    -> "grade_key " + dir + ", ";
            case "status"   -> "status_key " + dir + ", ";
            case "psira"    -> "psira_key " + dir + " " + nulls + ", ";
            case "activity" -> "GREATEST(last_shift, last_scan) " + dir + " " + nulls + ", ";
            default         -> "";
        };
        String nameDir = (sort == null || "name".equals(sort)) ? dir : "ASC";
        return key + "ln " + nameDir + ", fn " + nameDir + ", id";
    }

    static Instant latest(Instant a, Instant b) {
        if (a == null) return b;
        if (b == null) return a;
        return a.isAfter(b) ? a : b;
    }

    private static void validate(Query q) {
        check(q.status(), STATUSES, "status");
        check(q.grade(), GRADES, "grade");
        check(q.psira(), PSIRA_FILTERS, "psira");
        check(q.screening(), SCREENING_FILTERS, "screening");
    }

    private static void check(String value, Set<String> allowed, String what) {
        if (notBlank(value) && !allowed.contains(value))
            throw new HandyFlowException("Unknown " + what + " filter: " + value, HttpStatus.BAD_REQUEST, "BAD_FILTER");
    }

    private static boolean notBlank(String s) { return s != null && !s.isBlank(); }

    private static String escapeLike(String s) { return s.replace("\\", "\\\\").replace("%", "\\%").replace("_", "\\_"); }
}
