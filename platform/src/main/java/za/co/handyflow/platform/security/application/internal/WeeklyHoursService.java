// security/application/internal/WeeklyHoursService.java
package za.co.handyflow.platform.security.application.internal;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import za.co.handyflow.platform.security.application.internal.WeeklyHoursRules.Assessment;
import za.co.handyflow.platform.security.application.internal.WeeklyHoursRules.Slot;
import za.co.handyflow.platform.security.application.internal.WeeklyHoursRules.Thresholds;
import za.co.handyflow.platform.shared.TenantId;

import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.util.*;

/** Which guards are near, over or far over their weekly hours on the roster, and who is double-booked or short of rest. */
@Service
public class WeeklyHoursService {

    static final int MAX_ROWS = 500;

    public record Row(UUID guardId, String guardName, double hours, String state, int shifts, boolean overlaps,
                      List<WeeklyHoursRules.RestBreach> restBreaches, @com.fasterxml.jackson.annotation.JsonIgnore boolean flagged) {}
    public record Report(LocalDate weekStart, int guardsRostered, int flagged, Thresholds thresholds, boolean truncated, List<Row> rows) {}

    private final JdbcTemplate jdbc;
    private final Thresholds thresholds;

    public WeeklyHoursService(JdbcTemplate jdbc,
                              @Value("${handyflow.security.hours.warn-at:40}") double warnAt,
                              @Value("${handyflow.security.hours.limit:45}") double limit,
                              @Value("${handyflow.security.hours.ceiling:55}") double ceiling,
                              @Value("${handyflow.security.hours.min-rest:12}") double minRest) {
        this.jdbc = jdbc;
        this.thresholds = new Thresholds(warnAt, limit, ceiling, minRest);
    }

    @Transactional(readOnly = true)
    public Report report(TenantId tenantId, LocalDate anyDayInWeek, boolean flaggedOnly) {
        LocalDate day = anyDayInWeek != null ? anyDayInWeek : LocalDate.now(WeeklyHoursRules.SAST);
        Instant[] w = WeeklyHoursRules.week(day);
        Computed c = compute(tenantId, w);
        List<Row> rows = new ArrayList<>();
        for (Row r : c.rows()) if (!flaggedOnly || r.flagged()) rows.add(r);
        rows.sort(Comparator.comparingDouble(Row::hours).reversed().thenComparing(r -> r.guardName() == null ? "" : r.guardName()));
        boolean truncated = rows.size() > MAX_ROWS;
        List<Row> out = truncated ? rows.subList(0, MAX_ROWS) : rows;
        return new Report(w[0].atZone(WeeklyHoursRules.SAST).toLocalDate(), c.rows().size(), (int) c.rows().stream().filter(Row::flagged).count(), thresholds, truncated, out);
    }

    /** The dashboard's counts for the current week. Own transaction, so a failure here cannot spoil the dashboard's. Not capped. */
    @Transactional(readOnly = true, propagation = org.springframework.transaction.annotation.Propagation.REQUIRES_NEW)
    public HoursAttention.Counts attentionCounts(TenantId tenantId, Instant now) {
        Instant[] w = WeeklyHoursRules.week(now.atZone(WeeklyHoursRules.SAST).toLocalDate());
        return HoursAttention.count(compute(tenantId, w).assessments());
    }

    private record Computed(List<Row> rows, List<Assessment> assessments) {}

    private Computed compute(TenantId tenantId, Instant[] w) {
        // one extra day before the week so a short rest at the start of the week is seen
        Instant from = w[0].minusSeconds(24 * 3600L);
        Map<UUID, List<Slot>> byGuard = new LinkedHashMap<>();
        Map<UUID, String> names = new HashMap<>();
        jdbc.query("""
                SELECT sh.guard_id, g.first_name || ' ' || g.last_name AS name, sh.start_at, sh.end_at
                FROM security_shifts sh LEFT JOIN security_guards g ON g.id = sh.guard_id
                WHERE sh.tenant_id = ? AND sh.deleted_at IS NULL AND sh.status IN ('SCHEDULED','ACTIVE','COMPLETED')
                  AND sh.start_at < ? AND sh.end_at > ?""",
                rs -> {
                    UUID g = (UUID) rs.getObject("guard_id");
                    names.putIfAbsent(g, rs.getString("name"));
                    byGuard.computeIfAbsent(g, k -> new ArrayList<>()).add(new Slot(
                            rs.getObject("start_at", LocalDateTime.class).toInstant(ZoneOffset.UTC),
                            rs.getObject("end_at", LocalDateTime.class).toInstant(ZoneOffset.UTC)));
                },
                tenantId.getValue(), utc(w[1]), utc(from));

        List<Row> rows = new ArrayList<>();
        List<Assessment> all = new ArrayList<>();
        for (var e : byGuard.entrySet()) {
            Assessment a = WeeklyHoursRules.assess(e.getValue(), w[0], w[1], thresholds);
            if (a.shifts() == 0) continue; // only the day before the week
            all.add(a);
            rows.add(new Row(e.getKey(), names.get(e.getKey()), a.hours(), a.state().name(), a.shifts(), a.overlaps(), a.restBreaches(), a.flagged()));
        }
        return new Computed(rows, all);
    }

    private static java.sql.Timestamp utc(Instant i) { return java.sql.Timestamp.valueOf(LocalDateTime.ofInstant(i, ZoneOffset.UTC)); }
}
