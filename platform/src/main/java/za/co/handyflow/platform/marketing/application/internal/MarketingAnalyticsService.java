package za.co.handyflow.platform.marketing.application.internal;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.http.HttpStatus;
import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import za.co.handyflow.platform.marketing.dto.AnalyticsDtos.*;
import za.co.handyflow.platform.shared.HandyFlowException;
import za.co.handyflow.platform.shared.ResourceNotFoundException;
import za.co.handyflow.platform.shared.TenantId;

import java.sql.Timestamp;
import java.time.Instant;
import java.util.*;

/**
 * Single source of truth for marketing metrics. Everything is recomputed from per-recipient rows
 * (mkt_campaign_contacts); stored campaign counters are only used by the reconciliation view.
 * All queries are tenant-scoped through mkt_campaigns.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class MarketingAnalyticsService {

    private final NamedParameterJdbcTemplate named;

    private static final int MAX_COMPARE = 10;
    private static final int MAX_EXPORT  = 50_000;

    private record Counts(long snapshot, long sent, long delivered, long bounced, long complained,
                          long opened, long clicked, long unsub, long failed, long suppressed, long pending) {
        static Counts zero() { return new Counts(0,0,0,0,0,0,0,0,0,0,0); }
    }

    private record Camp(UUID id, String name, String status, int version, int recipients,
                        Instant createdAt, Instant sentAt, Map<String, Long> stored) {}

    private static final Map<String, String> OUTCOME_SQL = Map.ofEntries(
            Map.entry("ALL", "TRUE"),
            Map.entry("SENT", "cc.sent_at IS NOT NULL"),
            Map.entry("DELIVERED", "cc.delivered_at IS NOT NULL"),
            Map.entry("BOUNCED", "(cc.bounced_at IS NOT NULL OR cc.status = 'BOUNCED')"),
            Map.entry("COMPLAINED", "cc.complained_at IS NOT NULL"),
            Map.entry("OPENED", "cc.opened_at IS NOT NULL"),
            Map.entry("CLICKED", "cc.clicked_at IS NOT NULL"),
            Map.entry("UNSUBSCRIBED", "cc.status = 'UNSUBSCRIBED'"),
            Map.entry("FAILED", "cc.status = 'FAILED'"),
            Map.entry("SUPPRESSED", "cc.status = 'SUPPRESSED'"),
            Map.entry("PENDING", "cc.status = 'PENDING'"));

    // ── Definitions ──────────────────────────────────────────────────────────

    public List<MetricDefinition> definitions() {
        return List.of(
                new MetricDefinition("targeted", "Targeted", "Contacts that were eligible when the campaign launched.", "Excludes anyone suppressed at launch."),
                new MetricDefinition("sent", "Sent", "Recipients whose email was accepted by the mail server.", "Acceptance is not delivery. The message can still bounce or be filtered."),
                new MetricDefinition("delivered", "Delivered", "Recipients for whom your email provider confirmed delivery.", "Needs a provider webhook. Without one this stays at 0 and is shown as “not confirmed”."),
                new MetricDefinition("opened", "Opened (approx.)", "Recipients with at least one recorded open (a click also counts as an open).", "Approximate. Image blocking hides opens and mail-privacy features can inflate them."),
                new MetricDefinition("clicked", "Clicked", "Recipients with at least one click on a tracked link.", "The most reliable engagement signal. Security scanners can occasionally click links."),
                new MetricDefinition("bounced", "Bounced", "Recipients whose address was rejected or reported as a bounce.", ""),
                new MetricDefinition("complained", "Complained", "Recipients who marked the email as spam (provider-reported).", "Needs a provider webhook."),
                new MetricDefinition("unsubscribed", "Unsubscribed", "Recipients who opted out or were suppressed after being sent the email.", ""),
                new MetricDefinition("failed", "Failed", "Emails that could not be sent after retries.", ""),
                new MetricDefinition("suppressed", "Suppressed", "Targeted contacts blocked at launch or just before sending because they are on the suppression list.", ""),
                new MetricDefinition("openRate", "Open rate", "Opened ÷ Sent.", "Approximate, see Opened."),
                new MetricDefinition("clickRate", "Click rate", "Clicked ÷ Sent.", ""),
                new MetricDefinition("clickToOpenRate", "Click-to-open rate", "Clicked ÷ Opened.", ""),
                new MetricDefinition("bounceRate", "Bounce rate", "Bounced ÷ Sent.", "Capped at 100%."),
                new MetricDefinition("complaintRate", "Complaint rate", "Complained ÷ Sent.", ""),
                new MetricDefinition("unsubscribeRate", "Unsubscribe rate", "Unsubscribed ÷ Sent.", ""),
                new MetricDefinition("deliveryRate", "Delivery rate", "Delivered ÷ Sent.", "Only shown once the provider has confirmed at least one delivery."));
    }

    // ── Metrics ──────────────────────────────────────────────────────────────

    @Transactional(readOnly = true)
    public Overview overview(TenantId tenantId, int days) {
        int d = Math.max(1, Math.min(days, 365));
        List<UUID> ids = named.queryForList(
                "SELECT id FROM mkt_campaigns WHERE tenant_id = :t AND deleted_at IS NULL "
                        + "AND status IN ('SENDING','SENT','PAUSED') "
                        + "AND COALESCE(sent_at, created_at) >= now() - make_interval(days => :d) "
                        + "ORDER BY COALESCE(sent_at, created_at) DESC LIMIT 100",
                new MapSqlParameterSource("t", tenantId.getValue()).addValue("d", d), UUID.class);
        List<CampaignMetrics> items = metricsFor(tenantId, ids);

        long[] sum = new long[11];
        for (CampaignMetrics m : items) {
            long[] v = {m.targeted(), m.sent(), m.delivered(), m.bounced(), m.complained(), m.opened(),
                    m.clicked(), m.unsubscribed(), m.failed(), m.suppressed(), m.pending()};
            for (int i = 0; i < v.length; i++) sum[i] += v[i];
        }
        Counts c = new Counts(0, sum[1], sum[2], sum[3], sum[4], sum[5], sum[6], sum[7], sum[8], sum[9], sum[10]);
        CampaignMetrics totals = build(null, "All campaigns", "", 0, null, null, sum[0], c);
        return new Overview(d, items.size(), totals, items);
    }

    @Transactional(readOnly = true)
    public List<CampaignMetrics> compare(TenantId tenantId, List<UUID> ids) {
        if (ids == null || ids.size() < 2) throw new HandyFlowException("Select at least two campaigns to compare.", HttpStatus.BAD_REQUEST, "COMPARE_MIN");
        if (ids.size() > MAX_COMPARE) throw new HandyFlowException("Compare at most " + MAX_COMPARE + " campaigns.", HttpStatus.BAD_REQUEST, "COMPARE_MAX");
        return metricsFor(tenantId, ids);
    }

    @Transactional(readOnly = true)
    public CampaignMetrics campaign(TenantId tenantId, UUID id) {
        List<CampaignMetrics> l = metricsFor(tenantId, List.of(id));
        if (l.isEmpty()) throw new ResourceNotFoundException("Campaign", id.toString());
        return l.get(0);
    }

    private List<CampaignMetrics> metricsFor(TenantId tenantId, List<UUID> ids) {
        if (ids.isEmpty()) return List.of();
        List<Camp> camps = loadCamps(tenantId, ids);
        Map<UUID, Counts> counts = loadCounts(tenantId, camps.stream().map(Camp::id).toList());
        // keep the caller's order
        Map<UUID, Camp> byId = new HashMap<>();
        camps.forEach(c -> byId.put(c.id(), c));
        List<CampaignMetrics> out = new ArrayList<>();
        for (UUID id : ids) {
            Camp c = byId.get(id);
            if (c == null) continue;
            out.add(build(c.id(), c.name(), c.status(), c.version(), c.createdAt(), c.sentAt(),
                    c.recipients(), counts.getOrDefault(id, Counts.zero())));
        }
        return out;
    }

    private static CampaignMetrics build(UUID id, String name, String status, int version, Instant created, Instant sentAt,
                                         long targeted, Counts c) {
        return new CampaignMetrics(id, name, status, version, created, sentAt,
                targeted, c.sent(), c.delivered(), c.bounced(), c.complained(), c.opened(), c.clicked(),
                c.unsub(), c.failed(), c.suppressed(), c.pending(),
                rate(c.opened(), c.sent()), rate(c.clicked(), c.sent()), rate(c.clicked(), c.opened()),
                rate(c.bounced(), c.sent()), rate(c.complained(), c.sent()), rate(c.unsub(), c.sent()),
                c.delivered() > 0 ? rate(c.delivered(), c.sent()) : null,
                c.delivered() > 0);
    }

    private static Double rate(long n, long d) {
        if (d <= 0) return null;
        return Math.round(Math.min(1.0, n / (double) d) * 10_000d) / 10_000d;
    }

    private List<Camp> loadCamps(TenantId tenantId, Collection<UUID> ids) {
        return named.query("SELECT id, name, status, content_version, recipient_count, created_at, sent_at, "
                        + "sent_count, delivered_count, bounced_count, complained_count, open_count, click_count, "
                        + "unsubscribed_count, failed_count, suppressed_count "
                        + "FROM mkt_campaigns WHERE tenant_id = :t AND deleted_at IS NULL AND id IN (:ids)",
                new MapSqlParameterSource("t", tenantId.getValue()).addValue("ids", ids),
                (rs, i) -> {
                    Map<String, Long> s = new LinkedHashMap<>();
                    s.put("sent", rs.getLong("sent_count"));
                    s.put("delivered", rs.getLong("delivered_count"));
                    s.put("bounced", rs.getLong("bounced_count"));
                    s.put("complained", rs.getLong("complained_count"));
                    s.put("opened", rs.getLong("open_count"));
                    s.put("clicked", rs.getLong("click_count"));
                    s.put("unsubscribed", rs.getLong("unsubscribed_count"));
                    s.put("failed", rs.getLong("failed_count"));
                    s.put("suppressed", rs.getLong("suppressed_count"));
                    return new Camp((UUID) rs.getObject("id"), rs.getString("name"), rs.getString("status"),
                            rs.getInt("content_version"), rs.getInt("recipient_count"),
                            ts(rs.getTimestamp("created_at")), ts(rs.getTimestamp("sent_at")), s);
                });
    }

    private Map<UUID, Counts> loadCounts(TenantId tenantId, Collection<UUID> ids) {
        Map<UUID, Counts> out = new HashMap<>();
        named.query("""
            SELECT cc.campaign_id,
                   COUNT(*) AS snapshot_rows,
                   COUNT(*) FILTER (WHERE cc.sent_at IS NOT NULL)      AS sent,
                   COUNT(*) FILTER (WHERE cc.delivered_at IS NOT NULL) AS delivered,
                   COUNT(*) FILTER (WHERE cc.bounced_at IS NOT NULL OR cc.status = 'BOUNCED') AS bounced,
                   COUNT(*) FILTER (WHERE cc.complained_at IS NOT NULL) AS complained,
                   COUNT(*) FILTER (WHERE cc.opened_at IS NOT NULL)    AS opened,
                   COUNT(*) FILTER (WHERE cc.clicked_at IS NOT NULL)   AS clicked,
                   COUNT(*) FILTER (WHERE cc.status = 'UNSUBSCRIBED')  AS unsub,
                   COUNT(*) FILTER (WHERE cc.status = 'FAILED')        AS failed,
                   COUNT(*) FILTER (WHERE cc.status = 'SUPPRESSED')    AS suppressed,
                   COUNT(*) FILTER (WHERE cc.status = 'PENDING')       AS pending
            FROM mkt_campaign_contacts cc
            JOIN mkt_campaigns c ON c.id = cc.campaign_id
            WHERE c.tenant_id = :t AND cc.campaign_id IN (:ids)
            GROUP BY cc.campaign_id
            """, new MapSqlParameterSource("t", tenantId.getValue()).addValue("ids", ids),
                rs -> {
                    out.put((UUID) rs.getObject("campaign_id"), new Counts(
                            rs.getLong("snapshot_rows"), rs.getLong("sent"), rs.getLong("delivered"), rs.getLong("bounced"),
                            rs.getLong("complained"), rs.getLong("opened"), rs.getLong("clicked"), rs.getLong("unsub"),
                            rs.getLong("failed"), rs.getLong("suppressed"), rs.getLong("pending")));
                });
        return out;
    }

    // ── Timeline, links, recipients ──────────────────────────────────────────

    @Transactional(readOnly = true)
    public List<TimelinePoint> timeline(TenantId tenantId, UUID campaignId) {
        requireCampaign(tenantId, campaignId);
        return named.query("""
            SELECT d, SUM(o) AS opened, SUM(k) AS clicked FROM (
                SELECT CAST(cc.opened_at AT TIME ZONE 'Africa/Johannesburg' AS date) AS d, 1 AS o, 0 AS k
                  FROM mkt_campaign_contacts cc WHERE cc.campaign_id = :c AND cc.opened_at IS NOT NULL
                UNION ALL
                SELECT CAST(cc.clicked_at AT TIME ZONE 'Africa/Johannesburg' AS date), 0, 1
                  FROM mkt_campaign_contacts cc WHERE cc.campaign_id = :c AND cc.clicked_at IS NOT NULL
            ) x GROUP BY d ORDER BY d
            """, new MapSqlParameterSource("c", campaignId),
                (rs, i) -> new TimelinePoint(rs.getDate("d").toString(), rs.getLong("opened"), rs.getLong("clicked")));
    }

    @Transactional(readOnly = true)
    public List<LinkStat> links(TenantId tenantId, UUID campaignId) {
        requireCampaign(tenantId, campaignId);
        return named.query("SELECT url, COUNT(*) AS uniq, SUM(click_count) AS total, MAX(last_clicked_at) AS last "
                        + "FROM mkt_link_clicks WHERE campaign_id = :c AND tenant_id = :t "
                        + "GROUP BY url ORDER BY uniq DESC, total DESC LIMIT 100",
                new MapSqlParameterSource("c", campaignId).addValue("t", tenantId.getValue()),
                (rs, i) -> new LinkStat(rs.getString("url"), rs.getLong("uniq"), rs.getLong("total"), ts(rs.getTimestamp("last"))));
    }

    @Transactional(readOnly = true)
    public Page<RecipientRow> recipients(TenantId tenantId, UUID campaignId, String outcome, String search, Pageable pageable) {
        requireCampaign(tenantId, campaignId);
        MapSqlParameterSource p = new MapSqlParameterSource("c", campaignId);
        String where = where(outcome, search, p);
        int size = Math.max(1, Math.min(pageable.getPageSize(), 200));
        int page = Math.max(0, pageable.getPageNumber());

        Long total = named.queryForObject("SELECT COUNT(*) FROM mkt_campaign_contacts cc WHERE " + where, p, Long.class);
        MapSqlParameterSource q = copy(p).addValue("limit", size).addValue("offset", (long) page * size);
        List<RecipientRow> rows = named.query(RECIPIENT_SELECT + where
                + " ORDER BY cc.email LIMIT :limit OFFSET :offset", q, (rs, i) -> mapRecipient(rs));
        return new PageImpl<>(rows, PageRequest.of(page, size), total == null ? 0 : total);
    }

    @Transactional(readOnly = true)
    public String exportRecipientsCsv(TenantId tenantId, UUID campaignId, String outcome, String search) {
        requireCampaign(tenantId, campaignId);
        MapSqlParameterSource p = new MapSqlParameterSource("c", campaignId);
        String where = where(outcome, search, p);
        p.addValue("limit", MAX_EXPORT);
        List<RecipientRow> rows = named.query(RECIPIENT_SELECT + where + " ORDER BY cc.email LIMIT :limit", p, (rs, i) -> mapRecipient(rs));

        StringBuilder sb = new StringBuilder("Email,Name,Status,Delivery status,Sent,Delivered,Bounced,Complained,Opened,Clicked,Error\r\n");
        for (RecipientRow r : rows) {
            sb.append(String.join(",", cell(r.email()), cell(r.name()), cell(r.status()), cell(r.deliveryStatus()),
                    cell(iso(r.sentAt())), cell(iso(r.deliveredAt())), cell(iso(r.bouncedAt())), cell(iso(r.complainedAt())),
                    cell(iso(r.openedAt())), cell(iso(r.clickedAt())), cell(r.error()))).append("\r\n");
        }
        return sb.toString();
    }

    private static final String RECIPIENT_SELECT =
            "SELECT cc.id, cc.email, cc.name, cc.status, cc.delivery_status, cc.sent_at, cc.delivered_at, cc.bounced_at, "
                    + "cc.complained_at, cc.opened_at, cc.clicked_at, cc.error_message "
                    + "FROM mkt_campaign_contacts cc WHERE ";

    private RecipientRow mapRecipient(java.sql.ResultSet rs) throws java.sql.SQLException {
        return new RecipientRow((UUID) rs.getObject("id"), rs.getString("email"), rs.getString("name"),
                rs.getString("status"), rs.getString("delivery_status"),
                ts(rs.getTimestamp("sent_at")), ts(rs.getTimestamp("delivered_at")), ts(rs.getTimestamp("bounced_at")),
                ts(rs.getTimestamp("complained_at")), ts(rs.getTimestamp("opened_at")), ts(rs.getTimestamp("clicked_at")),
                rs.getString("error_message"));
    }

    private String where(String outcome, String search, MapSqlParameterSource p) {
        String key = outcome == null ? "ALL" : outcome.trim().toUpperCase();
        String frag = OUTCOME_SQL.get(key);
        if (frag == null) throw new HandyFlowException("Unknown outcome filter '" + outcome + "'.", HttpStatus.BAD_REQUEST, "BAD_FILTER");
        StringBuilder w = new StringBuilder("cc.campaign_id = :c AND ").append(frag);
        if (search != null && !search.isBlank()) {
            String esc = search.trim().toLowerCase().replace("\\", "\\\\").replace("%", "\\%").replace("_", "\\_");
            p.addValue("q", "%" + esc + "%");
            w.append(" AND (LOWER(cc.email) LIKE :q ESCAPE '\\' OR LOWER(COALESCE(cc.name,'')) LIKE :q ESCAPE '\\')");
        }
        return w.toString();
    }

    // ── Reconciliation ───────────────────────────────────────────────────────

    @Transactional(readOnly = true)
    public Reconciliation reconcile(TenantId tenantId, UUID campaignId) {
        Camp camp = loadCamps(tenantId, List.of(campaignId)).stream().findFirst()
                .orElseThrow(() -> new ResourceNotFoundException("Campaign", campaignId.toString()));
        Counts a = loadCounts(tenantId, List.of(campaignId)).getOrDefault(campaignId, Counts.zero());
        Map<String, Long> actual = new LinkedHashMap<>();
        actual.put("sent", a.sent()); actual.put("delivered", a.delivered()); actual.put("bounced", a.bounced());
        actual.put("complained", a.complained()); actual.put("opened", a.opened()); actual.put("clicked", a.clicked());
        actual.put("unsubscribed", a.unsub()); actual.put("failed", a.failed()); actual.put("suppressed", a.suppressed());

        List<ReconDiff> diffs = new ArrayList<>();
        boolean inSync = true;
        for (var e : actual.entrySet()) {
            long stored = camp.stored().getOrDefault(e.getKey(), 0L);
            diffs.add(new ReconDiff(e.getKey(), stored, e.getValue()));
            if (stored != e.getValue()) inSync = false;
        }

        List<String> notes = new ArrayList<>();
        Long queued = named.queryForObject("SELECT COUNT(*) FROM mkt_send_queue WHERE campaign_id = :c AND status IN ('PENDING','SENDING')",
                new MapSqlParameterSource("c", campaignId), Long.class);
        boolean finished = Set.of("SENT", "CANCELLED", "FAILED").contains(camp.status());
        if (finished && queued != null && queued > 0)
            notes.add(queued + " email(s) are still queued although the campaign is " + camp.status() + ". They will not be sent. Investigate the send queue.");
        if ("SENT".equals(camp.status()) && a.pending() > 0)
            notes.add(a.pending() + " recipient(s) are still PENDING on a finished campaign.");
        if (a.delivered() == 0 && a.sent() > 0)
            notes.add("No provider delivery confirmations have been received. Delivered, bounced-after-acceptance and complaint figures need a provider webhook.");
        if (a.opened() < a.clicked())
            notes.add("More recipients clicked than opened. This is possible when images are blocked, and open counts are approximate.");

        boolean repairable = !"SENDING".equals(camp.status());
        return new Reconciliation(camp.status(), inSync, repairable, diffs, notes);
    }

    @Transactional
    public Reconciliation repair(TenantId tenantId, UUID campaignId) {
        Camp camp = loadCamps(tenantId, List.of(campaignId)).stream().findFirst()
                .orElseThrow(() -> new ResourceNotFoundException("Campaign", campaignId.toString()));
        if ("SENDING".equals(camp.status())) {
            throw new HandyFlowException("Counters can't be repaired while the campaign is sending. Pause it first.",
                    HttpStatus.CONFLICT, "CAMPAIGN_SENDING");
        }
        Counts a = loadCounts(tenantId, List.of(campaignId)).getOrDefault(campaignId, Counts.zero());
        named.update("""
            UPDATE mkt_campaigns SET sent_count = :s, delivered_count = :d, bounced_count = :b, complained_count = :cm,
                   open_count = :o, click_count = :k, unsubscribed_count = :u, failed_count = :f, suppressed_count = :sp,
                   updated_at = now()
            WHERE id = :id AND tenant_id = :t
            """, new MapSqlParameterSource("id", campaignId).addValue("t", tenantId.getValue())
                .addValue("s", a.sent()).addValue("d", a.delivered()).addValue("b", a.bounced()).addValue("cm", a.complained())
                .addValue("o", a.opened()).addValue("k", a.clicked()).addValue("u", a.unsub()).addValue("f", a.failed()).addValue("sp", a.suppressed()));
        log.info("Marketing counters repaired campaign={} tenant={}", campaignId, tenantId);
        return reconcile(tenantId, campaignId);
    }

    // ── Helpers ──────────────────────────────────────────────────────────────

    private void requireCampaign(TenantId tenantId, UUID id) {
        Integer n = named.queryForObject("SELECT COUNT(*) FROM mkt_campaigns WHERE id = :id AND tenant_id = :t AND deleted_at IS NULL",
                new MapSqlParameterSource("id", id).addValue("t", tenantId.getValue()), Integer.class);
        if (n == null || n == 0) throw new ResourceNotFoundException("Campaign", id.toString());
    }

    private static MapSqlParameterSource copy(MapSqlParameterSource s) {
        MapSqlParameterSource c = new MapSqlParameterSource();
        c.addValues(s.getValues());
        return c;
    }
    private static Instant ts(Timestamp t) { return t == null ? null : t.toInstant(); }
    private static String iso(Instant i) { return i == null ? "" : i.toString(); }
    private static String cell(String v) {
        String s = v == null ? "" : v;
        if (!s.isEmpty() && "=+-@\t\r".indexOf(s.charAt(0)) >= 0) s = "'" + s;
        return "\"" + s.replace("\"", "\"\"") + "\"";
    }
}