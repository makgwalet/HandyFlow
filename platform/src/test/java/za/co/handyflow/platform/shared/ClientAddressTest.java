package za.co.handyflow.platform.shared;

import org.junit.jupiter.api.Test;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

class ClientAddressTest {

    private static final List<ClientAddress.Cidr> LB = ClientAddress.parse("10.0.0.0/24, 192.168.5.7");

    @Test
    void withNoTrustedProxyTheConnectionAddressIsAlwaysUsed() {
        assertEquals("203.0.113.9", ClientAddress.resolve("203.0.113.9", List.of("1.2.3.4"), List.of()));
    }

    @Test
    void aForwardedHeaderFromAnUntrustedConnectionIsIgnored() {
        assertEquals("203.0.113.9", ClientAddress.resolve("203.0.113.9", List.of("1.2.3.4"), LB));
        assertEquals("203.0.113.9", ClientAddress.resolve("203.0.113.9", List.of("10.0.0.5"), LB));
    }

    @Test
    void behindATrustedProxyTheClientIsTheFirstUntrustedHopFromTheRight() {
        assertEquals("198.51.100.20", ClientAddress.resolve("10.0.0.2", List.of("198.51.100.20"), LB));
        // a caller who forges the left of the header does not get to choose: the proxy appended the real address on the right
        assertEquals("198.51.100.20", ClientAddress.resolve("10.0.0.2", List.of("1.1.1.1", "198.51.100.20"), LB));
        assertEquals("198.51.100.20", ClientAddress.resolve("10.0.0.2", List.of("198.51.100.20", "10.0.0.9", "192.168.5.7"), LB));
    }

    @Test
    void noHeaderOrOnlyTrustedHopsMeansTheProxyAddress() {
        assertEquals("10.0.0.2", ClientAddress.resolve("10.0.0.2", List.of(), LB));
        assertEquals("10.0.0.2", ClientAddress.resolve("10.0.0.2", List.of("10.0.0.9"), LB));
    }

    @Test
    void aMalformedHopFallsBackToTheConnectionAddress() {
        assertEquals("10.0.0.2", ClientAddress.resolve("10.0.0.2", List.of("198.51.100.20", "not-an-ip"), LB));
        assertEquals("10.0.0.2", ClientAddress.resolve("10.0.0.2", List.of("999.1.1.1"), LB));
        assertEquals("10.0.0.2", ClientAddress.resolve("10.0.0.2", List.of("<script>"), LB));
    }

    @Test
    void hopsMayCarryAPortOrBrackets() {
        assertEquals("198.51.100.20", ClientAddress.resolve("10.0.0.2", List.of("198.51.100.20:5555"), LB));
        assertEquals("2606:4700:4700:0:0:0:0:1111", ClientAddress.resolve("10.0.0.2", List.of("[2606:4700:4700::1111]"), LB));
    }

    @Test
    void badConfigurationIsRefusedAtStartup() {
        for (String bad : new String[]{"nonsense", "10.0.0.0/33", "10.0.0.0/x", "0.0.0.0/0", "::/0", "10.0.0/8", "10.0.0.1/-1"})
            assertThrows(IllegalArgumentException.class, () -> ClientAddress.parse(bad), bad);
        assertTrue(ClientAddress.parse("").isEmpty());
        assertTrue(ClientAddress.parse(null).isEmpty());
        assertTrue(ClientAddress.parse(" , ").isEmpty());
    }

    @Test
    void cidrBoundariesAreExact() {
        var t = ClientAddress.parse("10.0.4.0/22");
        assertEquals("1.2.3.4", ClientAddress.resolve("10.0.7.255", List.of("1.2.3.4"), t));
        assertEquals("10.0.8.0", ClientAddress.resolve("10.0.8.0", List.of("1.2.3.4"), t));
        assertEquals("10.0.3.255", ClientAddress.resolve("10.0.3.255", List.of("1.2.3.4"), t));
    }

    @Test
    void ipv4KeysAreTheAddressItself() {
        assertEquals("203.0.113.9", ClientAddress.rateKey("203.0.113.9"));
        assertEquals("203.0.113.9", ClientAddress.rateKey("::ffff:203.0.113.9"));
    }

    @Test
    void ipv6KeysAreTheirSlash64SoOneSubscriberIsOneBucket() {
        String a = ClientAddress.rateKey("2606:4700:4700:1234:aaaa:bbbb:cccc:dddd");
        String b = ClientAddress.rateKey("2606:4700:4700:1234:1:2:3:4");
        assertEquals(a, b);
        assertEquals("2606:4700:4700:1234::/64", a);
        assertNotEquals(a, ClientAddress.rateKey("2606:4700:4700:1235::1"));
    }

    @Test
    void anUnreadableAddressStillGivesAKey() {
        assertEquals("unknown", ClientAddress.rateKey((String) null));
        assertEquals("unknown", ClientAddress.rateKey("  "));
        assertEquals("weird", ClientAddress.rateKey("weird"));
    }

    private static String filter() throws Exception {
        for (String root : new String[]{"src/main/java", "platform/src/main/java", "work/src/main/java"}) {
            Path p = Path.of(root, "za/co/handyflow/platform/config/RateLimitFilter.java");
            if (Files.exists(p)) return Files.readString(p);
        }
        fail("missing RateLimitFilter"); return "";
    }

    @Test
    void everyRateLimitKeyGoesThroughTheResolver() throws Exception {
        String f = filter();
        int direct = 0, i = 0;
        String code = f.replaceAll("(?s)/\\*.*?\\*/", "").replaceAll("//[^\\n]*", "");
        while ((i = code.indexOf("request.getRemoteAddr()", i)) >= 0) { direct++; i++; }
        assertEquals(1, direct, "only the fallback inside clientIp() may read the connection address directly");
        assertTrue(code.contains("clientAddress != null ? clientAddress.rateKey(request) : request.getRemoteAddr()"));
        assertTrue(code.contains("@org.springframework.beans.factory.annotation.Autowired(required = false)"));
        assertTrue(code.contains("limit.scope() + \":\" + clientIp(request)"));
        assertTrue(code.contains("String clientIp = clientIp(request);"));
    }
}
