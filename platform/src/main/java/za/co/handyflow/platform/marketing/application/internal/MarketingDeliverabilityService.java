package za.co.handyflow.platform.marketing.application.internal;

import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import za.co.handyflow.platform.marketing.dto.DeliverabilityDtos.*;
import za.co.handyflow.platform.shared.HandyFlowException;
import za.co.handyflow.platform.shared.RateLimiter;
import za.co.handyflow.platform.shared.TenantId;

import javax.naming.Context;
import javax.naming.NameNotFoundException;
import javax.naming.NamingEnumeration;
import javax.naming.NamingException;
import javax.naming.directory.Attribute;
import javax.naming.directory.Attributes;
import javax.naming.directory.DirContext;
import javax.naming.directory.InitialDirContext;
import java.security.KeyFactory;
import java.security.interfaces.RSAPublicKey;
import java.security.spec.X509EncodedKeySpec;
import java.sql.Timestamp;
import java.time.Instant;
import java.util.*;
import java.util.concurrent.*;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * DNS-based sender authentication checks (SPF, DKIM, DMARC, MX) for the tenant's sending domain.
 * DNS lookups only: nothing is fetched over HTTP. Input is validated so only safe names reach the resolver.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class MarketingDeliverabilityService {

    private final NamedParameterJdbcTemplate named;
    private final ObjectMapper               om;
    private final RateLimiter                rateLimiter;

    private static final Pattern DOMAIN   = Pattern.compile("^(?=.{4,253}$)([a-z0-9]([a-z0-9-]{0,61}[a-z0-9])?\\.)+[a-z]{2,63}$");
    private static final Pattern SELECTOR = Pattern.compile("^[a-z0-9]([a-z0-9._-]{0,61}[a-z0-9])?$");
    private static final Pattern SPF_ALL  = Pattern.compile("(?:^|\\s)([+\\-~?]?)all(?:\\s|$)", Pattern.CASE_INSENSITIVE);

    private static final Set<String> FREE_MAIL = Set.of("gmail.com", "googlemail.com", "yahoo.com", "yahoo.co.za", "outlook.com",
            "hotmail.com", "live.com", "icloud.com", "mweb.co.za", "telkomsa.net", "webmail.co.za", "vodamail.co.za");
    private static final Set<String> SECOND_LEVEL = Set.of("co.za", "org.za", "net.za", "ac.za", "gov.za", "web.za",
            "co.uk", "org.uk", "com.au", "co.nz");
    private static final List<String> PROBE = List.of("default", "google", "selector1", "selector2", "s1", "s2",
            "k1", "k2", "mail", "dkim", "smtp", "mandrill");
    private static final Map<String, String> SENDERS = Map.of(
            "amazonses.com", "Amazon SES", "sendgrid.net", "SendGrid", "mailgun.org", "Mailgun", "_spf.google.com", "Google Workspace",
            "spf.protection.outlook.com", "Microsoft 365", "servers.mcsv.net", "Mailchimp", "spf.mandrillapp.com", "Mandrill",
            "spf.mtasv.net", "Postmark", "mailjet.com", "Mailjet");

    private static final int MAX_SPF_FETCHES = 15;

    // ── State and settings ───────────────────────────────────────────────────

    @Transactional(readOnly = true)
    public State state(TenantId t) {
        List<State> rows = named.query("SELECT sending_domain, dkim_selectors, CAST(deliverability_result AS text) AS r FROM mkt_settings WHERE tenant_id = :t",
                new MapSqlParameterSource("t", t.getValue()), (rs, i) -> {
                    String sel = rs.getString("dkim_selectors");
                    List<String> selectors = sel == null || sel.isBlank() ? List.of() : Arrays.stream(sel.split(",")).map(String::trim).filter(s -> !s.isEmpty()).toList();
                    Result res = null;
                    try { if (rs.getString("r") != null) res = om.readValue(rs.getString("r"), Result.class); } catch (Exception ignored) { }
                    return new State(rs.getString("sending_domain"), selectors, res);
                });
        return rows.isEmpty() ? new State(null, List.of(), null) : rows.get(0);
    }

    @Transactional
    public State save(TenantId t, UUID user, String rawDomain, List<String> rawSelectors) {
        String domain = normaliseDomain(rawDomain);
        LinkedHashSet<String> sel = new LinkedHashSet<>();
        for (String s : rawSelectors == null ? List.<String>of() : rawSelectors) {
            if (s == null || s.isBlank()) continue;
            String n = s.trim().toLowerCase().replaceFirst("\\._domainkey.*$", "");   // tolerate pasting the full record name
            if (!SELECTOR.matcher(n).matches()) throw bad("'" + s.trim() + "' is not a valid DKIM selector (letters, digits, dots, dashes).");
            sel.add(n);
        }
        if (sel.size() > 5) throw bad("Enter at most 5 DKIM selectors.");
        String selectors = sel.isEmpty() ? null : String.join(",", sel);
        named.update("""
            INSERT INTO mkt_settings (tenant_id, sending_domain, dkim_selectors, updated_by, updated_at)
            VALUES (:t, CAST(:d AS text), CAST(:s AS text), CAST(:u AS uuid), now())
            ON CONFLICT (tenant_id) DO UPDATE SET
                deliverability_result = CASE WHEN mkt_settings.sending_domain IS NOT DISTINCT FROM CAST(:d AS text)
                                              AND mkt_settings.dkim_selectors IS NOT DISTINCT FROM CAST(:s AS text)
                                             THEN mkt_settings.deliverability_result ELSE NULL END,
                deliverability_checked_at = CASE WHEN mkt_settings.sending_domain IS NOT DISTINCT FROM CAST(:d AS text)
                                              AND mkt_settings.dkim_selectors IS NOT DISTINCT FROM CAST(:s AS text)
                                             THEN mkt_settings.deliverability_checked_at ELSE NULL END,
                sending_domain = CAST(:d AS text), dkim_selectors = CAST(:s AS text), updated_by = CAST(:u AS uuid), updated_at = now()
            """, new MapSqlParameterSource("t", t.getValue()).addValue("d", domain).addValue("s", selectors)
                .addValue("u", user == null ? null : user.toString()));
        return state(t);
    }

    // ── Check ────────────────────────────────────────────────────────────────

    @Transactional
    public Result check(TenantId t) {
        State st = state(t);
        if (st.domain() == null) throw bad("Save your sending domain first.");
        if (!rateLimiter.tryConsume("mkt:dnscheck:" + t.getValue(), 20, 3_600_000L)) {
            throw new HandyFlowException("Too many checks. Try again in a while.", HttpStatus.TOO_MANY_REQUESTS, "DNS_CHECK_RATE_LIMIT");
        }
        String d = st.domain();
        List<Check> out = new ArrayList<>();

        if (FREE_MAIL.contains(d)) {
            out.add(ck("freemail", "FAIL", "Free mail domain",
                    d + " is a free mail service. You can't add authentication records to it, and Gmail and Yahoo reject or junk bulk mail sent from these domains.",
                    "Send from a domain you own, such as yourbusiness.co.za."));
        }
        spf(d, out);
        dmarc(d, out);
        dkim(d, st.selectors(), out);
        mx(d, out);

        int pass = 0, warn = 0, fail = 0;
        for (Check c : out) switch (c.status()) { case "PASS" -> pass++; case "WARN" -> warn++; case "FAIL" -> fail++; default -> { } }
        Result res = new Result(d, Instant.now(), st.selectors(), out, pass, warn, fail);
        try {
            named.update("UPDATE mkt_settings SET deliverability_result = CAST(:r AS jsonb), deliverability_checked_at = now() WHERE tenant_id = :t",
                    new MapSqlParameterSource("t", t.getValue()).addValue("r", om.writeValueAsString(res)));
        } catch (Exception e) { throw new IllegalStateException("Could not store result", e); }
        log.info("Deliverability check tenant={} domain={} pass={} warn={} fail={}", t, d, pass, warn, fail);
        return res;
    }

    // ── SPF ──────────────────────────────────────────────────────────────────

    private void spf(String d, List<Check> out) {
        Dns r = lookup(d, "TXT");
        if (r.failed()) { out.add(dnsFailed("spf", "SPF", d)); return; }
        List<String> recs = r.values().stream().filter(v -> v.trim().toLowerCase().startsWith("v=spf1")).toList();
        if (recs.isEmpty()) {
            out.add(ck("spf", "FAIL", "SPF", "No SPF record found on " + d + ".",
                    "Add a TXT record on " + d + " such as:  v=spf1 include:<your email provider's SPF host> ~all"));
            return;
        }
        if (recs.size() > 1) {
            out.add(ck("spf", "FAIL", "SPF", "There are " + recs.size() + " SPF records. Receivers treat more than one as an error, so SPF fails for every message.",
                    "Merge them into a single TXT record that lists all your senders."));
            return;
        }
        String rec = recs.get(0).trim();
        boolean redirect = rec.toLowerCase().contains("redirect=");
        Matcher m = SPF_ALL.matcher(rec);
        String q = m.find() ? m.group(1) : null;
        if (q == null && !redirect) out.add(ck("spf", "WARN", "SPF", "The record has no “all” ending, so it doesn't say what to do with unlisted senders.", "End it with ~all (or -all once you are sure every sender is listed)."));
        else if ("+".equals(q)) out.add(ck("spf", "FAIL", "SPF", "The record ends with +all, which authorises anyone on the internet to send as your domain.", "Change it to ~all or -all."));
        else if ("?".equals(q)) out.add(ck("spf", "WARN", "SPF", "The record ends with ?all (neutral), which gives receivers no guidance.", "Change it to ~all or -all."));
        else out.add(ck("spf", "PASS", "SPF", "One valid SPF record" + ("-".equals(q) ? " that rejects unlisted senders (-all)." : redirect && q == null ? " that delegates to another domain." : " that soft-fails unlisted senders (~all)."), null));

        Set<String> includes = new LinkedHashSet<>();
        int[] fetches = {0};
        int lookups = spfLookups(rec, 0, new HashSet<>(Set.of(d)), fetches, includes);
        boolean capped = fetches[0] >= MAX_SPF_FETCHES;
        String atLeast = capped ? "at least " : "";
        if (lookups > 10) out.add(ck("spf-lookups", "FAIL", "SPF lookup limit", "Your SPF needs " + atLeast + lookups + " DNS lookups. The limit is 10, and above it receivers treat SPF as a permanent error.",
                "Remove unused includes, or replace several providers' includes with one."));
        else if (lookups >= 8) out.add(ck("spf-lookups", "WARN", "SPF lookup limit", "Your SPF uses " + atLeast + lookups + " of the 10 allowed DNS lookups. Adding another sender may push it over.", null));
        else out.add(ck("spf-lookups", "PASS", "SPF lookup limit", "Your SPF uses " + atLeast + lookups + " of the 10 allowed DNS lookups.", null));

        Set<String> known = new LinkedHashSet<>();
        for (String inc : includes) SENDERS.forEach((k, v) -> { if (inc.contains(k)) known.add(v); });
        out.add(ck("spf-senders", "INFO", "Senders in your SPF",
                known.isEmpty() ? "No well-known email provider is listed. If you send through one (SES, SendGrid, Mailgun and so on), add the include it gives you."
                        : "Recognised: " + String.join(", ", known) + ". Make sure the provider you actually send through is on this list.", null));
    }

    private int spfLookups(String rec, int depth, Set<String> seen, int[] fetches, Set<String> includes) {
        int n = 0;
        for (String tok : rec.split("\\s+")) {
            String t = tok.toLowerCase().replaceFirst("^[+\\-~?]", "");
            String target = null;
            if (t.startsWith("include:")) { n++; target = t.substring(8); includes.add(target); }
            else if (t.startsWith("redirect=")) { n++; target = t.substring(9); includes.add(target); }
            else if (t.equals("a") || t.startsWith("a:") || t.startsWith("a/") || t.equals("mx") || t.startsWith("mx:") || t.startsWith("mx/")
                    || t.startsWith("ptr") || t.startsWith("exists:")) n++;
            if (target != null && depth < 5 && DOMAIN.matcher(target).matches() && seen.add(target) && fetches[0] < MAX_SPF_FETCHES) {
                fetches[0]++;
                for (String v : lookup(target, "TXT").values()) {
                    if (v.trim().toLowerCase().startsWith("v=spf1")) n += spfLookups(v.trim(), depth + 1, seen, fetches, includes);
                }
            }
        }
        return n;
    }

    // ── DMARC ────────────────────────────────────────────────────────────────

    private void dmarc(String d, List<Check> out) {
        String name = d; List<String> recs = List.of(); boolean failed = false; String where = d;
        while (name != null) {
            Dns r = lookup("_dmarc." + name, "TXT");
            if (r.failed()) { failed = true; break; }
            recs = r.values().stream().filter(v -> v.trim().toLowerCase().startsWith("v=dmarc1")).toList();
            if (!recs.isEmpty()) { where = name; break; }
            name = parentOf(name);
        }
        if (failed) { out.add(dnsFailed("dmarc", "DMARC", d)); return; }
        if (recs.isEmpty()) {
            out.add(ck("dmarc", "FAIL", "DMARC", "No DMARC record found. Gmail and Yahoo require one from bulk senders.",
                    "Add a TXT record at _dmarc." + d + " such as:  v=DMARC1; p=none; rua=mailto:dmarc@" + d + "  (start with p=none, then tighten)."));
            return;
        }
        if (recs.size() > 1) { out.add(ck("dmarc", "FAIL", "DMARC", "There are " + recs.size() + " DMARC records at _dmarc." + where + ". Receivers ignore DMARC when there is more than one.", "Keep a single record.")); return; }
        Map<String, String> tags = tags(recs.get(0));
        String p = tags.getOrDefault("p", "").toLowerCase();
        String at = where.equals(d) ? "" : " (inherited from " + where + ")";
        switch (p) {
            case "reject", "quarantine" -> out.add(ck("dmarc", "PASS", "DMARC", "DMARC policy is p=" + p + at + ".", null));
            case "none" -> out.add(ck("dmarc", "WARN", "DMARC", "DMARC is set to p=none" + at + ": it satisfies Gmail and Yahoo's minimum but doesn't stop anyone spoofing your domain.",
                    "Once your reports show only legitimate senders, move to p=quarantine."));
            default -> out.add(ck("dmarc", "FAIL", "DMARC", "The DMARC record has a missing or invalid policy (p=)" + at + ".", "Set p=none, p=quarantine or p=reject."));
        }
        if (tags.get("rua") == null || tags.get("rua").isBlank())
            out.add(ck("dmarc-rua", "WARN", "DMARC reports", "No aggregate report address (rua=). You won't see who is sending as your domain.", "Add rua=mailto:dmarc@" + d + "."));
        try {
            if (tags.get("pct") != null && Integer.parseInt(tags.get("pct").trim()) < 100)
                out.add(ck("dmarc-pct", "INFO", "DMARC coverage", "pct=" + tags.get("pct") + ": the policy applies to only that share of messages.", null));
        } catch (NumberFormatException ignored) { }
    }

    private static String parentOf(String d) {
        int dot = d.indexOf('.');
        if (dot < 0) return null;
        String p = d.substring(dot + 1);
        return !p.contains(".") || SECOND_LEVEL.contains(p) ? null : p;
    }

    // ── DKIM ─────────────────────────────────────────────────────────────────

    private void dkim(String d, List<String> selectors, List<Check> out) {
        if (!selectors.isEmpty()) {
            for (String s : selectors) dkimOne(d, s, true, out);
            return;
        }
        List<String> found = new ArrayList<>();
        ExecutorService ex = Executors.newFixedThreadPool(6);
        try {
            Map<String, Future<Dns>> futures = new LinkedHashMap<>();
            for (String s : PROBE) futures.put(s, ex.submit(() -> lookup(s + "._domainkey." + d, "TXT")));
            for (var e : futures.entrySet()) {
                try { Dns r = e.getValue().get(20, TimeUnit.SECONDS); if (r.values().stream().anyMatch(v -> v.toLowerCase().contains("p="))) found.add(e.getKey()); }
                catch (Exception ignored) { }
            }
        } finally { ex.shutdownNow(); }
        if (found.isEmpty()) {
            out.add(ck("dkim", "WARN", "DKIM", "No DKIM key was found at the common selector names, and you haven't entered your own. Most providers use a selector of their own.",
                    "Find the selector in your email provider's domain-authentication screen, enter it above and run the check again."));
        } else {
            for (String s : found) dkimOne(d, s, false, out);
        }
    }

    private void dkimOne(String d, String sel, boolean entered, List<Check> out) {
        String id = "dkim:" + sel, title = "DKIM (" + sel + ")", host = sel + "._domainkey." + d;
        Dns r = lookup(host, "TXT");
        if (r.failed()) { out.add(dnsFailed(id, title, host)); return; }
        Optional<String> rec = r.values().stream().filter(v -> v.toLowerCase().contains("p=") || v.toLowerCase().startsWith("v=dkim1")).findFirst();
        if (rec.isEmpty()) {
            out.add(ck(id, "FAIL", title, "No DKIM key found at " + host + ".", "Publish the DNS record your provider shows for this selector (a TXT, or a CNAME they give you)."));
            return;
        }
        Map<String, String> tags = tags(rec.get());
        String p = tags.getOrDefault("p", "").replaceAll("\\s", "");
        String note = entered ? "" : " (found by checking common selector names)";
        if (p.isEmpty()) { out.add(ck(id, "FAIL", title, "The key at " + host + " is empty, which means it was revoked." + note, "Publish a current key from your provider.")); return; }
        if ("ed25519".equalsIgnoreCase(tags.get("k"))) { out.add(ck(id, "PASS", title, "Ed25519 DKIM key found at " + host + "." + note, null)); return; }
        Integer bits = keyBits(p);
        if (bits == null) out.add(ck(id, "WARN", title, "A key exists at " + host + " but it could not be read." + note, "Re-publish the record exactly as your provider shows it."));
        else if (bits < 1024) out.add(ck(id, "FAIL", title, "The DKIM key is only " + bits + " bits and will be rejected by many receivers." + note, "Generate a 2048-bit key with your provider."));
        else if (bits < 2048) out.add(ck(id, "WARN", title, "The DKIM key is " + bits + " bits. It works, but 2048 bits is the current recommendation." + note, "Rotate to a 2048-bit key."));
        else out.add(ck(id, "PASS", title, "A valid " + bits + "-bit DKIM key is published at " + host + "." + note, null));
    }

    private static Integer keyBits(String b64) {
        try {
            var key = KeyFactory.getInstance("RSA").generatePublic(new X509EncodedKeySpec(Base64.getDecoder().decode(b64)));
            return ((RSAPublicKey) key).getModulus().bitLength();
        } catch (Exception e) { return null; }
    }

    // ── MX ───────────────────────────────────────────────────────────────────

    private void mx(String d, List<Check> out) {
        Dns r = lookup(d, "MX");
        if (r.failed()) { out.add(dnsFailed("mx", "Mail servers (MX)", d)); return; }
        if (r.values().isEmpty()) out.add(ck("mx", "WARN", "Mail servers (MX)", d + " has no MX records, so it can't receive replies or bounce messages, and some filters distrust such domains.",
                "Point the domain's MX records at your mailbox provider."));
        else out.add(ck("mx", "PASS", "Mail servers (MX)", d + " can receive mail (" + r.values().size() + " MX record(s)).", null));
    }

    // ── DNS plumbing ─────────────────────────────────────────────────────────

    private record Dns(List<String> values, boolean failed) {}

    private Dns lookup(String name, String type) {
        DirContext ctx = null;
        try {
            Hashtable<String, String> env = new Hashtable<>();
            env.put(Context.INITIAL_CONTEXT_FACTORY, "com.sun.jndi.dns.DnsContextFactory");
            env.put("com.sun.jndi.dns.timeout.initial", "3000");
            env.put("com.sun.jndi.dns.timeout.retries", "2");
            ctx = new InitialDirContext(env);
            Attributes at = ctx.getAttributes("dns:/" + name, new String[]{type});
            Attribute a = at.get(type);
            List<String> out = new ArrayList<>();
            if (a != null) {
                NamingEnumeration<?> e = a.getAll();
                while (e.hasMore()) { String v = String.valueOf(e.next()); out.add("TXT".equals(type) ? unquote(v) : v.trim()); }
            }
            return new Dns(out, false);
        } catch (NameNotFoundException e) {
            return new Dns(List.of(), false);
        } catch (NamingException e) {
            log.warn("DNS {} lookup for {} failed: {}", type, name, e.getMessage());
            return new Dns(List.of(), true);
        } finally {
            if (ctx != null) try { ctx.close(); } catch (NamingException ignored) { }
        }
    }

    /** A TXT with several 255-byte chunks arrives as "chunk1" "chunk2"; join them. */
    private static String unquote(String raw) {
        if (!raw.contains("\"")) return raw;
        StringBuilder sb = new StringBuilder();
        Matcher m = Pattern.compile("\"((?:[^\"\\\\]|\\\\.)*)\"").matcher(raw);
        while (m.find()) sb.append(m.group(1).replace("\\\"", "\""));
        return sb.toString();
    }

    private static Map<String, String> tags(String rec) {
        Map<String, String> tags = new HashMap<>();
        for (String part : rec.split(";")) {
            int eq = part.indexOf('=');
            if (eq > 0) tags.put(part.substring(0, eq).trim().toLowerCase(), part.substring(eq + 1).trim());
        }
        return tags;
    }

    private static String normaliseDomain(String raw) {
        if (raw == null || raw.isBlank()) return null;
        String d = raw.trim().toLowerCase();
        int at = d.lastIndexOf('@');
        if (at >= 0) d = d.substring(at + 1);
        d = d.replaceFirst("\\.$", "");
        if (!DOMAIN.matcher(d).matches()) throw bad("Enter a domain such as yourbusiness.co.za, not a web address.");
        return d;
    }

    private static Check ck(String id, String status, String title, String detail, String fix) { return new Check(id, status, title, detail, fix); }
    private static Check dnsFailed(String id, String title, String name) {
        return ck(id, "WARN", title, "Could not look up DNS for " + name + " right now.", "Try again in a few minutes.");
    }
    private static HandyFlowException bad(String m) { return new HandyFlowException(m, HttpStatus.BAD_REQUEST, "DELIVERABILITY_INVALID"); }
}