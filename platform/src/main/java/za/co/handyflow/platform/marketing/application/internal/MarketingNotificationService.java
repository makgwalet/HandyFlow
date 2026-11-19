package za.co.handyflow.platform.marketing.application.internal;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;
import org.springframework.web.util.HtmlUtils;
import za.co.handyflow.platform.shared.EmailService;
import za.co.handyflow.platform.shared.HandyFlowException;
import za.co.handyflow.platform.shared.TenantId;

import java.util.*;
import java.util.regex.Pattern;

/** Emails approvers when a campaign is submitted, and the submitter when it is approved or sent back. Never blocks the workflow. */
@Slf4j
@Service
@RequiredArgsConstructor
public class MarketingNotificationService {

    private final NamedParameterJdbcTemplate named;
    private final EmailService               emailService;

    @Value("${app.frontend.url:https://app.handyflow.co.za}")
    private String frontendUrl;

    private static final Pattern EMAIL = Pattern.compile("^[^@\\s,;]+@[^@\\s,;]+\\.[^@\\s,;]+$");

    // ── Settings ─────────────────────────────────────────────────────────────

    @Transactional(readOnly = true)
    public List<String> approvers(TenantId t) {
        List<String> r = named.queryForList("SELECT approver_emails FROM mkt_settings WHERE tenant_id = :t",
                new MapSqlParameterSource("t", t.getValue()), String.class);
        if (r.isEmpty() || r.get(0) == null || r.get(0).isBlank()) return List.of();
        return Arrays.stream(r.get(0).split(",")).map(String::trim).filter(s -> !s.isEmpty()).toList();
    }

    @Transactional
    public List<String> setApprovers(TenantId t, UUID user, List<String> emails) {
        LinkedHashSet<String> clean = new LinkedHashSet<>();
        for (String e : emails == null ? List.<String>of() : emails) {
            String n = e == null ? "" : e.toLowerCase().trim();
            if (n.isEmpty()) continue;
            if (!EMAIL.matcher(n).matches()) throw new HandyFlowException("'" + e.trim() + "' is not a valid email address.", HttpStatus.BAD_REQUEST, "INVALID_EMAIL");
            clean.add(n);
        }
        if (clean.size() > 20) throw new HandyFlowException("At most 20 approver addresses.", HttpStatus.BAD_REQUEST, "TOO_MANY");
        named.update("""
            INSERT INTO mkt_settings (tenant_id, approver_emails, updated_by, updated_at) VALUES (:t, :a, CAST(:u AS uuid), now())
            ON CONFLICT (tenant_id) DO UPDATE SET approver_emails = :a, updated_by = CAST(:u AS uuid), updated_at = now()
            """, new MapSqlParameterSource("t", t.getValue()).addValue("a", String.join(",", clean))
                .addValue("u", user == null ? null : user.toString()));
        return List.copyOf(clean);
    }

    // ── Triggers (called by MarketingLifecycleService) ───────────────────────

    public void submitted(TenantId t, UUID campaignId, UUID submitter) {
        afterCommit(() -> {
            try {
                Map<String, Object> c = campaign(t, campaignId);
                String who = userName(submitter);
                String body = card("Campaign awaiting review", "<strong>" + h(c.get("name")) + "</strong> (version " + c.get("content_version")
                        + ") was submitted for review" + (who == null ? "" : " by " + h(who)) + ".", null);
                for (String to : approvers(t)) emailService.send(to, "Campaign awaiting approval: " + oneLine(c.get("name")), body);
            } catch (Exception e) { log.warn("Submit notification failed campaign={}: {}", campaignId, e.getMessage()); }
        });
    }

    public void decided(TenantId t, UUID campaignId, UUID submitter, boolean approved, String note) {
        if (submitter == null) return;
        afterCommit(() -> {
            try {
                String to = userEmail(submitter);
                if (to == null) return;
                Map<String, Object> c = campaign(t, campaignId);
                String name = h(c.get("name"));
                String msg = approved ? "<strong>" + name + "</strong> was approved and can now be sent."
                        : "<strong>" + name + "</strong> was sent back with changes requested.";
                emailService.send(to, (approved ? "Approved: " : "Changes requested: ") + oneLine(c.get("name")),
                        card(approved ? "Campaign approved" : "Changes requested", msg, note));
            } catch (Exception e) { log.warn("Decision notification failed campaign={}: {}", campaignId, e.getMessage()); }
        });
    }

    // ── Helpers ──────────────────────────────────────────────────────────────

    private Map<String, Object> campaign(TenantId t, UUID id) {
        return named.queryForMap("SELECT name, content_version FROM mkt_campaigns WHERE id = :id AND tenant_id = :t",
                new MapSqlParameterSource("id", id).addValue("t", t.getValue()));
    }
    private String userName(UUID id) {
        if (id == null) return null;
        try { String n = named.queryForObject("SELECT TRIM(COALESCE(first_name,'') || ' ' || COALESCE(last_name,'')) FROM users WHERE id = :id",
                new MapSqlParameterSource("id", id), String.class); return n == null || n.isBlank() ? null : n; } catch (Exception e) { return null; }
    }
    private String userEmail(UUID id) {
        try { return named.queryForObject("SELECT email FROM users WHERE id = :id", new MapSqlParameterSource("id", id), String.class); } catch (Exception e) { return null; }
    }
    private String card(String title, String message, String note) {
        return "<!DOCTYPE html><html><body style=\"font-family:Arial,sans-serif;background:#F1F5F9;margin:0\"><div style=\"max-width:520px;margin:32px auto;background:#fff;border-radius:12px;padding:26px\">"
                + "<h2 style=\"margin:0 0 12px;color:#0F172A\">" + h(title) + "</h2><p style=\"color:#374151;font-size:14px;line-height:1.6\">" + message + "</p>"
                + (note == null || note.isBlank() ? "" : "<p style=\"background:#F8FAFC;border-radius:8px;padding:10px 12px;color:#374151;font-size:13px\">“" + h(note) + "”</p>")
                + "<p style=\"text-align:center;margin:22px 0 0\"><a href=\"" + frontendUrl + "\" style=\"background:#2563EB;color:#fff;padding:11px 24px;border-radius:8px;text-decoration:none;font-weight:600\">Open Marketing</a></p>"
                + "</div></body></html>";
    }
    private static String h(Object o) { return HtmlUtils.htmlEscape(String.valueOf(o)); }
    private static String oneLine(Object o) { return String.valueOf(o).replaceAll("[\\r\\n]", " "); }
    private static void afterCommit(Runnable r) {
        if (TransactionSynchronizationManager.isSynchronizationActive())
            TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() { @Override public void afterCommit() { r.run(); } });
        else r.run();
    }
}