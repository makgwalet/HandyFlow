package za.co.handyflow.platform.contracting.application.internal;

import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockHttpServletRequest;
import za.co.handyflow.platform.contracting.application.internal.ClientIpResolver.ClientIp;
import za.co.handyflow.platform.contracting.application.internal.ClientIpResolver.Source;

import static org.junit.jupiter.api.Assertions.*;

class ClientIpResolverTest {

    private static MockHttpServletRequest request(String peer, String... forwardedFor) {
        MockHttpServletRequest r = new MockHttpServletRequest();
        r.setRemoteAddr(peer);
        for (String v : forwardedFor) r.addHeader("X-Forwarded-For", v);
        return r;
    }

    @Test
    void withNothingConfiguredTheForwardedHeaderIsIgnored() {
        ClientIpResolver resolver = new ClientIpResolver("");
        assertFalse(resolver.forwardingEnabled());
        ClientIp ip = resolver.resolve(request("10.0.0.5", "203.0.113.9"));
        assertEquals(new ClientIp("10.0.0.5", Source.DIRECT), ip);
    }

    @Test
    void aCallerCannotChooseItsAddressFromAnUntrustedPeer() {
        ClientIpResolver resolver = new ClientIpResolver("10.0.0.0/8");
        ClientIp ip = resolver.resolve(request("198.51.100.7", "1.2.3.4"));
        assertEquals(new ClientIp("198.51.100.7", Source.DIRECT), ip);
    }

    @Test
    void aTrustedProxyLetsTheClientAddressThrough() {
        ClientIpResolver resolver = new ClientIpResolver("10.0.0.0/8");
        ClientIp ip = resolver.resolve(request("10.1.2.3", "203.0.113.9"));
        assertEquals(new ClientIp("203.0.113.9", Source.TRUSTED_PROXY), ip);
    }

    @Test
    void trustedHopsAreSkippedFromTheRightAndAClientSuppliedLeftEntryIsNotBelieved() {
        ClientIpResolver resolver = new ClientIpResolver("10.0.0.0/8, 192.168.1.1");
        // the client sent "6.6.6.6"; the first proxy appended the real client, the second proxy appended the first proxy
        ClientIp ip = resolver.resolve(request("10.0.0.9", "6.6.6.6, 203.0.113.9, 192.168.1.1, 10.0.0.2"));
        assertEquals(new ClientIp("203.0.113.9", Source.TRUSTED_PROXY), ip);
    }

    @Test
    void multipleHeaderLinesAreTreatedAsOneChain() {
        ClientIpResolver resolver = new ClientIpResolver("10.0.0.1");
        ClientIp ip = resolver.resolve(request("10.0.0.1", "6.6.6.6", "203.0.113.9"));
        assertEquals("203.0.113.9", ip.address());
    }

    @Test
    void aMissingOrMalformedHeaderFallsBackToTheConnection() {
        ClientIpResolver resolver = new ClientIpResolver("10.0.0.0/8");
        assertEquals(new ClientIp("10.1.1.1", Source.DIRECT), resolver.resolve(request("10.1.1.1")));
        assertEquals(new ClientIp("10.1.1.1", Source.DIRECT), resolver.resolve(request("10.1.1.1", "not-an-ip")));
        assertEquals(new ClientIp("10.1.1.1", Source.DIRECT), resolver.resolve(request("10.1.1.1", "203.0.113.9, evil.example.com")));
        assertEquals(new ClientIp("10.1.1.1", Source.DIRECT), resolver.resolve(request("10.1.1.1", "999.1.1.1")));
    }

    @Test
    void aChainMadeOnlyOfTrustedProxiesFallsBackToTheConnection() {
        ClientIpResolver resolver = new ClientIpResolver("10.0.0.0/8");
        assertEquals(Source.DIRECT, resolver.resolve(request("10.1.1.1", "10.2.2.2, 10.3.3.3")).source());
    }

    @Test
    void ipv6AndPortsAreUnderstood() {
        ClientIpResolver resolver = new ClientIpResolver("fd00::/8, 10.0.0.0/8");
        assertEquals("203.0.113.9", resolver.resolve(request("fd00::1", "203.0.113.9:51234")).address());
        assertEquals(Source.TRUSTED_PROXY, resolver.resolve(request("10.0.0.1", "2001:db8::7")).source());
        assertEquals("2001:db8:0:0:0:0:0:7", resolver.resolve(request("10.0.0.1", "[2001:db8::7]")).address());
        // a v4 peer is never matched against a v6 range
        assertEquals(Source.DIRECT, new ClientIpResolver("fd00::/8").resolve(request("10.0.0.1", "203.0.113.9")).source());
    }

