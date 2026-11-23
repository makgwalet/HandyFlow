// security/application/internal/WebhookSendGuard.java
package za.co.handyflow.platform.security.application.internal;

import java.net.InetAddress;
import java.net.URI;
import java.net.UnknownHostException;
import java.time.Duration;
import java.util.Optional;

/**
 * The checks made at the moment a webhook is sent (not just when the URL was saved): the name must still resolve, and every address it
 * resolves to must be a public one. Pure apart from the injected resolver, so it can be tested offline.
 *
 * <p>Limit, stated plainly: the HTTP client resolves the name again when it connects, so a hostile DNS server that answers differently
 * the second time can still slip through the gap between this check and the connection. This narrows that gap to milliseconds; closing
 * it fully needs the connection pinned to the checked address, which the HTTP client in use cannot do with TLS name checking intact.</p>
 */
final class WebhookSendGuard {

    static final Duration CONNECT_TIMEOUT = Duration.ofSeconds(5);
    static final Duration READ_TIMEOUT = Duration.ofSeconds(10);
    static final int MAX_STORED_BODY = 2000;

    /** Resolves a host name to all its addresses. */
    interface Resolver { InetAddress[] resolve(String host) throws UnknownHostException; }

    private WebhookSendGuard() {}

    /** @return empty if the endpoint may be called now, else why not (for the delivery log) */
    static Optional<String> problem(String url, Resolver resolver) {
        Optional<String> written = WebhookUrlRules.problem(url);
        if (written.isPresent()) return written;
        String host = URI.create(url.trim()).getHost();
        InetAddress[] all;
        try {
            all = resolver.resolve(stripBrackets(host));
        } catch (UnknownHostException | RuntimeException e) {
            return Optional.of("The endpoint's host name could not be resolved.");
        }
        if (all == null || all.length == 0) return Optional.of("The endpoint's host name could not be resolved.");
        for (InetAddress a : all) {
            if (!isPublic(a)) return Optional.of("The endpoint resolves to a non-public address.");
        }
        return Optional.empty();
    }

    private static String stripBrackets(String h) { return h.startsWith("[") && h.endsWith("]") ? h.substring(1, h.length() - 1) : h; }

    /** True only for an address that belongs to the public internet. Anything unusual is refused. */
    static boolean isPublic(InetAddress a) {
        if (a == null) return false;
        if (a.isAnyLocalAddress() || a.isLoopbackAddress() || a.isLinkLocalAddress() || a.isSiteLocalAddress() || a.isMulticastAddress()) return false;
        byte[] b = a.getAddress();
        if (b.length == 4) return publicV4(b[0] & 0xFF, b[1] & 0xFF, b[2] & 0xFF);
        if (b.length != 16) return false;
        // IPv4-mapped (::ffff:a.b.c.d) and IPv4-compatible (::a.b.c.d) addresses are judged as the IPv4 address they carry.
        boolean mapped = true, compat = true;
        for (int i = 0; i < 10; i++) { if (b[i] != 0) { mapped = false; compat = false; } }
        if (b[10] != (byte) 0xFF || b[11] != (byte) 0xFF) mapped = false;
        if (b[10] != 0 || b[11] != 0) compat = false;
        if (mapped || compat) return publicV4(b[12] & 0xFF, b[13] & 0xFF, b[14] & 0xFF);
        int first = b[0] & 0xFF, second = b[1] & 0xFF;
        if ((first & 0xFE) == 0xFC) return false;                    // fc00::/7 unique local
        if (first == 0xFE && (second & 0xC0) == 0x80) return false;  // fe80::/10 link local
        if (first == 0xFE && (second & 0xC0) == 0xC0) return false;  // fec0::/10 site local (deprecated)
        if (first == 0x20 && second == 0x01 && b[2] == 0x0D && b[3] == (byte) 0xB8) return false; // 2001:db8::/32 documentation
        if (first == 0x00 || first == 0x01) return false;            // ::/8 and 100::/8 (discard) are not public destinations
        return true;
    }

    private static boolean publicV4(int a, int b, int c) {
        if (a == 0 || a == 10 || a == 127) return false;
        if (a == 100 && b >= 64 && b <= 127) return false;           // carrier-grade NAT
        if (a == 169 && b == 254) return false;
        if (a == 172 && b >= 16 && b <= 31) return false;
        if (a == 192 && b == 168) return false;
        if (a == 192 && b == 0 && c == 0) return false;              // IETF protocol assignments
        if (a == 192 && b == 0 && c == 2) return false;              // documentation
        if (a == 198 && (b == 18 || b == 19)) return false;          // benchmarking
        if (a == 198 && b == 51 && c == 100) return false;           // documentation
        if (a == 203 && b == 0 && c == 113) return false;            // documentation
        return a < 224;                                              // multicast, reserved and broadcast are not destinations
    }

    /** The reply body is kept for support, but a hostile endpoint must not be able to fill the database with it. */
    static String clip(String body) {
        if (body == null) return null;
        return body.length() <= MAX_STORED_BODY ? body : body.substring(0, MAX_STORED_BODY);
    }
}
