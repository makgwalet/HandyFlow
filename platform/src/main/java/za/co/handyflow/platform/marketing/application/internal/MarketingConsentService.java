package za.co.handyflow.platform.marketing.application.internal;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.http.HttpStatus;
import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.web.util.HtmlUtils;
import za.co.handyflow.platform.marketing.domain.model.MktCampaign;
import za.co.handyflow.platform.marketing.domain.repository.MktCampaignRepository;
import za.co.handyflow.platform.marketing.dto.StudioDtos.*;
import za.co.handyflow.platform.shared.*;

import java.net.URI;
import java.nio.charset.StandardCharsets;
import java.util.*;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/** Content checker, server-side preview, test sends and the reusable block library. */
@Slf4j
@Service
@RequiredArgsConstructor
public class MarketingContentService {

    private final NamedParameterJdbcTemplate named;
    private final MarketingConsentService    consentService;
    private final EmailService               emailService;
    private final RateLimiter                rateLimiter;
    private final MktCampaignRepository      campaignRepo;

    private static final Set<String> KNOWN_TOKENS = Set.of(
            "{{first_name}}", "{{name}}", "{{email}}", "{{company_name}}", "{{unsubscribe_url}}");
    private static final Pattern TOKEN    = Pattern.compile("\\{\\{[^}]*\\}\\}");
    private static final Pattern HREF     = Pattern.compile("(?i)href\\s*=\\s*([\"'])(.*?)\\1");
    private static final Pattern IMG      = Pattern.compile("(?i)<img\\b[^>]*>");
    private static final Pattern ALT      = Pattern.compile("(?i)\\balt\\s*=");
    private static final Pattern IMG_SRC  = Pattern.compile("(?i)\\bsrc\\s*=\\s*[\"']([^\"']*)[\"']");
    private static final Pattern BAD_TAGS = Pattern.compile("(?i)<\\s*(script|form|iframe|object|embed)\\b");
    private static final Pattern EMAIL    = Pattern.compile("^[^@\\s]+@[^@\\s]+\\.[^@\\s]+$");
    private static final List<String> SPAM_WORDS = List.of(
            "100% free", "act now", "buy now", "click here", "guarantee", "winner", "urgent", "risk free",
            "no obligation", "cash bonus", "$$$", "make money");

    private static final int MAX_TEST_RECIPIENTS = 5;
    private static final Map<String, Integer> ORDER = Map.of("ERROR", 0, "WARN", 1, "INFO", 2);

    // ── Token substitution (shared with MarketingService.personalise) ────────

    public static String applyTokens(String template, String firstName, String fullName, String email,
                                     String company, String unsubscribeUrl, boolean html) {
        if (template == null) return "";
        return template
                .replace("{{first_name}}",      esc(html, firstName))
                .replace("{{name}}",            esc(html, fullName))
                .replace("{{email}}",           esc(html, email))
                .replace("{{company_name}}",    esc(html, company))
                .replace("{{unsubscribe_url}}", unsubscribeUrl == null ? "" : unsubscribeUrl);
    }

    private static String esc(boolean html, String s) {
        String v = s == null ? "" : s;
        return html ? HtmlUtils.htmlEscape(v) : v;
    }

    // ── Lint ─────────────────────────────────────────────────────────────────

