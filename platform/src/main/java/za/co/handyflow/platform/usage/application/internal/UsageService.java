package za.co.handyflow.platform.usage.application.internal;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import za.co.handyflow.platform.shared.TenantId;

import java.time.LocalDate;
import java.time.ZoneId;
import java.util.List;

/**
 * What a business has used. Report mode: nothing here blocks anything. Seats are the active people plus invitations
 * still waiting (they hold a seat until accepted, cancelled or expired). Suspended people do not count. Everyone in
 * the users table is internal staff; client and portal people live elsewhere and are not counted.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class UsageService {

    static final ZoneId SA = ZoneId.of("Africa/Johannesburg");
    public static final String INVOICES = "INVOICES_ISSUED", QUOTES = "QUOTES_ISSUED", SEATS = "SEATS";

    private final JdbcTemplate jdbc;

    public record Meter(long used, Integer limit, Integer percent, String level) {}

    /** @param tier null when none has been assigned; then there are no limits to show */
    public record Snapshot(String period, String tier, boolean limitsProvisional, Meter invoices, Meter seats,
                           long activeUsers, long pendingInvitations, Meter quotes) {}

    @Transactional(readOnly = true)
    public Snapshot snapshot(TenantId tenantId) { return snapshot(tenantId, LocalDate.now(SA)); }

    @Transactional(readOnly = true)
    public Snapshot snapshot(TenantId tenantId, LocalDate today) {
        String period = String.format("%04d-%02d", today.getYear(), today.getMonthValue());
        String tier = single("SELECT usage_tier FROM tenants WHERE id = ?", String.class, tenantId.getValue());
        Integer invoiceLimit = tier == null ? null : limit(tier, INVOICES);
        Integer seatLimit = tier == null ? null : limit(tier, SEATS);
        Integer quoteLimit = tier == null ? null : limit(tier, QUOTES);
        boolean provisional = tier != null && Boolean.TRUE.equals(single(
                "SELECT bool_or(provisional) FROM usage_limits u WHERE tier = ? AND version = (SELECT max(version) FROM usage_limits "
                + "WHERE tier = u.tier AND metric = u.metric AND effective_from <= (now() AT TIME ZONE 'Africa/Johannesburg')::date)", Boolean.class, tier));

        Long issued = single("SELECT count(*) FROM usage_events WHERE tenant_id = ? AND metric = ? AND period = ?",
                Long.class, tenantId.getValue(), INVOICES, period);
        Long quoted = single("SELECT count(*) FROM usage_events WHERE tenant_id = ? AND metric = ? AND period = ?",
                Long.class, tenantId.getValue(), QUOTES, period);
        long qts = quoted == null ? 0 : quoted;
        Long active = single("SELECT count(*) FROM users WHERE tenant_id = ? AND status = 'ACTIVE'", Long.class, tenantId.getValue());
        Long invited = single("SELECT count(*) FROM user_invitations WHERE tenant_id = ? AND status = 'PENDING' AND expires_at > now()",
                Long.class, tenantId.getValue());
        long inv = issued == null ? 0 : issued, act = active == null ? 0 : active, pend = invited == null ? 0 : invited;

        Snapshot s = new Snapshot(period, tier, provisional,
                new Meter(inv, invoiceLimit, UsageRules.percent(inv, invoiceLimit), UsageRules.level(inv, invoiceLimit)),
                new Meter(act + pend, seatLimit, UsageRules.percent(act + pend, seatLimit), UsageRules.level(act + pend, seatLimit)),
                act, pend, new Meter(qts, quoteLimit, UsageRules.percent(qts, quoteLimit), UsageRules.level(qts, quoteLimit)));
        report(tenantId, s);
        return s;
    }

    /** One log line per look when a meter is at 80% or more, so the pattern can be reviewed before anything is enforced. */
    private void report(TenantId tenantId, Snapshot s) {
        for (var e : List.of(java.util.Map.entry(INVOICES, s.invoices()), java.util.Map.entry(QUOTES, s.quotes()), java.util.Map.entry(SEATS, s.seats()))) {
            String level = e.getValue().level();
            if (UsageRules.NONE.equals(level) || UsageRules.NO_LIMIT.equals(level)) continue;
            log.warn("USAGE-REPORT tenant={} metric={} used={} limit={} level={} tier={} provisional={} (report mode, nothing blocked)",
                    tenantId.getValue(), e.getKey(), e.getValue().used(), e.getValue().limit(), level, s.tier(), s.limitsProvisional());
        }
    }

    private Integer limit(String tier, String metric) {
        return single("SELECT limit_value FROM usage_limits WHERE tier = ? AND metric = ? "
                + "AND effective_from <= (now() AT TIME ZONE 'Africa/Johannesburg')::date ORDER BY version DESC LIMIT 1", Integer.class, tier, metric);
    }

    private <T> T single(String sql, Class<T> type, Object... args) {
        List<T> rows = jdbc.queryForList(sql, type, args);
        return rows.isEmpty() ? null : rows.get(0);
    }
}
