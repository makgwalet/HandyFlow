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
import za.co.handyflow.platform.marketing.dto.AttributionDtos.*;
import za.co.handyflow.platform.shared.HandyFlowException;
import za.co.handyflow.platform.shared.ResourceNotFoundException;
import za.co.handyflow.platform.shared.TenantId;

import java.sql.Timestamp;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.time.temporal.ChronoUnit;
import java.util.*;
import java.util.regex.Pattern;

/**
 * Sales attribution: last click within a window. Click-based only (opens are unreliable).
 * Attribution is computed once when the sale is recorded and stored, so reports never shift retroactively.
 * Every query is tenant-scoped.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class MarketingAttributionService {

    private final NamedParameterJdbcTemplate named;

    private static final int DEFAULT_WINDOW = 7;
    private static final long MAX_CENTS = 1_000_000_000_000L;   // R10 billion: rejects obvious garbage
    private static final Pattern EMAIL = Pattern.compile("^[^@\\s]+@[^@\\s]+\\.[^@\\s]+$");
    private static final Pattern CURRENCY = Pattern.compile("^[A-Z]{3}$");
    private static final ZoneId SAST = ZoneId.of("Africa/Johannesburg");

    // ── Settings and cost ────────────────────────────────────────────────────

    @Transactional(readOnly = true)
    public int windowDays(UUID tenant) {
        List<Integer> r = named.query("SELECT attribution_window_days FROM mkt_settings WHERE tenant_id = :t",
                new MapSqlParameterSource("t", tenant), (rs, i) -> rs.getInt(1));
        return r.isEmpty() ? DEFAULT_WINDOW : r.get(0);
    }

    @Transactional
    public AttributionSettings updateWindow(TenantId t, UUID user, int days) {
        if (days < 1 || days > 30) throw bad("The attribution window must be between 1 and 30 days.", "WINDOW_INVALID");
        named.update("""
            INSERT INTO mkt_settings (tenant_id, attribution_window_days, updated_by, updated_at)
            VALUES (:t, :d, CAST(:u AS uuid), now())
            ON CONFLICT (tenant_id) DO UPDATE SET attribution_window_days = :d, updated_by = CAST(:u AS uuid), updated_at = now()
            """, new MapSqlParameterSource("t", t.getValue()).addValue("d", days).addValue("u", user == null ? null : user.toString()));
        return new AttributionSettings(days);
    }

    @Transactional
    public void setCost(TenantId t, UUID campaignId, Long costCents) {
        if (costCents != null && costCents > MAX_CENTS) throw bad("That cost is too large.", "COST_INVALID");
        int n = named.update("UPDATE mkt_campaigns SET cost_cents = :c, updated_at = now() WHERE id = :id AND tenant_id = :t AND deleted_at IS NULL",
                new MapSqlParameterSource().addValue("c", costCents, java.sql.Types.BIGINT).addValue("id", campaignId).addValue("t", t.getValue()));
        if (n == 0) throw new ResourceNotFoundException("Campaign", campaignId.toString());
    }

    // ── Recording ────────────────────────────────────────────────────────────

    /** Idempotent on (tenant, externalRef). Never throws for a duplicate. */
    @Transactional
    public RecordResult recordPayment(TenantId t, UUID user, String email, long amountCents, String currency,
                                      String externalRef, Instant paidAt, String description, String source) {
        String em = email == null ? "" : email.toLowerCase().trim();
        if (em.length() > 320 || !EMAIL.matcher(em).matches()) throw bad("Invalid email address.", "INVALID_EMAIL");
        if (amountCents <= 0 || amountCents > MAX_CENTS) throw bad("The amount must be positive.", "AMOUNT_INVALID");
        String cur = currency == null ? "" : currency.trim().toUpperCase();
        if (!CURRENCY.matcher(cur).matches()) throw bad("Currency must be a 3-letter code such as ZAR.", "CURRENCY_INVALID");
        Instant at = paidAt == null ? Instant.now() : paidAt;
        if (at.isAfter(Instant.now().plus(1, ChronoUnit.DAYS))) throw bad("The payment date is in the future.", "DATE_INVALID");
        String ref = blank(externalRef) == null ? "manual-" + UUID.randomUUID() : externalRef.trim();

        int window = windowDays(t.getValue());
        UUID prefId = prefId(t, em);
        UUID campaignId = null, ccId = null; Instant clickAt = null;
        if (prefId != null) {
            List<Map<String, Object>> hit = lastClick(t, prefId, at, window);
            if (!hit.isEmpty()) {
                campaignId = (UUID) hit.get(0).get("campaign_id");
                ccId = (UUID) hit.get(0).get("id");
                clickAt = ((Timestamp) hit.get(0).get("t")).toInstant();
            }
        }
        List<UUID> ins = named.queryForList("""
            INSERT INTO mkt_conversions (id, tenant_id, external_ref, kind, email, preference_id, amount_cents, currency, paid_at,
                description, source, campaign_id, campaign_contact_id, attributed_click_at, window_days, created_by)
            VALUES (:id, :t, :ref, 'PAYMENT', :e, :p, :a, :cur, :at, :d, :src, :c, :cc, :ck, :w, CAST(:u AS uuid))
            ON CONFLICT (tenant_id, external_ref) DO NOTHING RETURNING id
            """, new MapSqlParameterSource("id", UUID.randomUUID()).addValue("t", t.getValue()).addValue("ref", ref)
                .addValue("e", em).addValue("p", prefId).addValue("a", amountCents).addValue("cur", cur)
                .addValue("at", Timestamp.from(at)).addValue("d", trunc(blank(description), 200)).addValue("src", source)
                .addValue("c", campaignId).addValue("cc", ccId).addValue("ck", clickAt == null ? null : Timestamp.from(clickAt))
                .addValue("w", window).addValue("u", user == null ? null : user.toString()), UUID.class);
        if (ins.isEmpty()) return new RecordResult(false, false, null, null);
        return new RecordResult(true, campaignId != null, campaignId, campaignName(t, campaignId));
    }

    /** A refund is a negative row credited to the same campaign as the original payment (if it was attributed). */
    @Transactional
    public RecordResult recordRefund(TenantId t, UUID user, String originalRef, String externalRef, long amountCents,
                                     Instant refundedAt, String source) {
        if (amountCents <= 0 || amountCents > MAX_CENTS) throw bad("The refund amount must be positive.", "AMOUNT_INVALID");
        List<Map<String, Object>> orig = named.queryForList("""
            SELECT email, preference_id, currency, campaign_id, campaign_contact_id FROM mkt_conversions
            WHERE tenant_id = :t AND external_ref = :r AND kind = 'PAYMENT'
            """, new MapSqlParameterSource("t", t.getValue()).addValue("r", originalRef == null ? "" : originalRef.trim()));
        if (orig.isEmpty()) throw new HandyFlowException("The original payment has not been recorded, so this refund cannot be matched.", HttpStatus.NOT_FOUND, "ORIGINAL_NOT_FOUND");
        Map<String, Object> o = orig.get(0);
        String ref = blank(externalRef) == null ? "refund-" + originalRef.trim() + "-" + UUID.randomUUID() : externalRef.trim();
        Instant at = refundedAt == null ? Instant.now() : refundedAt;
        UUID campaignId = (UUID) o.get("campaign_id");

        List<UUID> ins = named.queryForList("""
            INSERT INTO mkt_conversions (id, tenant_id, external_ref, kind, original_ref, email, preference_id, amount_cents, currency,
                paid_at, source, campaign_id, campaign_contact_id, created_by)
            VALUES (:id, :t, :ref, 'REFUND', :orig, :e, :p, :a, :cur, :at, :src, :c, :cc, CAST(:u AS uuid))
            ON CONFLICT (tenant_id, external_ref) DO NOTHING RETURNING id
            """, new MapSqlParameterSource("id", UUID.randomUUID()).addValue("t", t.getValue()).addValue("ref", ref)
                .addValue("orig", originalRef.trim()).addValue("e", o.get("email")).addValue("p", o.get("preference_id"))
                .addValue("a", -amountCents).addValue("cur", ((String) o.get("currency")).trim()).addValue("at", Timestamp.from(at))
                .addValue("src", source).addValue("c", campaignId).addValue("cc", o.get("campaign_contact_id"))
                .addValue("u", user == null ? null : user.toString()), UUID.class);
        return new RecordResult(!ins.isEmpty(), campaignId != null, campaignId, campaignName(t, campaignId));
    }

    private List<Map<String, Object>> lastClick(TenantId t, UUID prefId, Instant paid, int window) {
        // Latest click at or before the payment, inside the window. Uses per-link last-click times where recorded,
        // otherwise the recipient's first-click time (campaigns sent before link tracking existed).
        return named.queryForList("""
            SELECT x.id, x.campaign_id, x.t FROM (
                SELECT cc.id, cc.campaign_id,
                       COALESCE((SELECT MAX(l.last_clicked_at) FROM mkt_link_clicks l
                                 WHERE l.campaign_contact_id = cc.id AND l.last_clicked_at <= :paid),
                                CASE WHEN cc.clicked_at <= :paid THEN cc.clicked_at END) AS t
                FROM mkt_campaign_contacts cc JOIN mkt_campaigns c ON c.id = cc.campaign_id
                WHERE cc.preference_id = :p AND c.tenant_id = :t AND cc.clicked_at IS NOT NULL
            ) x WHERE x.t IS NOT NULL AND x.t >= :from ORDER BY x.t DESC LIMIT 1
            """, new MapSqlParameterSource("p", prefId).addValue("t", t.getValue()).addValue("paid", Timestamp.from(paid))
                .addValue("from", Timestamp.from(paid.minus(window, ChronoUnit.DAYS))));
    }

    // ── Reports ──────────────────────────────────────────────────────────────

    @Transactional(readOnly = true)
    public Overview overview(TenantId t, int days, String currency) {
        int d = Math.max(1, Math.min(days, 365));
        String cur = currency == null || currency.isBlank() ? "ZAR" : currency.trim().toUpperCase();
        if (!CURRENCY.matcher(cur).matches()) throw bad("Invalid currency.", "CURRENCY_INVALID");
        Instant from = Instant.now().minus(d, ChronoUnit.DAYS);
        MapSqlParameterSource p = new MapSqlParameterSource("t", t.getValue()).addValue("cur", cur).addValue("from", Timestamp.from(from));

        Totals totals = named.queryForObject("""
            SELECT COALESCE(SUM(amount_cents),0) AS rev, COUNT(*) FILTER (WHERE kind = 'PAYMENT') AS orders,
                   COALESCE(SUM(amount_cents) FILTER (WHERE campaign_id IS NOT NULL),0) AS arev,
                   COUNT(*) FILTER (WHERE kind = 'PAYMENT' AND campaign_id IS NOT NULL) AS aorders
            FROM mkt_conversions WHERE tenant_id = :t AND currency = :cur AND paid_at >= :from
            """, p, (rs, i) -> new Totals(rs.getLong("rev"), rs.getLong("orders"), rs.getLong("arev"), rs.getLong("aorders")));

        Long other = named.queryForObject("SELECT COUNT(*) FROM mkt_conversions WHERE tenant_id = :t AND currency <> :cur AND paid_at >= :from", p, Long.class);

        List<CampaignRevenue> campaigns = named.query("""
            SELECT c.id, c.name, c.kind, c.cost_cents, agg.orders, agg.rev,
                   (SELECT COUNT(*) FROM mkt_campaign_contacts x WHERE x.campaign_id = c.id AND x.sent_at IS NOT NULL) AS sent,
                   (SELECT COUNT(*) FROM mkt_campaign_contacts x WHERE x.campaign_id = c.id AND x.clicked_at IS NOT NULL) AS clicked
            FROM (SELECT campaign_id, COUNT(*) FILTER (WHERE kind = 'PAYMENT') AS orders, SUM(amount_cents) AS rev
                  FROM mkt_conversions WHERE tenant_id = :t AND currency = :cur AND paid_at >= :from AND campaign_id IS NOT NULL
                  GROUP BY campaign_id) agg
            JOIN mkt_campaigns c ON c.id = agg.campaign_id AND c.tenant_id = :t
            ORDER BY agg.rev DESC LIMIT 50
            """, p, (rs, i) -> {
            long rev = rs.getLong("rev"), sent = rs.getLong("sent");
            Long cost = (Long) rs.getObject("cost_cents");
            return new CampaignRevenue((UUID) rs.getObject("id"), rs.getString("name"), rs.getString("kind"), sent, rs.getLong("clicked"),
                    rs.getLong("orders"), rev, sent > 0 ? Math.round(rev / (double) sent) : null, cost,
                    cost != null && cost > 0 ? Math.round(((rev - cost) / (double) cost) * 1000d) / 1000d : null);
        });

        LocalDate start = LocalDate.now(SAST).minusDays(d - 1L);
        Map<LocalDate, long[]> byDay = new TreeMap<>();
        for (int i = 0; i < d; i++) byDay.put(start.plusDays(i), new long[2]);
        p.addValue("sfrom", Timestamp.from(start.atStartOfDay(SAST).toInstant()));
        named.query("""
            SELECT CAST(paid_at AT TIME ZONE 'Africa/Johannesburg' AS date) AS d, SUM(amount_cents) AS rev,
                   SUM(CASE WHEN campaign_id IS NOT NULL THEN amount_cents ELSE 0 END) AS arev
            FROM mkt_conversions WHERE tenant_id = :t AND currency = :cur AND paid_at >= :sfrom GROUP BY 1
            """, p, rs -> {
            long[] v = byDay.get(rs.getDate("d").toLocalDate());
            if (v != null) { v[0] = rs.getLong("rev"); v[1] = rs.getLong("arev"); }
        });
        List<DayRevenue> series = new ArrayList<>();
        byDay.forEach((day, v) -> series.add(new DayRevenue(day.toString(), v[0], v[1])));

        return new Overview(d, cur, windowDays(t.getValue()), totals, other == null ? 0 : other, campaigns, series);
    }

    @Transactional(readOnly = true)
    public Page<ConversionRow> conversions(TenantId t, UUID campaignId, Pageable pageable) {
        Long exists = named.queryForObject("SELECT COUNT(*) FROM mkt_campaigns WHERE id = :id AND tenant_id = :t",
                new MapSqlParameterSource("id", campaignId).addValue("t", t.getValue()), Long.class);
        if (exists == null || exists == 0) throw new ResourceNotFoundException("Campaign", campaignId.toString());
        int size = Math.max(1, Math.min(pageable.getPageSize(), 100)), page = Math.max(0, pageable.getPageNumber());
        MapSqlParameterSource p = new MapSqlParameterSource("c", campaignId).addValue("t", t.getValue())
                .addValue("limit", size).addValue("offset", (long) page * size);
        Long total = named.queryForObject("SELECT COUNT(*) FROM mkt_conversions WHERE campaign_id = :c AND tenant_id = :t", p, Long.class);
        List<ConversionRow> rows = named.query("""
            SELECT id, kind, email, amount_cents, currency, paid_at, description, source, attributed_click_at
            FROM mkt_conversions WHERE campaign_id = :c AND tenant_id = :t ORDER BY paid_at DESC, id LIMIT :limit OFFSET :offset
            """, p, (rs, i) -> new ConversionRow((UUID) rs.getObject("id"), rs.getString("kind"), mask(rs.getString("email")),
                rs.getLong("amount_cents"), rs.getString("currency").trim(), rs.getTimestamp("paid_at").toInstant(),
                rs.getString("description"), rs.getString("source"),
                rs.getTimestamp("attributed_click_at") == null ? null : rs.getTimestamp("attributed_click_at").toInstant()));
        return new PageImpl<>(rows, PageRequest.of(page, size), total == null ? 0 : total);
    }

    // ── Helpers ──────────────────────────────────────────────────────────────

    private UUID prefId(TenantId t, String email) {
        List<UUID> r = named.queryForList("SELECT id FROM mkt_contact_preferences WHERE tenant_id = :t AND email = :e",
                new MapSqlParameterSource("t", t.getValue()).addValue("e", email), UUID.class);
        return r.isEmpty() ? null : r.get(0);
    }

    private String campaignName(TenantId t, UUID id) {
        if (id == null) return null;
        List<String> r = named.queryForList("SELECT name FROM mkt_campaigns WHERE id = :id AND tenant_id = :t",
                new MapSqlParameterSource("id", id).addValue("t", t.getValue()), String.class);
        return r.isEmpty() ? null : r.get(0);
    }

    private static String mask(String e) {
        int at = e.indexOf('@');
        return at <= 1 ? "***" + e.substring(Math.max(at, 0)) : e.charAt(0) + "***" + e.substring(at);
    }
    private static String blank(String s) { return s == null || s.isBlank() ? null : s.trim(); }
    private static String trunc(String s, int n) { return s == null ? null : s.length() > n ? s.substring(0, n) : s; }
    private static HandyFlowException bad(String m, String code) { return new HandyFlowException(m, HttpStatus.BAD_REQUEST, code); }
}