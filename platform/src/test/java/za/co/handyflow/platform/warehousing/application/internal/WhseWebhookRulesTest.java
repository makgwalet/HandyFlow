package za.co.handyflow.platform.warehousing.application.internal;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.net.InetAddress;
import java.time.Duration;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

class WhseWebhookRulesTest {

    @Test @DisplayName("only https addresses are accepted, without credentials")
    void httpsOnly() {
        assertDoesNotThrow(() -> WhseWebhookRules.checkUrl("https://hooks.example.com/whse"));
        assertThrows(IllegalArgumentException.class, () -> WhseWebhookRules.checkUrl("http://hooks.example.com/whse"));
        assertThrows(IllegalArgumentException.class, () -> WhseWebhookRules.checkUrl("https://user:pw@hooks.example.com/"));
        assertThrows(IllegalArgumentException.class, () -> WhseWebhookRules.checkUrl(" "));
        assertThrows(IllegalArgumentException.class, () -> WhseWebhookRules.checkUrl("ftp://hooks.example.com/"));
    }

    @Test @DisplayName("addresses inside a private network are refused")
    void privateRefused() {
        for (String u : List.of("https://localhost/x", "https://printer.local/x", "https://intranet/x", "https://127.0.0.1/x", "https://10.1.2.3/x",
                "https://192.168.0.5/x", "https://169.254.169.254/latest/meta-data", "https://100.64.0.1/x", "https://[::1]/x", "https://api.internal/x")) {
            assertThrows(IllegalArgumentException.class, () -> WhseWebhookRules.checkUrl(u), u);
        }
    }

    @Test @DisplayName("blocked address ranges")
    void blocked() throws Exception {
        assertTrue(WhseWebhookRules.isBlocked(InetAddress.getByName("172.16.4.4")));
        assertTrue(WhseWebhookRules.isBlocked(InetAddress.getByName("0.0.0.0")));
        assertTrue(WhseWebhookRules.isBlocked(InetAddress.getByName("fd00::1")));
        assertFalse(WhseWebhookRules.isBlocked(InetAddress.getByName("93.184.216.34")));
    }

    @Test @DisplayName("events: star means all, unknown names are refused, at least one is needed")
    void events() {
        assertEquals("*", WhseWebhookRules.joinEvents(List.of("outbound.shipped", "*")));
        assertEquals("inbound.received,outbound.shipped", WhseWebhookRules.joinEvents(List.of("inbound.received", "outbound.shipped", "inbound.received")));
        assertThrows(IllegalArgumentException.class, () -> WhseWebhookRules.joinEvents(List.of("stock.exploded")));
        assertThrows(IllegalArgumentException.class, () -> WhseWebhookRules.joinEvents(List.of()));
        assertTrue(WhseWebhookRules.listensTo("*", "outbound.shipped"));
        assertTrue(WhseWebhookRules.listensTo("inbound.received,outbound.shipped", "outbound.shipped"));
        assertFalse(WhseWebhookRules.listensTo("inbound.received", "outbound.shipped"));
        assertFalse(WhseWebhookRules.listensTo("", "outbound.shipped"));
    }

    @Test @DisplayName("the signature is HMAC-SHA256 in hex, prefixed, and changes with the body or the secret")
    void signature() {
        // Known vector: HMAC-SHA256(key="key", "The quick brown fox jumps over the lazy dog")
        assertEquals("sha256=f7bc83f430538424b13298e6aa6fb143ef4d59a14946175997479dbc2d1a3cd8",
                WhseWebhookRules.sign("key", "The quick brown fox jumps over the lazy dog"));
        assertNotEquals(WhseWebhookRules.sign("key", "a"), WhseWebhookRules.sign("key", "b"));
        assertNotEquals(WhseWebhookRules.sign("key", "a"), WhseWebhookRules.sign("other", "a"));
    }

    @Test @DisplayName("waits grow after each failure and a delivery is given up on after six attempts")
    void retries() {
        assertEquals(Duration.ofMinutes(1), WhseWebhookRules.backoff(1));
        assertEquals(Duration.ofMinutes(5), WhseWebhookRules.backoff(2));
        assertEquals(Duration.ofMinutes(30), WhseWebhookRules.backoff(3));
        assertEquals(Duration.ofHours(2), WhseWebhookRules.backoff(4));
        assertEquals(Duration.ofHours(12), WhseWebhookRules.backoff(5));
        assertFalse(WhseWebhookRules.exhausted(5));
        assertTrue(WhseWebhookRules.exhausted(6));
        assertFalse(WhseWebhookRules.shouldSuspend(19));
        assertTrue(WhseWebhookRules.shouldSuspend(20));
    }

    @Test @DisplayName("name and address are checked together")
    void check() {
        assertDoesNotThrow(() -> WhseWebhookRules.check("ERP", "https://erp.example.com/hook", List.of("*")));
        assertThrows(IllegalArgumentException.class, () -> WhseWebhookRules.check(" ", "https://erp.example.com/hook", List.of("*")));
        assertThrows(IllegalArgumentException.class, () -> WhseWebhookRules.check("x".repeat(121), "https://erp.example.com/hook", List.of("*")));
    }
}