    @Test
    void cidrPrefixesThatAreNotOnAByteBoundaryAreRespected() {
        ClientIpResolver resolver = new ClientIpResolver("172.16.0.0/12");
        assertEquals(Source.TRUSTED_PROXY, resolver.resolve(request("172.31.255.255", "203.0.113.9")).source());
        assertEquals(Source.DIRECT, resolver.resolve(request("172.32.0.1", "203.0.113.9")).source());
    }

    @Test
    void aBadConfigurationFailsAtStartupRatherThanTrustingEverything() {
        for (String bad : new String[]{"10.0.0.0/40", "nope", "10.0.0.0/x", "999.0.0.1", "proxy.example.com"})
            assertThrows(IllegalArgumentException.class, () -> new ClientIpResolver(bad), bad);
    }

    // ── the source of each address is remembered for whatever stores it ──

    @org.junit.jupiter.api.AfterEach
    void clearRequest() { org.springframework.web.context.request.RequestContextHolder.resetRequestAttributes(); }

    private static void bind(MockHttpServletRequest r) {
        org.springframework.web.context.request.RequestContextHolder.setRequestAttributes(
                new org.springframework.web.context.request.ServletRequestAttributes(r));
    }

    @Test
    void theSourceIsDirectWhenNothingIsConfiguredEvenIfTheCallerSendsForwardingHeaders() {
        MockHttpServletRequest r = request("198.51.100.7", "203.0.113.9");
        r.addHeader("X-Forwarded-Proto", "https");
        r.addHeader("X-Real-IP", "203.0.113.9");
        bind(r);
        assertEquals("198.51.100.7", new ClientIpResolver("").address(r));
        assertEquals("DIRECT", ClientIpContext.currentSource());
        assertEquals("DIRECT", ClientIpContext.sourceFor("198.51.100.7"));
    }

    @Test
    void theSourceIsDirectWhenTheForwardedHeaderComesFromAnUntrustedPeer() {
        MockHttpServletRequest r = request("198.51.100.7", "203.0.113.9");
        bind(r);
        assertEquals("198.51.100.7", new ClientIpResolver("10.0.0.0/8").address(r));
        assertEquals("DIRECT", ClientIpContext.currentSource());
    }

    @Test
    void theSourceIsTrustedProxyOnlyWhenAConfiguredProxyVouchedForTheAddress() {
        MockHttpServletRequest r = request("10.0.0.5", "203.0.113.9");
        bind(r);
        assertEquals("203.0.113.9", new ClientIpResolver("10.0.0.0/8").address(r));
        assertEquals("TRUSTED_PROXY", ClientIpContext.currentSource());
    }

    @Test
    void aTrustedPeerWithAMalformedChainIsRecordedAsDirect() {
        MockHttpServletRequest r = request("10.0.0.5", "not-an-ip");
        bind(r);
        assertEquals("10.0.0.5", new ClientIpResolver("10.0.0.0/8").address(r));
        assertEquals("DIRECT", ClientIpContext.currentSource());
    }

    @Test
    void nothingIsRecordedOutsideARequestOrWithoutAnAddress() {
        assertNull(ClientIpContext.currentSource());
        assertNull(ClientIpContext.sourceFor("198.51.100.7"));
        MockHttpServletRequest r = request("198.51.100.7");
        bind(r);
        new ClientIpResolver("").resolve(r);
        assertNull(ClientIpContext.sourceFor(null));
        assertNull(ClientIpContext.sourceFor(" "));
    }

    @Test
    void trustingEveryProxyIsNotPossible() {
        assertThrows(IllegalArgumentException.class, () -> new ClientIpResolver("*"));
        assertThrows(IllegalArgumentException.class, () -> new ClientIpResolver("0.0.0.0/0"));
        assertThrows(IllegalArgumentException.class, () -> new ClientIpResolver("::/0"));
        assertThrows(IllegalArgumentException.class, () -> new ClientIpResolver("10.0.0.0/8,0.0.0.0/0"));
    }
}
