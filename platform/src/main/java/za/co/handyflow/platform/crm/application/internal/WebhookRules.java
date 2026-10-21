package za.co.handyflow.platform.crm.application.internal;

import java.net.InetAddress;
import java.net.URI;
import java.net.URISyntaxException;
import java.nio.charset.StandardCharsets;
import java.security.InvalidKeyException;
import java.security.NoSuchAlgorithmException;
import java.util.List;
import java.util.Locale;
import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import za.co.handyflow.platform.crm.domain.model.WebhookEvent;

/** What a webhook address must be, how a message is signed, and which addresses we refuse to call. Pure, no database or network. */
final class WebhookRules {
    static final int MAX_WEBHOOKS = 10, URL_MAX = 500;

    private WebhookRules() {}

    /** Checks the name, address and events the way the screen does. */
    static void check(String name, String url, List<WebhookEvent> events) {
        if (name == null || name.isBlank()) throw new IllegalArgumentException("Name the webhook");
        if (name.strip().length() > 120) throw new IllegalArgumentException("The name can be at most 120 characters");
        checkUrl(url);
        if (events == null || events.isEmpty()) throw new IllegalArgumentException("Choose at least one event");
        if (events.contains(WebhookEvent.PING)) throw new IllegalArgumentException("Ping is only used by the test button");
    }

    /** https only, no sign-in details in the address, and not an address that obviously points inside a network. Resolution is checked again when sending. */
    static void checkUrl(String url) {
        if (url == null || url.isBlank()) throw new IllegalArgumentException("Give the address to send to");
        String u = url.strip();
        if (u.length() > URL_MAX) throw new IllegalArgumentException("The address can be at most " + URL_MAX + " characters");
        URI uri;
        try { uri = new URI(u); } catch (URISyntaxException e) { throw new IllegalArgumentException("That address is not valid"); }
        if (!"https".equalsIgnoreCase(uri.getScheme())) throw new IllegalArgumentException("The address must start with https://");
        String host = uri.getHost();
        if (host == null || host.isBlank()) throw new IllegalArgumentException("That address is not valid");
        if (uri.getUserInfo() != null) throw new IllegalArgumentException("The address cannot contain a user name or password");
        String h = host.toLowerCase(Locale.ROOT);
        if (h.equals("localhost") || h.endsWith(".localhost") || h.endsWith(".local") || h.endsWith(".internal") || h.endsWith(".lan") || !h.contains(".") && !h.contains(":")) {
            throw new IllegalArgumentException("That address points inside a private network");
        }
        if (isIpLiteral(h)) {
            try {
                if (isBlocked(InetAddress.getByName(h))) throw new IllegalArgumentException("That address points inside a private network");
            } catch (java.net.UnknownHostException e) {
                throw new IllegalArgumentException("That address is not valid");
            }
        }
    }

    private static boolean isIpLiteral(String host) { return host.contains(":") || host.matches("^\\d{1,3}(\\.\\d{1,3}){3}$"); }

    /** Addresses a webhook may never call: this machine, private networks, link-local (cloud metadata), carrier-grade NAT, multicast and unspecified. */
    static boolean isBlocked(InetAddress a) {
        if (a.isAnyLocalAddress() || a.isLoopbackAddress() || a.isLinkLocalAddress() || a.isSiteLocalAddress() || a.isMulticastAddress()) return true;
        byte[] b = a.getAddress();
        if (b.length == 4) return (b[0] & 0xff) == 100 && (b[1] & 0xc0) == 64 || (b[0] & 0xff) == 0;           // 100.64.0.0/10, 0.0.0.0/8
        return b.length == 16 && (b[0] & 0xfe) == 0xfc;                                                          // fc00::/7 unique local
    }

    /** "sha256=" and the HMAC-SHA256 of the body, in lower-case hex. Receivers compute the same to prove a message is ours. */
    static String sign(String secret, String body) {
        try {
            Mac mac = Mac.getInstance("HmacSHA256");
            mac.init(new SecretKeySpec(secret.getBytes(StandardCharsets.UTF_8), "HmacSHA256"));
            byte[] out = mac.doFinal(body.getBytes(StandardCharsets.UTF_8));
            StringBuilder sb = new StringBuilder("sha256=");
            for (byte x : out) sb.append(String.format("%02x", x));
            return sb.toString();
        } catch (NoSuchAlgorithmException | InvalidKeyException e) {
            throw new IllegalStateException("Could not sign the message", e);
        }
    }

    static String joinEvents(List<WebhookEvent> events) { return String.join(",", events.stream().distinct().map(Enum::name).toList()); }

    static List<WebhookEvent> parseEvents(String stored) {
        if (stored == null || stored.isBlank()) return List.of();
        return java.util.Arrays.stream(stored.split(",")).map(String::strip).filter(s -> !s.isEmpty()).map(WebhookEvent::valueOf).toList();
    }

    /** What a person reads in the log for an answer from the receiver. */
    static String statusText(int code) {
        if (code >= 200 && code < 300) return "Delivered (" + code + ")";
        if (code >= 300 && code < 400) return "The address redirected (" + code + "); redirects are not followed";
        if (code == 401 || code == 403) return "The receiver refused it (" + code + ")";
        if (code == 404) return "The address was not found (404)";
        if (code >= 500) return "The receiver had an error (" + code + ")";
        return "The receiver answered " + code;
    }
}
