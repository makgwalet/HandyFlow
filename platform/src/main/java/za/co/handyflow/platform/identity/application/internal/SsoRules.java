package za.co.handyflow.platform.identity.application.internal;

import org.springframework.http.HttpStatus;
import za.co.handyflow.platform.shared.HandyFlowException;

import java.net.URI;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.UUID;
import java.util.regex.Pattern;

/**
 * Pure rules for single sign-on settings and the sign-in decision. No secret passes through here: the client secret is named by an
 * environment variable (see {@link #cleanSecretRef}).
 */
public final class SsoRules {

    public static final int MAX_DOMAINS = 20;
    public static final int MAX_BREAK_GLASS = 10;

    private SsoRules() {}

    public enum Method { PASSWORD_OK, SSO_REQUIRED }

    /** The settings the sign-in decision needs. */
    public record Policy(boolean enabled, boolean enforce, List<String> allowedDomains) {}

    /** Mail providers anyone can register at: a company cannot claim these, or it would capture other people's sign-in. */
    static final Set<String> FREE_MAIL = Set.of("gmail.com", "googlemail.com", "outlook.com", "hotmail.com", "live.com", "msn.com", "yahoo.com", "ymail.com",
            "icloud.com", "me.com", "aol.com", "proton.me", "protonmail.com", "gmx.com", "mweb.co.za", "telkomsa.net", "vodamail.co.za", "webmail.co.za", "mail.com");

    private static final Pattern DOMAIN = Pattern.compile("^(?=.{1,253}$)([a-z0-9]([a-z0-9-]{0,61}[a-z0-9])?\\.)+[a-z]{2,63}$");
    private static final Pattern SECRET_REF = Pattern.compile("^[A-Z][A-Z0-9_]{0,99}$");
    /** A real host name ends in a label that starts with a letter; an address (127.0.0.1, 127.1, 10.1) ends in digits. */
    private static final Pattern NAME_ENDING = Pattern.compile("\\.[a-z][a-z0-9-]*$");

    private static HandyFlowException bad(String msg, String code) { return new HandyFlowException(msg, HttpStatus.BAD_REQUEST, code); }

    /**
     * The issuer address: https only, with a real host, no user info, query or fragment, and not an address inside the server's own network
     * (the server will later fetch from it). Returned exactly as given, trimmed: an issuer must match the provider's own value character for character.
     */
    public static String cleanIssuer(String raw) {
        String s = raw == null ? "" : raw.strip();
        if (s.isEmpty()) throw bad("The issuer address is needed.", "SSO_ISSUER");
        if (s.length() > 500) throw bad("The issuer address is too long.", "SSO_ISSUER");
        URI u;
        try { u = new URI(s); } catch (java.net.URISyntaxException e) { throw bad("The issuer address is not a valid address.", "SSO_ISSUER"); }
        if (!"https".equals(u.getScheme())) throw bad("The issuer address must start with https://", "SSO_ISSUER");
        String host = u.getHost();
        if (host == null || host.isEmpty()) throw bad("The issuer address needs a host name.", "SSO_ISSUER");
        if (u.getUserInfo() != null || u.getRawQuery() != null || u.getRawFragment() != null) throw bad("The issuer address cannot contain a user name, query or fragment.", "SSO_ISSUER");
        String h = host.toLowerCase(Locale.ROOT);
        if (h.equals("localhost") || h.endsWith(".localhost") || h.endsWith(".local") || h.endsWith(".internal") || h.contains(":") || !h.contains(".") || !NAME_ENDING.matcher(h).find()) {
            throw bad("The issuer address must be a public host name, not an IP address or an internal name.", "SSO_ISSUER");
        }
        return s;
    }

    public static String cleanClientId(String raw) {
        String s = raw == null ? "" : raw.strip();
        if (s.isEmpty()) throw bad("The client id is needed.", "SSO_CLIENT_ID");
        if (s.length() > 200) throw bad("The client id is too long.", "SSO_CLIENT_ID");
        for (char c : s.toCharArray()) if (Character.isWhitespace(c) || Character.isISOControl(c)) throw bad("The client id cannot contain spaces or control characters.", "SSO_CLIENT_ID");
        return s;
    }

