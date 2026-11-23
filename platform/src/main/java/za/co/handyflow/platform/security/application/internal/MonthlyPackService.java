// security/application/internal/MonthlyPackService.java
package za.co.handyflow.platform.security.application.internal;

import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import za.co.handyflow.platform.security.application.internal.CoverageRules.Cover;
import za.co.handyflow.platform.security.application.internal.CoverageRules.Post;
import za.co.handyflow.platform.shared.HandyFlowException;
import za.co.handyflow.platform.shared.TenantId;

import java.sql.Timestamp;
import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.time.YearMonth;
import java.time.ZoneOffset;
import java.util.*;

/**
 * The monthly service report of one site, on South African month boundaries. Counts only: no guard, visitor or
 * incident detail leaves this report, so it is safe to hand to the client.
 */
@Service
@RequiredArgsConstructor
public class MonthlyPackService {

    public record Cover_(int posts, boolean measured, int postDays, double requiredGuardHours, double coveredGuardHours,
                         Double coveragePct, int postDaysShort, int postDaysUncovered) {}
    public record Patrols(int rounds, int complete, int partial, int missed, int checkpointsExpected, int checkpointsScanned,
                          Double checkpointPct) {}
    public record Incidents(int total, int resolved, Map<String, Integer> bySeverity) {}
    public record Visitors(int total, int withVehicle, Map<String, Integer> byType) {}
    public record Book(int daysInPeriod, int daysSigned) {}
    public record Pack(UUID siteId, String siteName, String month, LocalDate from, LocalDate to, boolean inProgress, String contractStatus,
                       Cover_ cover, Map<String, Integer> shifts, Patrols patrols, Incidents incidents, Visitors visitors,
                       int alarms, Book occurrenceBook) {}

    /** Optional so a service built by hand still works; without it South Africa's zone is used, never the server's. */
    @org.springframework.beans.factory.annotation.Autowired(required = false)
    private za.co.handyflow.platform.shared.BusinessCalendar calendar;

    private final JdbcTemplate jdbc;

    @Transactional(readOnly = true)
    public Pack pack(TenantId tenantId, UUID siteId, String month) { return pack(tenantId, siteId, month, Instant.now()); }

