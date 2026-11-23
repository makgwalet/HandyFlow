// shared/BusinessCalendar.java
package za.co.handyflow.platform.shared;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.dao.DataAccessException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.LocalDate;
import java.time.YearMonth;
import java.time.ZoneId;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Answers "which day, week or month?" in a company's own time zone (tenants.time_zone, default Africa/Johannesburg).
 * Use this instead of ZoneOffset.UTC, hard-coded zones or LocalDate.now(). The rules themselves live in {@link BusinessTime}.
 */
@Component
public class BusinessCalendar {

    static final Duration CACHE_FOR = Duration.ofMinutes(5);
    private record Cached(ZoneId zone, Instant expires) {}

    private final JdbcTemplate jdbc;
    private final Clock clock;
    private final ConcurrentHashMap<UUID, Cached> cache = new ConcurrentHashMap<>();

    @Autowired
    public BusinessCalendar(JdbcTemplate jdbc, Clock clock) {
        this.jdbc = jdbc;
        this.clock = clock;
    }

    public ZoneId zone(TenantId tenantId) { return zone(tenantId.getValue()); }

    /** The company's zone through an optional calendar: the default zone when there is none. Never the server's zone. */
    public static ZoneId zoneOf(BusinessCalendar calendar, TenantId tenantId) {
        return calendar == null ? BusinessTime.DEFAULT_ZONE : calendar.zone(tenantId);
    }

    /** The company's business day today through an optional calendar (the default zone when there is none). */
    public static LocalDate todayOf(BusinessCalendar calendar, TenantId tenantId) {
        return calendar == null ? BusinessTime.today(BusinessTime.DEFAULT_ZONE, Instant.now()) : calendar.today(tenantId);
    }

    /** The company's zone; the default if the company has none, it is not a known zone, or the lookup fails (a failure is not remembered). */
    public ZoneId zone(UUID tenant) {
        Instant now = clock.instant();
        Cached c = cache.get(tenant);
        if (c != null && c.expires().isAfter(now)) return c.zone();
        try {
            List<String> ids = jdbc.queryForList("SELECT time_zone FROM tenants WHERE id = ?", String.class, tenant);
            ZoneId z = BusinessTime.zoneOrDefault(ids.isEmpty() ? null : ids.get(0));
            cache.put(tenant, new Cached(z, now.plus(CACHE_FOR)));
            return z;
        } catch (DataAccessException e) {
            return BusinessTime.DEFAULT_ZONE;
        }
    }

    /** Forget a company's zone, for when it is changed. */
    public void evict(UUID tenant) { cache.remove(tenant); }

    public LocalDate today(TenantId t) { return BusinessTime.today(zone(t), clock.instant()); }
    public LocalDate businessDateOf(TenantId t, Instant moment) { return BusinessTime.businessDateOf(zone(t), moment); }
    public Instant[] dayRange(TenantId t, LocalDate day) { return BusinessTime.dayRange(zone(t), day); }
    public Instant[] weekRange(TenantId t, LocalDate anyDay) { return BusinessTime.weekRange(zone(t), anyDay); }
    public Instant[] monthRange(TenantId t, YearMonth month) { return BusinessTime.monthRange(zone(t), month); }
    public String format(TenantId t, Instant moment) { return BusinessTime.format(zone(t), moment); }
}