    public List<LintIssue> lint(String subject, String html) {
        List<LintIssue> out = new ArrayList<>();
        String s = subject == null ? "" : subject.trim();
        String h = html == null ? "" : html;

        if (s.isEmpty()) out.add(i("ERROR", "SUBJECT_EMPTY", "The subject line is empty."));
        else {
            if (s.length() > 100) out.add(i("WARN", "SUBJECT_LONG", "Subject is " + s.length() + " characters; most inboxes cut it off around 60–70."));
            else if (s.length() > 70) out.add(i("INFO", "SUBJECT_LONG", "Subject is " + s.length() + " characters and may be truncated on mobile."));
            long letters = s.chars().filter(Character::isLetter).count();
            long upper = s.chars().filter(Character::isUpperCase).count();
            if (letters >= 8 && upper * 10 > letters * 7) out.add(i("WARN", "SUBJECT_CAPS", "The subject is mostly capital letters, which spam filters penalise."));
            if (s.chars().filter(ch -> ch == '!').count() > 1) out.add(i("WARN", "SUBJECT_EXCLAIM", "Multiple exclamation marks in the subject can trigger spam filters."));
            String lower = (s + " " + stripTags(h)).toLowerCase();
            List<String> found = SPAM_WORDS.stream().filter(lower::contains).toList();
            if (!found.isEmpty()) out.add(i("WARN", "SPAM_WORDS", "Wording that often triggers spam filters: " + String.join(", ", found) + "."));
            if (s.contains("{{unsubscribe_url}}")) out.add(i("WARN", "TOKEN_IN_SUBJECT", "{{unsubscribe_url}} only makes sense in the email body."));
        }

        Set<String> tokens = new LinkedHashSet<>();
        Matcher tm = TOKEN.matcher(s + " " + h);
        while (tm.find()) tokens.add(tm.group());
        List<String> unknown = tokens.stream().filter(t -> !KNOWN_TOKENS.contains(t)).toList();
        if (!unknown.isEmpty()) out.add(i("WARN", "TOKEN_UNKNOWN", "Unrecognised placeholder(s) will be sent as literal text: " + String.join(", ", unknown) + "."));

        if (h.isBlank()) { out.add(i("ERROR", "BODY_EMPTY", "The email body is empty.")); return sort(out); }

        int bytes = h.getBytes(StandardCharsets.UTF_8).length;
        if (bytes > 102_400) out.add(i("WARN", "BODY_LARGE", "The HTML is " + (bytes / 1024) + " KB. Gmail clips messages over ~102 KB, hiding the unsubscribe link behind “View entire message”."));

        Matcher bad = BAD_TAGS.matcher(h);
        Set<String> badTags = new LinkedHashSet<>();
        while (bad.find()) badTags.add(bad.group(1).toLowerCase());
        if (!badTags.isEmpty()) out.add(i("WARN", "BLOCKED_TAGS", "Email clients strip or block: <" + String.join(">, <", badTags) + ">."));

        // Images
        int noAlt = 0; Set<String> httpImg = new LinkedHashSet<>();
        Matcher im = IMG.matcher(h);
        while (im.find()) {
            String tag = im.group();
            if (!ALT.matcher(tag).find()) noAlt++;
            Matcher src = IMG_SRC.matcher(tag);
            if (src.find() && src.group(1).toLowerCase().startsWith("http://")) httpImg.add(shorten(src.group(1)));
        }
        if (noAlt > 0) out.add(i("WARN", "IMG_ALT", noAlt + " image(s) have no alt text; many clients block images by default."));
        if (!httpImg.isEmpty()) out.add(i("WARN", "IMG_INSECURE", "Image(s) loaded over http://: " + String.join(", ", httpImg)));

        // Links
        int total = 0, untracked = 0;
        Set<String> seen = new HashSet<>();
        Matcher lm = HREF.matcher(h);
        while (lm.find()) {
            total++;
            String raw = lm.group(0);
            String v = lm.group(2).trim();
            if (!raw.startsWith("href=\"")) untracked++;
            if (v.equals("{{unsubscribe_url}}")) continue;
            String key = v.toLowerCase();
            if (!seen.add(key)) continue;

            String lower = v.toLowerCase();
            if (v.isEmpty() || v.equals("#")) { out.add(i("WARN", "LINK_EMPTY", "A link has an empty or “#” destination.")); continue; }
            if (lower.startsWith("javascript:")) { out.add(i("ERROR", "LINK_JS", "A javascript: link will not work in email and is treated as unsafe.")); continue; }
            if (lower.startsWith("mailto:") || lower.startsWith("tel:")) continue;
            if (v.contains("{{")) { out.add(i("WARN", "LINK_TOKEN", "A link contains a placeholder (" + shorten(v) + "). Values are inserted without URL-encoding.")); continue; }
            try {
                URI u = URI.create(v);
                if (u.getScheme() == null) out.add(i("ERROR", "LINK_RELATIVE", "Relative link “" + shorten(v) + "” will break in email. Use the full https:// address."));
                else if (u.getScheme().equalsIgnoreCase("http")) out.add(i("WARN", "LINK_INSECURE", "Insecure link: " + shorten(v)));
                else if (!u.getScheme().equalsIgnoreCase("https")) out.add(i("WARN", "LINK_SCHEME", "Unusual link scheme: " + shorten(v)));
            } catch (IllegalArgumentException e) {
                out.add(i("ERROR", "LINK_MALFORMED", "Malformed link: " + shorten(v)));
            }
        }
        if (total > 30) out.add(i("WARN", "LINK_MANY", "The email has " + total + " links; very link-heavy emails are more likely to be filtered."));
        if (untracked > 0) out.add(i("INFO", "LINK_UNTRACKED", untracked + " link(s) don't use the standard href=\"…\" format and won't be click-tracked (they still work)."));
        if (!h.contains("{{unsubscribe_url}}")) out.add(i("INFO", "FOOTER_AUTO", "No unsubscribe placeholder found. A sender-identification and unsubscribe footer is added automatically."));
        out.add(i("INFO", "LINKS_NOT_FETCHED", "Links are checked for format only. They are not opened, so a dead page will not be detected."));
        return sort(out);
    }

