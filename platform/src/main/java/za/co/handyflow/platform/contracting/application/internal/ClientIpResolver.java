package za.co.handyflow.platform.contracting.application.internal;

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
 * The one place the Contracting module decides which client address a public request came from.
 *
 * <p>By default (no trusted proxies configured) it returns the address of the connection itself, which is the only address the caller
 * cannot choose. Forwarded headers are read only when the connection comes from a proxy listed in
 * {@code handyflow.contracting.trusted-proxies} (comma-separated addresses or CIDR ranges). No proxy addresses are assumed: the list is
 * empty until the hosting topology is confirmed and someone configures it.</p>
 *
 * <p>With a trusted peer, {@code X-Forwarded-For} is read from the right: hops that are themselves trusted proxies are skipped, and the
 * first address that is not trusted is the client. A malformed entry makes the resolver fall back to the connection address rather than
 * guess. Every result says whether it came from the connection ({@link Source#DIRECT}) or from a verified proxy chain
 * ({@link Source#TRUSTED_PROXY}).</p>
 */
@Component
public class ClientIpResolver {

    public enum Source { DIRECT, TRUSTED_PROXY }

    public record ClientIp(String address, Source source) {}

    static final String FORWARDED_FOR = "X-Forwarded-For";

    private static final Pattern IPV4 = Pattern.compile("^(\\d{1,3})(\\.\\d{1,3}){3}$");
    private static final Pattern IPV6 = Pattern.compile("^[0-9A-Fa-f:.]+$");

    private record Cidr(byte[] network, int prefix) {
        boolean contains(byte[] address) {
            if (address.length != network.length) return false;
            int fullBytes = prefix / 8;
            for (int i = 0; i < fullBytes; i++) if (address[i] != network[i]) return false;
            int rest = prefix % 8;
            if (rest == 0) return true;
            int mask = (0xFF << (8 - rest)) & 0xFF;
            return (address[fullBytes] & mask) == (network[fullBytes] & mask);
        }
    }

    private final List<Cidr> trusted;

    public ClientIpResolver(@Value("${handyflow.contracting.trusted-proxies:}") String configured) {
        this.trusted = parseTrusted(configured);
    }

    /** Whether any trusted proxy is configured. When false, forwarded headers are never read. */
    public boolean forwardingEnabled() { return !trusted.isEmpty(); }

    public String address(HttpServletRequest request) { return resolve(request).address(); }

    public ClientIp resolve(HttpServletRequest request) {
        String peer = request.getRemoteAddr();
        if (trusted.isEmpty()) return direct(peer);
        byte[] peerBytes = parseAddress(peer);
        if (peerBytes == null || !isTrusted(peerBytes)) return direct(peer);

        List<String> hops = forwardedHops(request);
        for (int i = hops.size() - 1; i >= 0; i--) {
            byte[] hop = parseAddress(hops.get(i));
            if (hop == null) return direct(peer);
            if (!isTrusted(hop)) return new ClientIp(text(hop), Source.TRUSTED_PROXY);
        }
        return direct(peer);
    }

    private static ClientIp direct(String peer) { return new ClientIp(peer, Source.DIRECT); }

    private boolean isTrusted(byte[] address) {
        for (Cidr c : trusted) if (c.contains(address)) return true;
        return false;
    }

    private static List<String> forwardedHops(HttpServletRequest request) {
        List<String> hops = new ArrayList<>();
        for (String value : Collections.list(request.getHeaders(FORWARDED_FOR))) {
            for (String part : value.split(",")) {
                String p = part.trim();
                if (!p.isEmpty()) hops.add(p);
            }
        }
        return hops;
    }

    static List<Cidr> parseTrusted(String configured) {
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
            byte[] bytes = parseAddress(host);
            if (bytes == null) throw new IllegalArgumentException("Invalid trusted proxy entry: " + entry);
            int max = bytes.length * 8;
            if (prefix == null) prefix = max;
            if (prefix < 0 || prefix > max) throw new IllegalArgumentException("Invalid trusted proxy entry: " + entry);
            out.add(new Cidr(bytes, prefix));
        }
        return out;
    }

    /** Parses an IPv4 or IPv6 literal (an IPv4 literal may carry a port). Never resolves a host name. Returns null if it is not a literal. */
    static byte[] parseAddress(String raw) {
        if (raw == null) return null;
        String s = raw.trim();
        if (s.startsWith("[") && s.endsWith("]")) s = s.substring(1, s.length() - 1);
        int colon = s.indexOf(':');
        if (colon > 0 && s.indexOf(':', colon + 1) < 0 && s.indexOf('.') >= 0) s = s.substring(0, colon);   // a.b.c.d:port
        if (IPV4.matcher(s).matches()) {
            for (String octet : s.split("\\.")) if (Integer.parseInt(octet) > 255) return null;
        } else if (!(s.indexOf(':') >= 0 && IPV6.matcher(s).matches())) {
            return null;
        }
        try { return InetAddress.getByName(s).getAddress(); }
        catch (UnknownHostException e) { return null; }
    }

    private static String text(byte[] address) {
        try { return InetAddress.getByAddress(address).getHostAddress(); }
        catch (UnknownHostException e) { throw new IllegalStateException(e); }
    }
}
