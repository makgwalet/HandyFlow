package za.co.handyflow.platform.security.application.internal;

import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import za.co.handyflow.platform.shared.HandyFlowException;
import za.co.handyflow.platform.shared.TenantId;

import java.sql.Timestamp;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

/** Service-level targets for incidents, and how incidents stand against them. Reads can be limited to a set of sites; writes are whole-company. */
@Service
@RequiredArgsConstructor
public class SlaService {

    public record TargetView(UUID siteId, String siteName, String severity, int acknowledgeMinutes, int resolveMinutes) {}

    public record TargetsView(List<TargetView> companyDefaults, List<TargetView> sites) {}

    public record LiveItem(UUID incidentId, UUID siteId, String siteName, String severity, String title, String status, Instant recordedAt,
                           String state, String stage, Long overdueMinutes, Long remainingMinutes) {}

    public record Report(LocalDate from, LocalDate to, List<SlaRules.Row> rows, SlaRules.Row total) {}

    static final int MAX_DAYS = 366;
    static final int MAX_ROWS = 20000;
    static final int MAX_LIVE = 5000;

    static final String TARGETS = """
            SELECT t.site_id, s.name, t.severity, t.acknowledge_minutes, t.resolve_minutes
            FROM security_sla_targets t LEFT JOIN security_sites s ON s.id = t.site_id AND s.tenant_id = t.tenant_id
            WHERE t.tenant_id = ?""";

    static final String INCIDENTS = """
            SELECT i.id, i.site_id, s.name, i.severity, i.title, i.status, i.created_at, i.acknowledged_at, i.resolved_at
            FROM security_incidents i JOIN security_sites s ON s.id = i.site_id AND s.tenant_id = i.tenant_id
            WHERE i.tenant_id = ? AND i.deleted_at IS NULL""";

    private final JdbcTemplate jdbc;

    /** The company's calendar. Optional so a hand-built service still works, on the South African default. */
    @org.springframework.beans.factory.annotation.Autowired(required = false)
    private za.co.handyflow.platform.shared.BusinessCalendar calendar;

    /** The clock. Not final and set to a fixed time in tests. */
    private java.time.Clock clock = java.time.Clock.systemUTC();

    Instant now() { return clock.instant(); }

    private ZoneId zone(TenantId tenantId) { return za.co.handyflow.platform.shared.BusinessCalendar.zoneOf(calendar, tenantId); }

    // ---- targets ----

    private record Loaded(Map<String, SlaRules.Target> company, Map<UUID, Map<String, SlaRules.Target>> bySite, List<TargetView> views) {
        SlaRules.Target forIncident(UUID siteId, String severity) {
            Map<String, SlaRules.Target> s = bySite.get(siteId);
            return SlaRules.pick(s == null ? null : s.get(severity), company.get(severity));
        }
    }

    private Loaded load(TenantId tenantId) {
        Map<String, SlaRules.Target> company = new HashMap<>();
        Map<UUID, Map<String, SlaRules.Target>> bySite = new HashMap<>();
        List<TargetView> views = new ArrayList<>();
        jdbc.query(TARGETS, rs -> {
            UUID site = rs.getObject("site_id", UUID.class);
            String sev = rs.getString("severity");
            SlaRules.Target t = new SlaRules.Target(rs.getInt("acknowledge_minutes"), rs.getInt("resolve_minutes"));
            if (site == null) company.put(sev, t); else bySite.computeIfAbsent(site, k -> new HashMap<>()).put(sev, t);
            views.add(new TargetView(site, rs.getString("name"), sev, t.acknowledgeMinutes(), t.resolveMinutes()));
        }, tenantId.getValue());
        return new Loaded(company, bySite, views);
    }

    /** @param allowed the sites the user may see, or null for the whole company. Company defaults are shown to everyone: they decide what a limited user sees. */
    @Transactional(readOnly = true)
    public TargetsView targets(TenantId tenantId, Set<UUID> allowed) {
        Loaded l = load(tenantId);
        Comparator<TargetView> order = Comparator.comparing((TargetView v) -> v.siteName() == null ? "" : v.siteName().toLowerCase(java.util.Locale.ROOT))
                .thenComparingInt(v -> SlaRules.SEVERITIES.indexOf(v.severity()));
        List<TargetView> defaults = l.views().stream().filter(v -> v.siteId() == null).sorted(order).toList();
        List<TargetView> sites = l.views().stream().filter(v -> v.siteId() != null && (allowed == null || allowed.contains(v.siteId()))).sorted(order).toList();
        return new TargetsView(defaults, sites);
    }

