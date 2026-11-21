package za.co.handyflow.platform.security.application.internal;

import java.net.URI;
import java.util.Locale;
import java.util.Optional;

/**
 * Is this a sensible place to send a customer's webhook? Pure and offline: it checks the
 * address as written. It does NOT resolve DNS, so a public name pointing at a private address
 * (rebinding) still gets through; that needs a check at send time.
 */
final class WebhookUrlRules {

    private WebhookUrlRules() {}

    /** @return empty if acceptable, else a message for the user */
    static Optional<String> problem(String url) {
        URI u;
        try {
            u = URI.create(url == null ? "" : url.trim());
        } catch (IllegalArgumentException e) {
            return Optional.of("The endpoint URL is not a valid address.");
        }
        if (u.getScheme() == null || !u.getScheme().equalsIgnoreCase("https")) {
            return Optional.of("The endpoint must use https.");
        }
        if (u.getUserInfo() != null) return Optional.of("The endpoint must not contain a username or password.");
        String host = u.getHost();
        if (host == null || host.isBlank()) return Optional.of("The endpoint URL has no host.");
        String h = host.toLowerCase(Locale.ROOT);
        if (h.endsWith(".")) h = h.substring(0, h.length() - 1);
        if (h.equals("localhost") || h.endsWith(".localhost") || h.endsWith(".local") || h.endsWith(".internal")
                || h.endsWith(".svc") || h.endsWith(".cluster.local") || !h.contains(".") && !h.contains(":")) {
            return Optional.of("The endpoint must be a public internet address.");
        }
        if (isPrivateIpLiteral(h)) return Optional.of("The endpoint must be a public internet address.");
        return Optional.empty();
    }

    private static boolean isPrivateIpLiteral(String h) {
        String x = h.startsWith("[") && h.endsWith("]") ? h.substring(1, h.length() - 1) : h;
        if (x.contains(":")) { // IPv6 literal
            String l = x.toLowerCase(Locale.ROOT);
            return l.equals("::1") || l.equals("::") || l.startsWith("fc") || l.startsWith("fd") || l.startsWith("fe8")
                    || l.startsWith("fe9") || l.startsWith("fea") || l.startsWith("feb") || l.startsWith("::ffff:");
        }
        String[] p = x.split("\\.");
        if (p.length != 4) return false;
        int[] o = new int[4];
        try {
            for (int i = 0; i < 4; i++) o[i] = Integer.parseInt(p[i]);
        } catch (NumberFormatException e) {
            return false; // a name, not an address
        }
        return o[0] == 10 || o[0] == 127 || o[0] == 0
                || (o[0] == 169 && o[1] == 254)
                || (o[0] == 172 && o[1] >= 16 && o[1] <= 31)
                || (o[0] == 192 && o[1] == 168)
                || (o[0] == 100 && o[1] >= 64 && o[1] <= 127);
    }
}
