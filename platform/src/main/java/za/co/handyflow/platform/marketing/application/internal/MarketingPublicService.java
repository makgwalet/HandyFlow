package za.co.handyflow.platform.marketing.application.internal;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.util.HtmlUtils;
import za.co.handyflow.platform.marketing.domain.model.MktContactPreference;
import za.co.handyflow.platform.marketing.domain.repository.MktContactPreferenceRepository;
import za.co.handyflow.platform.marketing.dto.FormDtos.*;
import za.co.handyflow.platform.shared.*;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.SecureRandom;
import java.sql.Timestamp;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.*;
import java.util.regex.Pattern;

/**
 * Unauthenticated flows: signup form, double opt-in confirmation and the preference centre.
 * Rules: nothing is opted in until the emailed confirmation is POSTed; the form never reveals whether an
 * address is already known or suppressed; suppression is never bypassed.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class MarketingPublicService {

    private final NamedParameterJdbcTemplate     named;
    private final ObjectMapper                   om;
    private final RateLimiter                    rateLimiter;
    private final EmailService                   emailService;
    private final MarketingService               marketing;
    private final MarketingConsentService        consentService;
    private final MktContactPreferenceRepository preferenceRepo;

    @Value("${app.frontend.url:https://app.handyflow.co.za}")
    private String frontendUrl;

    private static final SecureRandom RND = new SecureRandom();
    private static final Pattern EMAIL = Pattern.compile("^[^@\\s]+@[^@\\s]+\\.[^@\\s]+$");
    private static final long HOUR = 3_600_000L;

    private record FormRow(UUID id, UUID tenantId, String name, String title, String description, String button,
                           String consent, String success, boolean collectName, List<UUID> topicIds, String business, String slug) {}

    // ── Form ─────────────────────────────────────────────────────────────────

    @Transactional(readOnly = true)
    public PublicForm form(String slug) {
        FormRow f = load(slug);
        return new PublicForm(f.slug(), f.business(), f.title(), f.description(), f.button(), f.consent(), f.collectName(),
                topicsOf(f.tenantId(), f.topicIds()));
    }

    /** Always completes silently for honeypot hits, known subscribers and suppressed addresses. */
    @Transactional
    public void submit(String slug, SubmitRequest req, String ip) {
        if (!req.consent()) throw new HandyFlowException("Please tick the box to confirm you agree.", HttpStatus.BAD_REQUEST, "CONSENT_REQUIRED");
        if (req.website() != null && !req.website().isBlank()) return;   // honeypot

        FormRow f = load(slug);
        String email = req.email() == null ? "" : req.email().toLowerCase().trim();
        if (email.length() > 320 || !EMAIL.matcher(email).matches()) {
            throw new HandyFlowException("Please enter a valid email address.", HttpStatus.BAD_REQUEST, "INVALID_EMAIL");
        }
        if (!rateLimiter.tryConsume("mkt:form:ip:" + ip, 60, HOUR)
                || !rateLimiter.tryConsume("mkt:form:em:" + f.tenantId() + ":" + email, 3, HOUR)) {
            throw new HandyFlowException("Too many attempts. Please try again later.", HttpStatus.TOO_MANY_REQUESTS, "RATE_LIMITED");
        }

        TenantId tid = TenantId.of(f.tenantId());
        if (consentService.isSuppressed(tid, email)) return;
        Optional<MktContactPreference> existing = preferenceRepo.findByTenantIdAndEmail(tid, email);
        if (existing.isPresent() && existing.get().isEmailOptedIn()) return;

        String name = f.collectName() && req.name() != null ? req.name().replaceAll("\\p{Cntrl}", " ").trim() : null;
        if (name != null && name.length() > 150) name = name.substring(0, 150);
        if (name != null && name.isBlank()) name = null;

        Set<UUID> allowed = new HashSet<>(f.topicIds());
        List<UUID> picked = req.topicIds() == null ? List.of() : req.topicIds().stream().filter(allowed::contains).distinct().toList();

        String token = newToken();
        named.update("""
            INSERT INTO mkt_optin_requests (id, tenant_id, form_id, email, name, token_hash, topic_ids, consent_text, ip, expires_at)
            VALUES (:id, :t, :f, :e, :n, :h, CAST(:topics AS jsonb), :c, :ip, :exp)
            """, new MapSqlParameterSource("id", UUID.randomUUID()).addValue("t", f.tenantId()).addValue("f", f.id())
                .addValue("e", email).addValue("n", name).addValue("h", sha256(token)).addValue("topics", json(picked))
                .addValue("c", f.consent()).addValue("ip", ip == null ? null : ip.length() > 64 ? ip.substring(0, 64) : ip)
                .addValue("exp", Timestamp.from(Instant.now().plus(48, ChronoUnit.HOURS))));

        emailService.send(email, "Confirm your subscription to " + f.business().replaceAll("[\\r\\n]", " "),
                confirmEmail(f.business(), name, frontendUrl + "/confirm/" + token));
        log.info("Opt-in confirmation sent form={} tenant={}", f.id(), f.tenantId());
    }

    // ── Confirmation ─────────────────────────────────────────────────────────

    @Transactional
    public ConfirmResult confirm(String token) {
        String hash = sha256(token == null ? "" : token);
        List<Map<String, Object>> rows = named.queryForList("""
            SELECT r.id, r.tenant_id, r.email, r.name, CAST(r.topic_ids AS text) AS picked, r.consent_text, r.ip, r.created_at,
                   r.expires_at, r.confirmed_at, f.name AS form_name, f.slug, CAST(f.topic_ids AS text) AS form_topics,
                   (SELECT name FROM tenants WHERE id = r.tenant_id) AS business
            FROM mkt_optin_requests r JOIN mkt_forms f ON f.id = r.form_id WHERE r.token_hash = :h
            """, new MapSqlParameterSource("h", hash));
        if (rows.isEmpty()) throw new HandyFlowException("This confirmation link is not valid.", HttpStatus.NOT_FOUND, "CONFIRM_INVALID");
        Map<String, Object> r = rows.get(0);

        UUID tenant = (UUID) r.get("tenant_id");
        TenantId tid = TenantId.of(tenant);
        String email = (String) r.get("email");
        String business = r.get("business") == null ? "this business" : (String) r.get("business");

        if (r.get("confirmed_at") != null) {
            return new ConfirmResult(business, true, tokenFor(tid, email));
        }
        if (((Timestamp) r.get("expires_at")).toInstant().isBefore(Instant.now())) {
            throw new HandyFlowException("This confirmation link has expired. Please sign up again.", HttpStatus.GONE, "CONFIRM_EXPIRED");
        }
        if (consentService.isSuppressed(tid, email)) {
            throw new HandyFlowException("This subscription can't be completed. Please contact " + business + " directly.", HttpStatus.CONFLICT, "CONTACT_SUPPRESSED");
        }
        // Claim the request first so a double-click can't run this twice.
        int claimed = named.update("UPDATE mkt_optin_requests SET confirmed_at = now() WHERE id = :id AND confirmed_at IS NULL",
                new MapSqlParameterSource("id", r.get("id")));
        if (claimed == 0) return new ConfirmResult(business, true, tokenFor(tid, email));

        Instant submitted = ((Timestamp) r.get("created_at")).toInstant();
        String evidence = trunc("Web form \"" + r.get("form_name") + "\" (" + r.get("slug") + "). Submitted " + submitted
                + (r.get("ip") != null ? " from IP " + r.get("ip") : "") + "; confirmed by email link " + Instant.now()
                + ". Consent wording shown: \"" + r.get("consent_text") + "\"", 1000);

        var pref = marketing.optIn(tid, null, email, (String) r.get("name"), "WEB_FORM", evidence);

        List<UUID> formTopics = uuids((String) r.get("form_topics"));
        Set<UUID> picked = new HashSet<>(uuids((String) r.get("picked")));
        for (UUID topic : formTopics) setTopic(tenant, pref.id(), topic, picked.contains(topic), "WEB_FORM");

        return new ConfirmResult(business, false, tokenFor(tid, email));
    }

    // ── Preference centre ────────────────────────────────────────────────────

    @Transactional(readOnly = true)
    public PrefView preferences(String token) {
        MktContactPreference p = byToken(token);
        UUID tenant = p.getTenantId().getValue();
        List<PrefTopic> topics = named.query("""
            SELECT t.id, t.name, t.description, COALESCE(c.subscribed, true) AS subscribed
            FROM mkt_topics t LEFT JOIN mkt_contact_topic_prefs c ON c.topic_id = t.id AND c.preference_id = :p
            WHERE t.tenant_id = :t AND t.deleted_at IS NULL AND t.visible = true ORDER BY lower(t.name)
            """, new MapSqlParameterSource("p", p.getId()).addValue("t", tenant),
                (rs, i) -> new PrefTopic((UUID) rs.getObject("id"), rs.getString("name"), rs.getString("description"), rs.getBoolean("subscribed")));
        String first = p.getName() == null || p.getName().isBlank() ? null : p.getName().trim().split("\\s+")[0];
        return new PrefView(tenantName(tenant), mask(p.getEmail()), first, p.isEmailOptedIn(), topics);
    }

    @Transactional
    public PrefView updatePreferences(String token, PrefUpdate req) {
        MktContactPreference p = byToken(token);
        if (!p.isEmailOptedIn()) {
            throw new HandyFlowException("You're not subscribed to marketing emails, so there is nothing to change.", HttpStatus.CONFLICT, "NOT_OPTED_IN");
        }
        UUID tenant = p.getTenantId().getValue();
        Set<UUID> keep = req.topicIds() == null ? Set.of() : new HashSet<>(req.topicIds());
        List<UUID> visible = named.queryForList("SELECT id FROM mkt_topics WHERE tenant_id = :t AND deleted_at IS NULL AND visible = true",
                new MapSqlParameterSource("t", tenant), UUID.class);
        for (UUID topic : visible) setTopic(tenant, p.getId(), topic, keep.contains(topic), "PREFERENCE_CENTRE");
        return preferences(token);
    }

    // ── Helpers ──────────────────────────────────────────────────────────────

    private void setTopic(UUID tenant, UUID prefId, UUID topic, boolean subscribed, String source) {
        named.update("""
            INSERT INTO mkt_contact_topic_prefs (preference_id, topic_id, tenant_id, subscribed, source)
            VALUES (:p, :tp, :t, :s, :src)
            ON CONFLICT (preference_id, topic_id) DO UPDATE SET subscribed = :s, source = :src, updated_at = now()
            """, new MapSqlParameterSource("p", prefId).addValue("tp", topic).addValue("t", tenant)
                .addValue("s", subscribed).addValue("src", source));
        if (!subscribed) cancelPending(tenant, prefId, topic);
    }

    /** A topic opt-out stops that contact's not-yet-sent emails for campaigns about the topic. */
    private void cancelPending(UUID tenant, UUID prefId, UUID topic) {
        MapSqlParameterSource p = new MapSqlParameterSource("p", prefId).addValue("tp", topic).addValue("t", tenant);
        named.update("""
            UPDATE mkt_campaigns c SET suppressed_count = c.suppressed_count + 1, updated_at = now()
            WHERE c.topic_id = :tp AND c.tenant_id = :t AND EXISTS (SELECT 1 FROM mkt_campaign_contacts cc
                WHERE cc.campaign_id = c.id AND cc.preference_id = :p AND cc.status = 'PENDING')
            """, p);
        named.update("""
            UPDATE mkt_campaign_contacts cc SET status = 'SUPPRESSED', error_message = 'TOPIC_OPT_OUT'
            FROM mkt_campaigns c WHERE cc.campaign_id = c.id AND c.topic_id = :tp AND cc.preference_id = :p AND cc.status = 'PENDING'
            """, p);
        named.update("""
            UPDATE mkt_send_queue q SET status = 'DEAD', error_message = 'TOPIC_OPT_OUT', processed_at = now()
            FROM mkt_campaigns c, mkt_contact_preferences pr
            WHERE q.campaign_id = c.id AND c.topic_id = :tp AND pr.id = :p AND q.tenant_id = :t
              AND q.to_email = pr.email AND q.status = 'PENDING'
            """, p);
    }

    private FormRow load(String slug) {
        List<FormRow> rows = named.query("""
            SELECT f.id, f.tenant_id, f.name, f.title, f.description, f.button_label, f.consent_text, f.success_message,
                   f.collect_name, CAST(f.topic_ids AS text) AS topic_ids, f.slug, (SELECT name FROM tenants WHERE id = f.tenant_id) AS business
            FROM mkt_forms f WHERE f.slug = :s AND f.status = 'ACTIVE' AND f.deleted_at IS NULL
            """, new MapSqlParameterSource("s", slug == null ? "" : slug),
                (rs, i) -> new FormRow((UUID) rs.getObject("id"), (UUID) rs.getObject("tenant_id"), rs.getString("name"), rs.getString("title"),
                        rs.getString("description"), rs.getString("button_label"), rs.getString("consent_text"), rs.getString("success_message"),
                        rs.getBoolean("collect_name"), uuids(rs.getString("topic_ids")),
                        rs.getString("business") == null ? "Our team" : rs.getString("business"), rs.getString("slug")));
        if (rows.isEmpty()) throw new HandyFlowException("This form is not available.", HttpStatus.NOT_FOUND, "FORM_UNAVAILABLE");
        return rows.get(0);
    }

    private List<PublicTopic> topicsOf(UUID tenant, List<UUID> ids) {
        if (ids.isEmpty()) return List.of();
        return named.query("SELECT id, name, description FROM mkt_topics WHERE tenant_id = :t AND deleted_at IS NULL AND id IN (:ids) ORDER BY lower(name)",
                new MapSqlParameterSource("t", tenant).addValue("ids", ids),
                (rs, i) -> new PublicTopic((UUID) rs.getObject("id"), rs.getString("name"), rs.getString("description")));
    }

    private MktContactPreference byToken(String token) {
        return preferenceRepo.findByUnsubscribeToken(token == null ? "" : token)
                .orElseThrow(() -> new HandyFlowException("This link is invalid or has expired.", HttpStatus.NOT_FOUND, "PREF_TOKEN_INVALID"));
    }

    private String tokenFor(TenantId tid, String email) {
        return preferenceRepo.findByTenantIdAndEmail(tid, email).map(MktContactPreference::getUnsubscribeToken).orElse(null);
    }

    private String tenantName(UUID tenant) {
        try { return named.queryForObject("SELECT name FROM tenants WHERE id = :t", new MapSqlParameterSource("t", tenant), String.class); }
        catch (Exception e) { return "this business"; }
    }

    private String confirmEmail(String business, String name, String link) {
        String hi = name != null && !name.isBlank() ? "Hi " + HtmlUtils.htmlEscape(name.split("\\s+")[0]) + "," : "Hi,";
        String b = HtmlUtils.htmlEscape(business);
        return "<!DOCTYPE html><html><head><meta charset=\"UTF-8\"></head><body style=\"font-family:Arial,sans-serif;background:#F1F5F9;margin:0;padding:0\">"
                + "<div style=\"max-width:520px;margin:40px auto;background:#fff;border-radius:12px;padding:28px\">"
                + "<h2 style=\"margin:0 0 14px;color:#0F172A\">Confirm your subscription</h2>"
                + "<p style=\"color:#374151;font-size:14px;line-height:1.6\">" + hi + "</p>"
                + "<p style=\"color:#374151;font-size:14px;line-height:1.6\">Someone asked to receive marketing emails from " + b
                + " using this address. To finish, please confirm below.</p>"
                + "<p style=\"text-align:center;margin:26px 0\"><a href=\"" + link + "\" style=\"background:#2563EB;color:#fff;padding:12px 26px;border-radius:8px;text-decoration:none;font-weight:600\">Confirm subscription</a></p>"
                + "<p style=\"color:#94A3B8;font-size:12px;line-height:1.6\">If this wasn't you, ignore this email and you will not be subscribed. The link expires in 48 hours.</p>"
                + "</div></body></html>";
    }

    private List<UUID> uuids(String json) {
        try { return om.readValue(json == null ? "[]" : json, new TypeReference<List<UUID>>() {}); } catch (Exception e) { return List.of(); }
    }
    private String json(Object o) { try { return om.writeValueAsString(o); } catch (Exception e) { throw new IllegalStateException(e); } }
    private static String newToken() { byte[] b = new byte[32]; RND.nextBytes(b); return Base64.getUrlEncoder().withoutPadding().encodeToString(b); }
    private static String sha256(String s) {
        try { return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(s.getBytes(StandardCharsets.UTF_8))); }
        catch (Exception e) { throw new IllegalStateException(e); }
    }
    private static String mask(String email) {
        int at = email.indexOf('@');
        return at <= 1 ? "***" + email.substring(Math.max(at, 0)) : email.charAt(0) + "***" + email.substring(at);
    }
    private static String trunc(String s, int n) { return s.length() > n ? s.substring(0, n) : s; }
}