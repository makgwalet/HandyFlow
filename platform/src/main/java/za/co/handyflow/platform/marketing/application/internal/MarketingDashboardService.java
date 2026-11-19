package za.co.handyflow.platform.marketing.application.internal;

import lombok.RequiredArgsConstructor;
import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import za.co.handyflow.platform.marketing.dto.DashboardDtos.*;
import za.co.handyflow.platform.shared.TenantId;

import java.sql.Timestamp;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.time.temporal.ChronoUnit;
import java.util.*;

/** Aggregates for the Marketing dashboard. Same definitions as MarketingAnalyticsService; journey step campaigns excluded. */
@Service
@RequiredArgsConstructor
public class MarketingDashboardService {

    private final NamedParameterJdbcTemplate named;

    private static final ZoneId SAST = ZoneId.of("Africa/Johannesburg");

    private static final String BASE =
            " FROM mkt_campaign_contacts cc JOIN mkt_campaigns c ON c.id = cc.campaign_id "
                    + "WHERE c.tenant_id = :t AND c.kind = 'CAMPAIGN' AND c.deleted_at IS NULL";

    @Transactional(readOnly = true)
    public Dashboard dashboard(TenantId tenantId, int days) {
        int d = Math.max(1, Math.min(days, 365));
        Instant now = Instant.now();
        Instant from = now.minus(d, ChronoUnit.DAYS);
        Instant prevFrom = from.minus(d, ChronoUnit.DAYS);

        Period current = period(tenantId, from, now.plusSeconds(1));
        Period previous = period(tenantId, prevFrom, from);

        LocalDate start = LocalDate.now(SAST).minusDays(d - 1L);
        Instant seriesFrom = start.atStartOfDay(SAST).toInstant();
        Map<LocalDate, long[]> byDay = new TreeMap<>();
        for (int i = 0; i < d; i++) byDay.put(start.plusDays(i), new long[3]);

        MapSqlParameterSource p = new MapSqlParameterSource("t", tenantId.getValue()).addValue("from", Timestamp.from(seriesFrom));
        named.query("SELECT d, SUM(s) AS sent, SUM(o) AS opened, SUM(k) AS clicked FROM ("
                        + " SELECT CAST(cc.sent_at AT TIME ZONE 'Africa/Johannesburg' AS date) AS d, 1 AS s, 0 AS o, 0 AS k" + BASE + " AND cc.sent_at >= :from"
                        + " UNION ALL SELECT CAST(cc.opened_at AT TIME ZONE 'Africa/Johannesburg' AS date), 0, 1, 0" + BASE + " AND cc.opened_at >= :from"
                        + " UNION ALL SELECT CAST(cc.clicked_at AT TIME ZONE 'Africa/Johannesburg' AS date), 0, 0, 1" + BASE + " AND cc.clicked_at >= :from"
                        + ") x GROUP BY d", p,
                rs -> {
                    long[] v = byDay.get(rs.getDate("d").toLocalDate());
                    if (v != null) { v[0] = rs.getLong("sent"); v[1] = rs.getLong("opened"); v[2] = rs.getLong("clicked"); }
                });
        List<DayPoint> series = new ArrayList<>();
        byDay.forEach((day, v) -> series.add(new DayPoint(day.toString(), v[0], v[1], v[2])));

        List<SourceCount> sources = named.query(
                "SELECT COALESCE(opt_in_source, 'UNKNOWN') AS s, COUNT(*) AS n FROM mkt_contact_preferences "
                        + "WHERE tenant_id = :t GROUP BY 1 ORDER BY 2 DESC",
                new MapSqlParameterSource("t", tenantId.getValue()),
                (rs, i) -> new SourceCount(rs.getString("s"), rs.getLong("n")));

        return new Dashboard(d, current, previous, series, sources);
    }

    private Period period(TenantId tenantId, Instant from, Instant to) {
        return named.queryForObject(
                "SELECT COUNT(DISTINCT cc.campaign_id) AS campaigns, COUNT(*) AS sent, "
                        + "COUNT(*) FILTER (WHERE cc.opened_at IS NOT NULL) AS opened, "
                        + "COUNT(*) FILTER (WHERE cc.clicked_at IS NOT NULL) AS clicked, "
                        + "COUNT(*) FILTER (WHERE cc.status = 'UNSUBSCRIBED') AS unsub"
                        + BASE + " AND cc.sent_at >= :from AND cc.sent_at < :to",
                new MapSqlParameterSource("t", tenantId.getValue()).addValue("from", Timestamp.from(from)).addValue("to", Timestamp.from(to)),
                (rs, i) -> {
                    long sent = rs.getLong("sent"), o = rs.getLong("opened"), k = rs.getLong("clicked"), u = rs.getLong("unsub");
                    return new Period(rs.getLong("campaigns"), sent, o, k, u, rate(o, sent), rate(k, sent), rate(u, sent));
                });
    }

    private static Double rate(long n, long d) {
        return d <= 0 ? null : Math.round(Math.min(1.0, n / (double) d) * 10_000d) / 10_000d;
    }
}