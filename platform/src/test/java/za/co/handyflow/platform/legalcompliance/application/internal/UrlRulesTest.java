package za.co.handyflow.platform.legalcompliance.application.internal;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.net.InetAddress;

import static org.junit.jupiter.api.Assertions.*;

class UrlRulesTest {

    @Test @DisplayName("A plain public https address is accepted and trimmed")
    void accepted() {
        assertEquals("https://www.gov.za/feed.xml", UrlRules.validate("  https://www.gov.za/feed.xml "));
        assertEquals("https://example.org:443/rss?x=1", UrlRules.validate("https://example.org:443/rss?x=1"));
    }

    @Test @DisplayName("Anything that could reach an internal service or hide credentials is refused")
    void refused() {
        for (String bad : new String[]{null, "", "   ", "http://example.org/feed", "ftp://example.org/feed", "file:///etc/passwd", "https://user:pw@example.org/feed", "https://example.org:8443/feed",
                "https://localhost/feed", "https://127.0.0.1/feed", "https://10.0.0.5/feed", "https://[::1]/feed", "https://intranet/feed", "https://db.internal/feed", "https://printer.local/feed",
                "https://example.org/feed#frag", "not a url", "https://" + "a".repeat(500) + ".org/x"})
            assertThrows(IllegalArgumentException.class, () -> UrlRules.validate(bad), String.valueOf(bad));
    }

    private static boolean pub(String ip) throws Exception { return UrlRules.isPublicAddress(InetAddress.getByName(ip)); }

    @Test @DisplayName("Resolved addresses: public ones pass; loopback, private, link-local, CGNAT, unique-local and IPv4-mapped private ones do not")
    void addresses() throws Exception {
        assertTrue(pub("8.8.8.8"));
        assertTrue(pub("196.25.1.1"));
        assertTrue(pub("2606:4700:4700::1111"));
        for (String bad : new String[]{"127.0.0.1", "10.1.2.3", "172.16.0.1", "192.168.1.1", "169.254.169.254", "100.64.0.1", "0.0.0.0", "224.0.0.1", "198.18.0.1", "255.255.255.255",
                "::1", "fe80::1", "fc00::1", "fd12:3456::1", "::ffff:10.0.0.1", "::ffff:127.0.0.1"})
            assertFalse(pub(bad), bad);
        assertTrue(pub("::ffff:8.8.8.8"));
    }
}