    @Transactional
    public TargetsView save(TenantId tenantId, UUID actor, UUID siteId, String severity, Integer acknowledgeMinutes, Integer resolveMinutes) {
        String sev = SlaRules.severity(severity);
        SlaRules.Target t = SlaRules.validate(acknowledgeMinutes, resolveMinutes);
        UUID tenant = tenantId.getValue();
        if (siteId != null) {
            Integer n = jdbc.queryForObject("SELECT COUNT(*) FROM security_sites WHERE id = ? AND tenant_id = ? AND deleted_at IS NULL", Integer.class, siteId, tenant);
            if (n == null || n == 0) throw new HandyFlowException("Site not found.", HttpStatus.NOT_FOUND, "SITE_NOT_FOUND");
            jdbc.update("""
                    INSERT INTO security_sla_targets (id, tenant_id, site_id, severity, acknowledge_minutes, resolve_minutes, updated_by, updated_at)
                    VALUES (gen_random_uuid(), ?, ?, ?, ?, ?, ?, now())
                    ON CONFLICT (tenant_id, site_id, severity) WHERE site_id IS NOT NULL
                    DO UPDATE SET acknowledge_minutes = EXCLUDED.acknowledge_minutes, resolve_minutes = EXCLUDED.resolve_minutes, updated_by = EXCLUDED.updated_by, updated_at = now()""",
                    tenant, siteId, sev, t.acknowledgeMinutes(), t.resolveMinutes(), actor);
        } else {
            jdbc.update("""
                    INSERT INTO security_sla_targets (id, tenant_id, site_id, severity, acknowledge_minutes, resolve_minutes, updated_by, updated_at)
                    VALUES (gen_random_uuid(), ?, NULL, ?, ?, ?, ?, now())
                    ON CONFLICT (tenant_id, severity) WHERE site_id IS NULL
                    DO UPDATE SET acknowledge_minutes = EXCLUDED.acknowledge_minutes, resolve_minutes = EXCLUDED.resolve_minutes, updated_by = EXCLUDED.updated_by, updated_at = now()""",
                    tenant, sev, t.acknowledgeMinutes(), t.resolveMinutes(), actor);
        }
        audit(tenant, actor, "SLA_TARGET_SET", siteId, "{\"severity\":\"" + sev + "\",\"acknowledgeMinutes\":" + t.acknowledgeMinutes() + ",\"resolveMinutes\":" + t.resolveMinutes() + "}");
        return targets(tenantId, null);
    }

    @Transactional
    public TargetsView remove(TenantId tenantId, UUID actor, UUID siteId, String severity) {
        String sev = SlaRules.severity(severity);
        UUID tenant = tenantId.getValue();
        int n = siteId == null
                ? jdbc.update("DELETE FROM security_sla_targets WHERE tenant_id = ? AND site_id IS NULL AND severity = ?", tenant, sev)
                : jdbc.update("DELETE FROM security_sla_targets WHERE tenant_id = ? AND site_id = ? AND severity = ?", tenant, siteId, sev);
        if (n == 0) throw new HandyFlowException("There is no such target.", HttpStatus.NOT_FOUND, "SLA_NOT_FOUND");
        audit(tenant, actor, "SLA_TARGET_REMOVED", siteId, "{\"severity\":\"" + sev + "\"}");
        return targets(tenantId, null);
    }

    private void audit(UUID tenant, UUID actor, String action, UUID siteId, String json) {
        jdbc.update("""
                INSERT INTO security_audit_log (id, tenant_id, actor_id, actor_type, entity_type, entity_id, action, metadata, occurred_at)
                VALUES (gen_random_uuid(), ?, ?, 'USER', 'SLA_TARGET', ?, ?, ?::jsonb, now())""", tenant, actor, siteId != null ? siteId : tenant, action, json);
    }

    // ---- incidents against targets ----

    private record Found(SlaRules.Item item, String title, String status, Instant recordedAt) {}

    /** What the query saw: the incidents the user may see, and how many rows came back before that filter (to catch a range that is too big). */
    private record Scan(List<Found> found, int seen) {}

