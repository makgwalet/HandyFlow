package za.co.handyflow.platform.security.application.internal;

import org.junit.jupiter.api.Test;

import java.net.InetAddress;
import java.net.UnknownHostException;
import java.nio.file.Files;
import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.*;

class WebhookSendGuardTest {

    private static InetAddress ip(String literal) {
        try { return InetAddress.getByName(literal); } catch (UnknownHostException e) { throw new AssertionError(e); }
    }

    private static WebhookSendGuard.Resolver answers(String... literals) {
        return host -> { InetAddress[] a = new InetAddress[literals.length]; for (int i = 0; i < a.length; i++) a[i] = ip(literals[i]); return a; };
    }

    // ---- which addresses count as public ----

    @Test
    void publicAddressesPass() {
        for (String a : new String[]{"8.8.8.8", "1.1.1.1", "41.76.1.2", "172.15.0.1", "172.32.0.1", "100.63.255.255", "100.128.0.1", "198.17.0.1", "198.20.0.1",
                "223.255.255.255", "2606:4700:4700::1111", "2a00:1450:4001:81b::200e", "::ffff:8.8.8.8"})
            assertTrue(WebhookSendGuard.isPublic(ip(a)), a);
    }

    @Test
    void privateLoopbackLinkLocalAndSpecialAddressesAreRefused() {
        for (String a : new String[]{"127.0.0.1", "127.255.0.9", "10.0.0.1", "10.255.255.255", "172.16.0.1", "172.31.255.255", "192.168.1.1", "169.254.169.254",
                "100.64.0.1", "100.127.255.255", "0.0.0.0", "0.1.2.3", "192.0.0.5", "192.0.2.7", "198.18.0.1", "198.19.255.255", "198.51.100.9", "203.0.113.9",
                "224.0.0.1", "239.1.1.1", "240.0.0.1", "255.255.255.255",
                "::1", "::", "fe80::1", "fc00::1", "fd12:3456::1", "febf::1", "fec0::1", "2001:db8::1", "ff02::1", "::ffff:127.0.0.1", "::ffff:10.0.0.1",
                "::ffff:169.254.169.254", "::10.0.0.1", "64:ff9b::1"})
            assertFalse(WebhookSendGuard.isPublic(ip(a)), a);
        assertFalse(WebhookSendGuard.isPublic(null));
    }

    // ---- the send-time check ----

    @Test
    void aNameThatResolvesToPublicAddressesMayBeCalled() {
        assertTrue(WebhookSendGuard.problem("https://hooks.example.com/in", answers("93.184.216.34", "2606:2800:220:1::1")).isEmpty());
    }

    @Test
    void oneBadAddressAmongGoodOnesRefusesTheSend() {
        assertEquals("The endpoint resolves to a non-public address.", WebhookSendGuard.problem("https://hooks.example.com/in", answers("93.184.216.34", "10.0.0.5")).orElse(null));
        assertEquals("The endpoint resolves to a non-public address.", WebhookSendGuard.problem("https://rebind.example.com/", answers("169.254.169.254")).orElse(null));
        assertEquals("The endpoint resolves to a non-public address.", WebhookSendGuard.problem("https://rebind.example.com/", answers("::ffff:127.0.0.1")).orElse(null));
    }

    @Test
    void aNameThatNoLongerResolvesIsRefused() {
        assertEquals("The endpoint's host name could not be resolved.", WebhookSendGuard.problem("https://gone.example.com/", h -> { throw new UnknownHostException(h); }).orElse(null));
        assertEquals("The endpoint's host name could not be resolved.", WebhookSendGuard.problem("https://gone.example.com/", h -> new InetAddress[0]).orElse(null));
        assertEquals("The endpoint's host name could not be resolved.", WebhookSendGuard.problem("https://gone.example.com/", h -> null).orElse(null));
        assertEquals("The endpoint's host name could not be resolved.", WebhookSendGuard.problem("https://gone.example.com/", h -> { throw new IllegalStateException("x"); }).orElse(null));
    }

    @Test
    void theWrittenUrlRulesStillApplyFirst() {
        WebhookSendGuard.Resolver neverCalled = h -> { throw new AssertionError("must not resolve " + h); };
        assertTrue(WebhookSendGuard.problem("http://hooks.example.com/", neverCalled).isPresent());
        assertTrue(WebhookSendGuard.problem("https://localhost/", neverCalled).isPresent());
        assertTrue(WebhookSendGuard.problem("https://10.0.0.1/", neverCalled).isPresent());
        assertTrue(WebhookSendGuard.problem("https://user:pw@hooks.example.com/", neverCalled).isPresent());
    }

    @Test
    void anIpLiteralInBracketsIsCheckedWithoutTheBrackets() {
        WebhookSendGuard.Resolver echo = host -> { assertFalse(host.contains("[")); return new InetAddress[]{ip(host)}; };
        assertTrue(WebhookSendGuard.problem("https://[2606:4700:4700::1111]/x", echo).isEmpty());
        assertTrue(WebhookSendGuard.problem("https://[fe80::1]/x", echo).isPresent());
    }

    // ---- the reply body ----

    @Test
    void aLongReplyIsClipped() {
        assertNull(WebhookSendGuard.clip(null));
        assertEquals("ok", WebhookSendGuard.clip("ok"));
        assertEquals(2000, WebhookSendGuard.clip("x".repeat(2000)).length());
        assertEquals(2000, WebhookSendGuard.clip("x".repeat(100000)).length());
    }

    @Test
    void timeoutsAreShortAndBounded() {
        assertEquals(5L, WebhookSendGuard.CONNECT_TIMEOUT.toSeconds());
        assertEquals(10L, WebhookSendGuard.READ_TIMEOUT.toSeconds());
    }

    // ---- wiring, read from the source ----

    private static String service() throws Exception {
        for (String root : new String[]{"src/main/java", "platform/src/main/java", "work/src/main/java"}) {
            Path p = Path.of(root, "za/co/handyflow/platform/security/application/internal/PublicApiService.java");
            if (Files.exists(p)) return Files.readString(p);
        }
        fail("missing PublicApiService"); return "";
    }

    @Test
    void theSenderUsesTimeoutsNoRedirectsAndChecksBeforeSending() throws Exception {
        String s = service();
        assertFalse(s.contains("RestClient.create()"));
        assertTrue(s.contains(".connectTimeout(WebhookSendGuard.CONNECT_TIMEOUT)"));
        assertTrue(s.contains("followRedirects(java.net.http.HttpClient.Redirect.NEVER)"));
        assertTrue(s.contains("setReadTimeout(WebhookSendGuard.READ_TIMEOUT)"));
        int check = s.indexOf("WebhookSendGuard.problem(sub.getEndpointUrl()");
        int post = s.indexOf("restClient.post()");
        assertTrue(check > 0 && check < post);
        assertTrue(s.contains("WebhookSendGuard.clip(response.getBody())"));
        assertFalse(s.contains("markDelivered(status, response.getBody())"));
    }

    @Test
    void aRefusedSendIsLoggedAndRetriedLikeAnyFailure() throws Exception {
        String s = service();
        int a = s.indexOf("if (blocked.isPresent())");
        String block = s.substring(a, s.indexOf("try {", a));
        assertTrue(block.contains("delivery.markNetworkFailed(blocked.get()"));
        assertTrue(block.contains("sub.recordFailure()"));
        assertTrue(block.contains("deliveryRepository.save(delivery)"));
        assertTrue(block.contains("return;"));
    }
}