    /** The name of the environment variable that holds the client secret, or null for none. Capitals, digits and underscores. */
    public static String cleanSecretRef(String raw) {
        String s = raw == null ? "" : raw.strip();
        if (s.isEmpty()) return null;
        if (!SECRET_REF.matcher(s).matches()) throw bad("The secret reference is the name of a server setting: capital letters, digits and underscores, starting with a letter.", "SSO_SECRET_REF");
        return s;
    }

    /** Domains in lower case without a leading @, each valid, none a public mail provider, no repeats, at most 20. */
    public static List<String> cleanDomains(List<String> raw) {
        Set<String> out = new LinkedHashSet<>();
        if (raw != null) {
            for (String r : raw) {
                if (r == null) continue;
                String d = r.strip().toLowerCase(Locale.ROOT);
                if (d.startsWith("@")) d = d.substring(1);
                if (d.isEmpty()) continue;
                if (!DOMAIN.matcher(d).matches()) throw bad("'" + r.strip() + "' is not a valid domain.", "SSO_DOMAIN");
                if (FREE_MAIL.contains(d)) throw bad(d + " is a public mail provider. Only domains your company owns can be used.", "SSO_DOMAIN_FREE_MAIL");
                out.add(d);
            }
        }
        if (out.size() > MAX_DOMAINS) throw bad("At most " + MAX_DOMAINS + " domains.", "SSO_DOMAIN_TOO_MANY");
        return new ArrayList<>(out);
    }

    /** The people allowed to keep using a password: no repeats, no empty entries, at most 10. */
    public static List<UUID> cleanBreakGlass(List<UUID> raw) {
        Set<UUID> out = new LinkedHashSet<>();
        if (raw != null) for (UUID id : raw) if (id != null) out.add(id);
        if (out.size() > MAX_BREAK_GLASS) throw bad("At most " + MAX_BREAK_GLASS + " people can keep a password.", "SSO_BREAK_GLASS_TOO_MANY");
        return new ArrayList<>(out);
    }

    /** The part after the last @, in lower case, or null when the address has none. */
    public static String domainOf(String email) {
        if (email == null) return null;
        int at = email.lastIndexOf('@');
        if (at < 0 || at == email.length() - 1) return null;
        return email.substring(at + 1).strip().toLowerCase(Locale.ROOT);
    }

    /**
     * How this person must sign in. Only when SSO is on and enforced, and the person's address is at an allowed domain, and they are not a
     * break-glass user, is a password refused. Everything else keeps working as before.
     */
    public static Method decide(Policy p, String email, boolean breakGlass) {
        if (p == null || !p.enabled() || !p.enforce()) return Method.PASSWORD_OK;
        if (breakGlass) return Method.PASSWORD_OK;
        String d = domainOf(email);
        if (d == null || p.allowedDomains() == null) return Method.PASSWORD_OK;
        return p.allowedDomains().contains(d) ? Method.SSO_REQUIRED : Method.PASSWORD_OK;
    }

    /**
     * Why enforcing cannot be switched on, or null when it is safe. At least one break-glass person must be able to sign in with a password,
     * and the person switching it on must not lock themselves out: they must be one of them, or have an address outside the domains.
     */
    public static String lockoutProblem(List<String> domains, List<UUID> breakGlass, UUID actor, String actorEmail) {
        if (breakGlass == null || breakGlass.isEmpty()) return "Choose at least one person who can still sign in with a password before enforcing single sign-on.";
        String d = domainOf(actorEmail);
        boolean actorCovered = d != null && domains != null && domains.contains(d);
        if (actorCovered && (actor == null || !breakGlass.contains(actor))) return "You sign in with an address at one of these domains. Include yourself among the people who can still use a password, or you could lock yourself out.";
        return null;
    }
}