    private Scan scan(Loaded l, String where, List<Object> args, Set<UUID> allowed, int limit, Instant now) {
        List<Found> out = new ArrayList<>();
        int[] seen = {0};
        jdbc.query(INCIDENTS + where + " ORDER BY i.created_at LIMIT " + (limit + 1), rs -> {
            seen[0]++;
            UUID site = rs.getObject("site_id", UUID.class);
            if (allowed != null && !allowed.contains(site)) return;
            String sev = rs.getString("severity");
            Instant created = instant(rs.getTimestamp("created_at")), ack = instant(rs.getTimestamp("acknowledged_at")), res = instant(rs.getTimestamp("resolved_at"));
            SlaRules.Evaluation e = SlaRules.evaluate(created, ack, res, l.forIncident(site, sev), now);
            out.add(new Found(new SlaRules.Item(rs.getObject("id", UUID.class), site, rs.getString("name"), sev, e), rs.getString("title"), rs.getString("status"), created));
        }, args.toArray());
        return new Scan(out, seen[0]);
    }

    private static Instant instant(Timestamp t) { return t == null ? null : t.toInstant(); }

    /** Open incidents that have breached a target or are close to it, worst first. */
    @Transactional(readOnly = true)
    public List<LiveItem> live(TenantId tenantId, Set<UUID> allowed) {
        Scan scan = scan(load(tenantId), " AND i.resolved_at IS NULL", List.of(tenantId.getValue()), allowed, MAX_LIVE, now());
        if (scan.seen() > MAX_LIVE) throw new HandyFlowException("Too many open incidents to list. Resolve or close some first.", HttpStatus.UNPROCESSABLE_ENTITY, "SLA_TOO_MANY");
        Map<UUID, Found> byId = new HashMap<>();
        scan.found().forEach(f -> byId.put(f.item().incidentId(), f));
        List<LiveItem> out = new ArrayList<>();
        for (SlaRules.Item i : SlaRules.needingAttention(scan.found().stream().map(Found::item).toList())) {
            SlaRules.Evaluation e = i.evaluation();
            boolean ackWorse = SlaRules.rank(e.acknowledge().state()) >= SlaRules.rank(e.resolve().state());
            SlaRules.Stage st = ackWorse ? e.acknowledge() : e.resolve();
            Found f = byId.get(i.incidentId());
            out.add(new LiveItem(i.incidentId(), i.siteId(), i.siteName(), i.severity(), f.title(), f.status(), f.recordedAt(),
                    e.overall().name(), ackWorse ? "ACKNOWLEDGE" : "RESOLVE", st.overdueMinutes(), st.remainingMinutes()));
        }
        return out;
    }

    @Transactional(readOnly = true)
    public Report report(TenantId tenantId, Set<UUID> allowed, LocalDate from, LocalDate to, UUID siteId) {
        if (from == null || to == null || to.isBefore(from)) throw new HandyFlowException("Choose a start date and an end date that is not before it.", HttpStatus.BAD_REQUEST, "SLA_RANGE");
        if (java.time.temporal.ChronoUnit.DAYS.between(from, to) + 1 > MAX_DAYS) throw new HandyFlowException("Choose at most " + MAX_DAYS + " days.", HttpStatus.BAD_REQUEST, "SLA_RANGE");
        ZoneId zone = zone(tenantId);
        Instant start = from.atStartOfDay(zone).toInstant(), end = to.plusDays(1).atStartOfDay(zone).toInstant();
        List<Object> args = new ArrayList<>(List.of(tenantId.getValue(), Timestamp.from(start), Timestamp.from(end)));
        String where = " AND i.created_at >= ? AND i.created_at < ?";
        if (siteId != null) { where += " AND i.site_id = ?"; args.add(siteId); }
        Scan scan = scan(load(tenantId), where, args, allowed, MAX_ROWS, now());
        if (scan.seen() > MAX_ROWS) throw new HandyFlowException("That range has too many incidents. Choose a shorter one.", HttpStatus.UNPROCESSABLE_ENTITY, "SLA_TOO_MANY");
        List<SlaRules.Item> items = scan.found().stream().map(Found::item).toList();
        return new Report(from, to, SlaRules.summarise(items), SlaRules.total(items));
    }
}