    // ── Preview ──────────────────────────────────────────────────────────────

    public ContentPreviewResponse preview(TenantId tenantId, ContentPreviewRequest req) {
        String name = req.sampleName() != null && !req.sampleName().isBlank() ? req.sampleName().trim() : "Thabo Modise";
        String first = name.split("\\s+")[0];
        String company = tenantName(tenantId);
        String email = "thabo@example.co.za";

        String subject = applyTokens(req.subject(), first, name, email, company, "#", false);
        String html = applyTokens(req.htmlBody(), first, name, email, company, "#", true);
        if (req.htmlBody() != null && !req.htmlBody().contains("{{unsubscribe_url}}")) {
            String contact = req.replyTo() != null && !req.replyTo().isBlank()
                    ? " Contact: " + HtmlUtils.htmlEscape(req.replyTo()) + "." : "";
            html += "<br><hr><p style=\"font-size:11px;color:#94A3B8;text-align:center\">Sent by "
                    + HtmlUtils.htmlEscape(company) + "." + contact
                    + " You received this because you opted in to receive marketing emails. <a href=\"#\">Unsubscribe</a></p>";
        }
        return new ContentPreviewResponse(subject, html);
    }

    // ── Test send ────────────────────────────────────────────────────────────

    public TestSendResult testSend(TenantId tenantId, UUID userId, UUID campaignId, List<String> emails) {
        MktCampaign c = campaignRepo.findByIdAndTenantId(campaignId, tenantId)
                .orElseThrow(() -> new ResourceNotFoundException("Campaign", campaignId.toString()));

        Set<String> targets = new LinkedHashSet<>();
        if (emails != null) for (String e : emails) {
            if (e == null) continue;
            String n = e.toLowerCase().trim();
            if (!EMAIL.matcher(n).matches()) throw new HandyFlowException("'" + e.trim() + "' is not a valid email address.", HttpStatus.BAD_REQUEST, "INVALID_EMAIL");
            targets.add(n);
        }
        if (targets.isEmpty()) throw new HandyFlowException("Enter at least one email address.", HttpStatus.BAD_REQUEST, "NO_RECIPIENTS");
        if (targets.size() > MAX_TEST_RECIPIENTS) throw new HandyFlowException("Send tests to at most " + MAX_TEST_RECIPIENTS + " addresses at a time.", HttpStatus.BAD_REQUEST, "TOO_MANY_RECIPIENTS");

        if (!rateLimiter.tryConsume("mkt:testsend:" + tenantId.getValue() + ":" + userId, 10, 3_600_000L)) {
            throw new HandyFlowException("Too many test sends. Try again in a while.", HttpStatus.TOO_MANY_REQUESTS, "TEST_SEND_RATE_LIMIT");
        }

        String company = tenantName(tenantId);
        String banner = "<div style=\"background:#FEF3C7;border:1px solid #FDE68A;color:#92400E;padding:10px 14px;"
                + "font:13px Arial,sans-serif;margin-bottom:16px\"><strong>TEST SEND</strong> — campaign “"
                + HtmlUtils.htmlEscape(c.getName()) + "”, version " + c.getContentVersion()
                + ". Links are not tracked and the unsubscribe link is disabled.</div>";

        List<String> sent = new ArrayList<>();
        List<Refused> refused = new ArrayList<>();
        for (String to : targets) {
            if (consentService.isSuppressed(tenantId, to)) {
                refused.add(new Refused(to, "On the suppression list"));
                continue;
            }
            String subject = "[TEST] " + applyTokens(c.getSubject(), "Thabo", "Thabo Modise", to, company, "#", false);
            String body = banner + applyTokens(c.getHtmlBody(), "Thabo", "Thabo Modise", to, company, "#", true);
            emailService.send(to, subject, body);    // async; failures are logged, not reported here
            sent.add(to);
        }
        log.info("Test send campaign={} tenant={} by={} sent={} refused={}", campaignId, tenantId, userId, sent.size(), refused.size());
        return new TestSendResult(sent, refused);
    }

