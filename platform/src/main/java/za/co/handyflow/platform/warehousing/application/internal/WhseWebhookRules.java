package za.co.handyflow.platform.warehousing.application.internal;

import java.net.InetAddress;
import java.net.URI;
import java.net.URISyntaxException;
import java.nio.charset.StandardCharsets;
import java.security.InvalidKeyException;
import java.security.NoSuchAlgorithmException;
import java.time.Duration;
import java.util.Arrays;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;

/** What a warehousing webhook must be, how deliveries are signed and retried, and which addresses are never called. Pure, no database or network. */
public final class WhseWebhookRules {

    private WhseWebhookRules() {}

    public static final int MAX_WEBHOOKS = 10, URL_MAX = 500, MAX_ATTEMPTS = 6, SUSPEND_AFTER = 20;

    public static final String INBOUND_RECEIVED = "inbound.received";
    public static final String OUTBOUND_PICKING_STARTED = "outbound.picking_started";
    public static final String OUTBOUND_SHIPPED = "outbound.shipped";
    public static final String OUTBOUND_CANCELLED = "outbound.cancelled";
    public static final String PING = "ping";
    /** The events a webhook can subscribe to. */
    public static final List<String> EVENTS = List.of(INBOUND_RECEIVED, OUTBOUND_PICKING_STARTED, OUTBOUND_SHIPPED, OUTBOUND_CANCELLED);

    public static void check(String name, String url, List<String> events) {
        if (name == null || name.isBlank()) throw new IllegalArgumentException("Name the webhook.");
        if (name.strip().length() > 120) throw new IllegalArgumentException("The name can be at most 120 characters.");
        checkUrl(url);
        joinEvents(events);
    }

    /** "*" for all events, else the chosen ones. At least one, each known. */
    public static String joinEvents(List<String> events) {
        if (events == null || events.isEmpty()) throw new IllegalArgumentException("Choose at least one event.");
        Set<String> out = new LinkedHashSet<>();
        for (String e : events) {
            String v = e == null ? "" : e.strip();
            if (v.equals("*")) return "*";
            if (!EVENTS.contains(v)) throw new IllegalArgumentException("Unknown event \"" + v + "\".");
            out.add(v);
        }
        return String.join(",", out);
    }

    public static boolean listensTo(String stored, String event) {
        if (stored == null || stored.isBlank()) return false;
        if (stored.equals("*")) return true;
        return Arrays.asList(stored.split(",")).contains(event);
    }

    /** https only, no credentials in the address, and not an address that obviously points inside a network. Resolution is checked again when sending. */
    public static void checkUrl(String url) {
        if (url == null || url.isBlank()) throw new IllegalArgumentException("Give the address to send to.");
        String u = url.strip();
        if (u.length() > URL_MAX) throw new IllegalArgumentException("The address can be at most " + URL_MAX + " characters.");
        URI uri;
        try { uri = new URI(u); } catch (URISyntaxException e) { throw new IllegalArgumentException("That address is not valid."); }
        if (!"https".equalsIgnoreCase(uri.getScheme())) throw new IllegalArgumentException("The address must start with https://");
        String host = uri.getHost();
        if (host == null || host.isBlank()) throw new IllegalArgumentException("That address is not valid.");
        if (uri.getUserInfo() != null) throw new IllegalArgumentException("The address cannot contain a user name or password.");
        String h = host.toLowerCase(Locale.ROOT);
        if (h.equals("localhost") || h.endsWith(".localhost") || h.endsWith(".local") || h.endsWith(".internal") || h.endsWith(".lan") || !h.contains(".") && !h.contains(":")) {
            throw new IllegalArgumentException("That address points inside a private network.");
        }
        if (h.contains(":") || h.matches("^\\d{1,3}(\\.\\d{1,3}){3}$")) {
            try {
                if (isBlocked(InetAddress.getByName(h))) throw new IllegalArgumentException("That address points inside a private network.");
            } catch (java.net.UnknownHostException e) {
                throw new IllegalArgumentException("That address is not valid.");
            }
        }
    }

    /** Addresses a webhook may never call: this machine, private networks, link-local (cloud metadata), carrier-grade NAT, multicast and unspecified. */
    public static boolean isBlocked(InetAddress a) {
        if (a.isAnyLocalAddress() || a.isLoopbackAddress() || a.isLinkLocalAddress() || a.isSiteLocalAddress() || a.isMulticastAddress()) return true;
        byte[] b = a.getAddress();
        if (b.length == 4) return (b[0] & 0xff) == 100 && (b[1] & 0xc0) == 64 || (b[0] & 0xff) == 0;
        return b.length == 16 && (b[0] & 0xfe) == 0xfc;
    }

    /** "sha256=" and the HMAC-SHA256 of the body in lower-case hex. Receivers compute the same to prove a message is ours. */
    public static String sign(String secret, String body) {
        try {
            Mac mac = Mac.getInstance("HmacSHA256");
            mac.init(new SecretKeySpec(secret.getBytes(StandardCharsets.UTF_8), "HmacSHA256"));
            StringBuilder sb = new StringBuilder("sha256=");
            for (byte x : mac.doFinal(body.getBytes(StandardCharsets.UTF_8))) sb.append(String.format("%02x", x));
            return sb.toString();
        } catch (NoSuchAlgorithmException | InvalidKeyException e) {
            throw new IllegalStateException("Could not sign the message", e);
        }
    }

    /** How long to wait after the n-th failed attempt (1-based): 1 minute, 5 minutes, 30 minutes, 2 hours, 12 hours. */
    public static Duration backoff(int attemptsMade) {
        return switch (attemptsMade) {
            case 1 -> Duration.ofMinutes(1);
            case 2 -> Duration.ofMinutes(5);
            case 3 -> Duration.ofMinutes(30);
            case 4 -> Duration.ofHours(2);
            default -> Duration.ofHours(12);
        };
    }

    /** True once a failed delivery has used up its attempts. */
    public static boolean exhausted(int attemptsMade) { return attemptsMade >= MAX_ATTEMPTS; }

    public static boolean shouldSuspend(int consecutiveFailures) { return consecutiveFailures >= SUSPEND_AFTER; }

    /** What a person reads in the log for an answer from the receiver. */
    public static String statusText(int code) {
        if (code >= 200 && code < 300) return "Delivered (" + code + ")";
        if (code >= 300 && code < 400) return "The address redirected (" + code + "); redirects are not followed";
        if (code == 401 || code == 403) return "The receiver refused it (" + code + ")";
        if (code == 404) return "The address was not found (404)";
        if (code >= 500) return "The receiver had an error (" + code + ")";
        return "The receiver answered " + code;
    }
}