    @Transactional(readOnly = true)
    Pack pack(TenantId tenantId, UUID siteId, String monthAsked, Instant now) {
        UUID tenant = tenantId.getValue();
        List<Map<String, Object>> site = jdbc.queryForList("SELECT name, contract_status FROM security_sites WHERE id = ? AND tenant_id = ? AND deleted_at IS NULL", siteId, tenant);
        if (site.isEmpty()) throw new HandyFlowException("Site not found", HttpStatus.NOT_FOUND, "SITE_NOT_FOUND");
        java.time.ZoneId zone = za.co.handyflow.platform.shared.BusinessCalendar.zoneOf(calendar, tenantId);
        YearMonth m = MonthlyPackRules.month(monthAsked, now, zone);
        Instant[] w = MonthlyPackRules.window(m, zone);
        Instant until = w[1].isAfter(now) ? now : w[1];
        Timestamp from = Timestamp.from(w[0]), to = Timestamp.from(w[1]);          // columns with a time zone
        Timestamp fromU = utc(w[0]), toU = utc(w[1]);                              // columns without one (read as UTC)

        // cover against posts
        List<Post> posts = jdbc.query("""
                SELECT id, site_id, name, weekdays, start_time, end_time, min_guards FROM security_site_posts
                WHERE tenant_id = ? AND site_id = ? AND active""",
                (rs, i) -> {
                    Set<Integer> days = new TreeSet<>();
                    for (Object o : (Object[]) rs.getArray("weekdays").getArray()) days.add(((Number) o).intValue());
                    return new Post((UUID) rs.getObject("id"), (UUID) rs.getObject("site_id"), rs.getString("name"), days,
                            rs.getObject("start_time", LocalTime.class), rs.getObject("end_time", LocalTime.class), rs.getInt("min_guards"));
                }, tenant, siteId);
        List<Cover> covers = jdbc.query("""
                SELECT start_at, end_at FROM security_shifts
                WHERE tenant_id = ? AND site_id = ? AND deleted_at IS NULL AND status IN ('SCHEDULED','ACTIVE','COMPLETED')
                  AND start_at < ? AND end_at > ?""",
                (rs, i) -> new Cover(rs.getObject("start_at", LocalDateTime.class).toInstant(ZoneOffset.UTC),
                        rs.getObject("end_at", LocalDateTime.class).toInstant(ZoneOffset.UTC)),
                tenant, siteId, toU, fromU);
        MonthlyPackRules.CoverFigures cf = MonthlyPackRules.cover(posts, m, until, covers, zone);
        Cover_ cover = new Cover_(posts.size(), !posts.isEmpty(), cf.windows(), cf.requiredHours(), cf.coveredHours(),
                MonthlyPackRules.pct(cf.coveredHours(), cf.requiredHours()), cf.windowsShort(), cf.windowsUncovered());

        // shifts by status
        Map<String, Integer> shifts = new TreeMap<>();
        jdbc.query("SELECT status, count(*) AS n FROM security_shifts WHERE tenant_id = ? AND site_id = ? AND deleted_at IS NULL AND start_at >= ? AND start_at < ? GROUP BY status",
                rs -> { shifts.put(rs.getString("status"), rs.getInt("n")); }, tenant, siteId, fromU, toU);

        // patrols
        Patrols patrols = jdbc.queryForObject("""
                SELECT count(*) AS rounds,
                       count(*) FILTER (WHERE status = 'COMPLETE') AS complete,
                       count(*) FILTER (WHERE status = 'PARTIAL') AS partial,
                       count(*) FILTER (WHERE status = 'MISSED') AS missed,
                       COALESCE(sum(checkpoints_expected), 0) AS cp_expected,
                       COALESCE(sum(checkpoints_scanned), 0) AS cp_scanned
                FROM security_patrol_rounds
                WHERE tenant_id = ? AND site_id = ? AND expected_start_at >= ? AND expected_start_at < ? AND expected_end_at <= ?""",
                (rs, i) -> new Patrols(rs.getInt("rounds"), rs.getInt("complete"), rs.getInt("partial"), rs.getInt("missed"),
                        rs.getInt("cp_expected"), rs.getInt("cp_scanned"),
                        MonthlyPackRules.pct(rs.getInt("cp_scanned"), rs.getInt("cp_expected"))),
                tenant, siteId, from, to, Timestamp.from(until));

        // incidents (counts only)
        Map<String, Integer> sev = new TreeMap<>();
        int[] inc = new int[2];
        jdbc.query("SELECT severity, count(*) AS n, count(*) FILTER (WHERE status = 'RESOLVED') AS resolved FROM security_incidents WHERE tenant_id = ? AND site_id = ? AND deleted_at IS NULL AND occurred_at >= ? AND occurred_at < ? GROUP BY severity",
                rs -> { sev.put(rs.getString("severity"), rs.getInt("n")); inc[0] += rs.getInt("n"); inc[1] += rs.getInt("resolved"); },
                tenant, siteId, fromU, toU);

        // visitors (counts only: no names, no ID numbers)
        Map<String, Integer> types = new TreeMap<>();
        int[] vis = new int[2];
        jdbc.query("SELECT entry_type, count(*) AS n, count(*) FILTER (WHERE vehicle_registration IS NOT NULL AND vehicle_registration <> '') AS veh FROM security_gate_register_entries WHERE tenant_id = ? AND site_id = ? AND logged_in_at >= ? AND logged_in_at < ? GROUP BY entry_type",
                rs -> { types.put(rs.getString("entry_type"), rs.getInt("n")); vis[0] += rs.getInt("n"); vis[1] += rs.getInt("veh"); },
                tenant, siteId, from, to);

        Integer alarms = jdbc.queryForObject("SELECT count(*) FROM security_alarm_events WHERE tenant_id = ? AND site_id = ? AND created_at >= ? AND created_at < ?", Integer.class, tenant, siteId, from, to);
        Integer signed = jdbc.queryForObject("SELECT count(*) FROM security_ob_signoffs WHERE tenant_id = ? AND site_id = ? AND day >= ? AND day < ?", Integer.class,
                tenant, siteId, java.sql.Date.valueOf(m.atDay(1)), java.sql.Date.valueOf(m.plusMonths(1).atDay(1)));

        return new Pack(siteId, (String) site.get(0).get("name"), m.toString(), m.atDay(1), m.atEndOfMonth(), MonthlyPackRules.inProgress(m, now, zone),
                (String) site.get(0).get("contract_status"), cover, shifts, patrols,
                new Incidents(inc[0], inc[1], sev), new Visitors(vis[0], vis[1], types), alarms == null ? 0 : alarms,
                new Book(MonthlyPackRules.daysInPeriod(m, now, zone), signed == null ? 0 : signed));
    }

    private static Timestamp utc(Instant i) { return Timestamp.valueOf(LocalDateTime.ofInstant(i, ZoneOffset.UTC)); }
}