    // ── Content blocks ───────────────────────────────────────────────────────

    public List<BlockResponse> listBlocks(TenantId tenantId) {
        return named.query("SELECT id, name, category, html, updated_at FROM mkt_content_blocks "
                        + "WHERE tenant_id = :t AND deleted_at IS NULL ORDER BY lower(name)",
                new MapSqlParameterSource("t", tenantId.getValue()),
                (rs, n) -> new BlockResponse((UUID) rs.getObject("id"), rs.getString("name"), rs.getString("category"),
                        rs.getString("html"), rs.getTimestamp("updated_at").toInstant()));
    }

    public BlockResponse createBlock(TenantId tenantId, UUID user, BlockRequest req) {
        UUID id = UUID.randomUUID();
        try {
            named.update("INSERT INTO mkt_content_blocks (id, tenant_id, name, category, html, created_by) "
                            + "VALUES (:id, :t, :n, :c, :h, CAST(:u AS uuid))",
                    new MapSqlParameterSource("id", id).addValue("t", tenantId.getValue()).addValue("n", req.name().trim())
                            .addValue("c", blank(req.category())).addValue("h", req.html())
                            .addValue("u", user == null ? null : user.toString()));
        } catch (DuplicateKeyException e) { throw nameTaken(); }
        return getBlock(tenantId, id);
    }

    public BlockResponse updateBlock(TenantId tenantId, UUID id, BlockRequest req) {
        int n;
        try {
            n = named.update("UPDATE mkt_content_blocks SET name = :n, category = :c, html = :h, updated_at = now() "
                            + "WHERE id = :id AND tenant_id = :t AND deleted_at IS NULL",
                    new MapSqlParameterSource("id", id).addValue("t", tenantId.getValue()).addValue("n", req.name().trim())
                            .addValue("c", blank(req.category())).addValue("h", req.html()));
        } catch (DuplicateKeyException e) { throw nameTaken(); }
        if (n == 0) throw new ResourceNotFoundException("Block", id.toString());
        return getBlock(tenantId, id);
    }

    public void deleteBlock(TenantId tenantId, UUID id) {
        int n = named.update("UPDATE mkt_content_blocks SET deleted_at = now() WHERE id = :id AND tenant_id = :t AND deleted_at IS NULL",
                new MapSqlParameterSource("id", id).addValue("t", tenantId.getValue()));
        if (n == 0) throw new ResourceNotFoundException("Block", id.toString());
    }

    private BlockResponse getBlock(TenantId tenantId, UUID id) {
        return listBlocks(tenantId).stream().filter(b -> b.id().equals(id)).findFirst()
                .orElseThrow(() -> new ResourceNotFoundException("Block", id.toString()));
    }

    // ── Helpers ──────────────────────────────────────────────────────────────

    private String tenantName(TenantId tenantId) {
        try {
            return named.queryForObject("SELECT name FROM tenants WHERE id = :t",
                    new MapSqlParameterSource("t", tenantId.getValue()), String.class);
        } catch (Exception e) { return "Your Business"; }
    }

    private static List<LintIssue> sort(List<LintIssue> l) {
        l.sort(Comparator.comparingInt(x -> ORDER.getOrDefault(x.level(), 3)));
        return l;
    }
    private static LintIssue i(String level, String code, String msg) { return new LintIssue(level, code, msg); }
    private static String shorten(String s) { return s.length() > 80 ? s.substring(0, 77) + "…" : s; }
    private static String stripTags(String s) { return s.replaceAll("(?s)<[^>]*>", " "); }
    private static String blank(String s) { return s == null || s.isBlank() ? null : s.trim(); }
    private static HandyFlowException nameTaken() {
        return new HandyFlowException("A block with this name already exists.", HttpStatus.CONFLICT, "BLOCK_NAME_EXISTS");
    }
}