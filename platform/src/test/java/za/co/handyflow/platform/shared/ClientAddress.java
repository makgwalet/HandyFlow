// shared/ClientAddress.java
package za.co.handyflow.platform.shared;

import jakarta.servlet.http.HttpServletRequest;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import java.net.InetAddress;
import java.net.UnknownHostException;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.regex.Pattern;

/**
 * Which address a request really came from, for rate limiting.
 *
 * <p>With no trusted proxy configured (the default) this is the connection's own address, the only one a caller cannot choose.
 * Behind a load balancer every request arrives from the balancer, so all users would share one rate-limit bucket; set
 * {@code handyflow.network.trusted-proxies} (comma-separated addresses or CIDR ranges of the balancer) and the client is read from
 * {@code X-Forwarded-For}, from the right, skipping trusted hops. If that property is not set, {@code handyflow.contracting.trusted-proxies}
 * is used, so one setting can serve both. A forwarded header from an untrusted connection is ignored, and anything malformed falls back
 * to the connection address.</p>
 *
 * <p>For the limit key an IPv6 address is reduced to its /64 prefix: one subscriber normally holds a whole /64, so keying on the full
 * address would give an attacker billions of fresh buckets.</p>
 */
@Component
public class ClientAddress {

    private static final Pattern IPV4 = Pattern.compile("^(\\d{1,3})(\\.\\d{1,3}){3}$");
    private static final Pattern IPV6 = Pattern.compile("^[0-9A-Fa-f:.]+$");

    record Cidr(byte[] network, int prefix) {
        boolean contains(byte[] a) {
            if (a.length != network.length) return false;
            int full = prefix / 8;
            for (int i = 0; i < full; i++) if (a[i] != network[i]) return false;
            int rest = prefix % 8;
            if (rest == 0) return true;
            int mask = (0xFF << (8 - rest)) & 0xFF;
            return (a[full] & mask) == (network[full] & mask);
        }
    }

    private final List<Cidr> trusted;

    public ClientAddress(@Value("${handyflow.network.trusted-proxies:${handyflow.contracting.trusted-proxies:}}") String configured) {
        this.trusted = parse(configured);
    }

    /** The address to key rate limits on. */
    public String rateKey(HttpServletRequest request) {
        return rateKey(resolve(request.getRemoteAddr(), forwarded(request)));
    }

    public String resolve(String peer, List<String> forwardedFor) { return resolve(peer, forwardedFor, trusted); }

    static String resolve(String peer, List<String> forwardedFor, List<Cidr> trusted) {
        if (trusted.isEmpty()) return peer;
        byte[] p = bytes(peer);
        if (p == null || !isTrusted(p, trusted)) return peer;
        for (int i = forwardedFor.size() - 1; i >= 0; i--) {
            byte[] hop = bytes(forwardedFor.get(i));
            if (hop == null) return peer;
            if (!isTrusted(hop, trusted)) return text(hop);
        }
        return peer;
    }

    /** IPv4 as it is; IPv6 as its /64 prefix; anything else as written (never empty). */
    static String rateKey(String address) {
        byte[] b = bytes(address);
        if (b == null) return address == null || address.isBlank() ? "unknown" : address;
        if (b.length == 16) {
            boolean mapped = true;
            for (int i = 0; i < 10; i++) if (b[i] != 0) mapped = false;
            if (b[10] != (byte) 0xFF || b[11] != (byte) 0xFF) mapped = false;
            if (mapped) return (b[12] & 0xFF) + "." + (b[13] & 0xFF) + "." + (b[14] & 0xFF) + "." + (b[15] & 0xFF);
            StringBuilder s = new StringBuilder();
            for (int i = 0; i < 8; i++) s.append(String.format("%02x", b[i])).append(i % 2 == 1 && i < 7 ? ":" : "");
            return s + "::/64";
        }
        return text(b);
    }

    private static boolean isTrusted(byte[] a, List<Cidr> trusted) {
        for (Cidr c : trusted) if (c.contains(a)) return true;
        return false;
    }

    private static List<String> forwarded(HttpServletRequest request) {
        List<String> hops = new ArrayList<>();
        for (String value : Collections.list(request.getHeaders("X-Forwarded-For"))) {
            for (String part : value.split(",")) { String s = part.trim(); if (!s.isEmpty()) hops.add(s); }
        }
        return hops;
    }

    static List<Cidr> parse(String configured) {
        List<Cidr> out = new ArrayList<>();
        if (configured == null) return out;
        for (String raw : configured.split(",")) {
            String entry = raw.trim();
            if (entry.isEmpty()) continue;
            String host = entry;
            Integer prefix = null;
            int slash = entry.indexOf('/');
            if (slash >= 0) {
                host = entry.substring(0, slash).trim();
                try { prefix = Integer.parseInt(entry.substring(slash + 1).trim()); }
                catch (NumberFormatException e) { throw new IllegalArgumentException("Invalid trusted proxy entry: " + entry); }
            }
            byte[] b = bytes(host);
            if (b == null) throw new IllegalArgumentException("Invalid trusted proxy entry: " + entry);
            if (prefix == null) prefix = b.length * 8;
            if (prefix < 0 || prefix > b.length * 8) throw new IllegalArgumentException("Invalid trusted proxy entry: " + entry);
            if (prefix == 0) throw new IllegalArgumentException("Trusted proxy entry " + entry + " would trust every address");
            out.add(new Cidr(b, prefix));
        }
        return out;
    }

    /** An IPv4 or IPv6 literal (IPv4 may carry a port); never resolves a name. Null if it is not a literal. */
    static byte[] bytes(String raw) {
        if (raw == null) return null;
        String s = raw.trim();
        if (s.startsWith("[") && s.endsWith("]")) s = s.substring(1, s.length() - 1);
        int colon = s.indexOf(':');
        if (colon > 0 && s.indexOf(':', colon + 1) < 0 && s.indexOf('.') >= 0) s = s.substring(0, colon);
        if (IPV4.matcher(s).matches()) {
            for (String o : s.split("\\.")) if (Integer.parseInt(o) > 255) return null;
        } else if (!(s.indexOf(':') >= 0 && IPV6.matcher(s).matches())) {
            return null;
        }
        try { return InetAddress.getByName(s).getAddress(); }
        catch (UnknownHostException e) { return null; }
    }

    private static String text(byte[] a) {
        try { return InetAddress.getByAddress(a).getHostAddress(); }
        catch (UnknownHostException e) { throw new IllegalStateException(e); }
    }
}
