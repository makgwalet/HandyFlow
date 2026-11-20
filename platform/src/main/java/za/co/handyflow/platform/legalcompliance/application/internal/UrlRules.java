package za.co.handyflow.platform.legalcompliance.application.internal;

import java.net.InetAddress;
import java.net.URI;
import java.util.Locale;

/**
 * Guards against a feed address being used to make the server call something internal (server-side request forgery). Two layers: {@link #validate} rejects an address
 * outright (plain http, credentials, odd ports, IP literals, internal-looking names), and {@link #isPublicAddress} is applied to every address the name resolves to at fetch time.
 * Redirects are never followed by the fetcher, so a public host cannot bounce the request to an internal one.
 */
public final class UrlRules {

    private UrlRules() {}

    public static final int MAX_LENGTH = 500;

    /** Returns the cleaned address, or throws IllegalArgumentException with a message safe to show the user. */
    public static String validate(String raw) {
        if (raw == null || raw.isBlank()) throw new IllegalArgumentException("A feed address is required");
        String s = raw.trim();
        if (s.length() > MAX_LENGTH) throw new IllegalArgumentException("The feed address is too long");
        URI u;
        try { u = new URI(s); } catch (Exception e) { throw new IllegalArgumentException("That is not a valid address"); }
        if (!"https".equalsIgnoreCase(u.getScheme())) throw new IllegalArgumentException("Only https feed addresses are accepted");
        if (u.getRawUserInfo() != null) throw new IllegalArgumentException("A feed address must not contain a username or password");
        if (u.getRawFragment() != null) throw new IllegalArgumentException("A feed address must not contain a fragment");
        String host = u.getHost();
        if (host == null || host.isBlank()) throw new IllegalArgumentException("That is not a valid address");
        if (u.getPort() != -1 && u.getPort() != 443) throw new IllegalArgumentException("Only the standard https port is accepted");
        String h = host.toLowerCase(Locale.ROOT);
        if (h.startsWith("[") || h.contains(":") || h.matches("[0-9.]+")) throw new IllegalArgumentException("Use a host name, not an IP address");
        if (!h.contains(".") || h.equals("localhost") || h.endsWith(".localhost") || h.endsWith(".local") || h.endsWith(".internal") || h.endsWith(".lan") || h.endsWith(".home.arpa"))
            throw new IllegalArgumentException("That host name is not a public address");
        return s;
    }

    /** False for loopback, private, link-local, carrier-grade NAT, unique-local, multicast and other non-public ranges. */
    public static boolean isPublicAddress(InetAddress a) {
        if (a.isAnyLocalAddress() || a.isLoopbackAddress() || a.isLinkLocalAddress() || a.isSiteLocalAddress() || a.isMulticastAddress()) return false;
        byte[] b = a.getAddress();
        if (b.length == 4) {
            int b0 = b[0] & 0xFF, b1 = b[1] & 0xFF, b2 = b[2] & 0xFF;
            if (b0 == 0) return false;                                      // 0.0.0.0/8
            if (b0 == 100 && b1 >= 64 && b1 <= 127) return false;           // 100.64.0.0/10 carrier-grade NAT
            if (b0 == 192 && b1 == 0 && b2 == 0) return false;              // 192.0.0.0/24
            if (b0 == 198 && (b1 == 18 || b1 == 19)) return false;          // 198.18.0.0/15 benchmarking
            if (b0 >= 240) return false;                                    // reserved and broadcast
            return true;
        }
        if (b.length == 16) {
            if ((b[0] & 0xFE) == 0xFC) return false;                        // fc00::/7 unique local
            boolean mapped = true;                                          // ::ffff:a.b.c.d is judged as the IPv4 address it wraps
            for (int i = 0; i < 10; i++) if (b[i] != 0) mapped = false;
            if (mapped && (b[10] & 0xFF) == 0xFF && (b[11] & 0xFF) == 0xFF) {
                try { return isPublicAddress(InetAddress.getByAddress(new byte[]{b[12], b[13], b[14], b[15]})); } catch (Exception e) { return false; }
            }
            return true;
        }
        return false;
    }
}
