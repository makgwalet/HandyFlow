package za.co.handyflow.platform.security.application.internal;

import org.junit.jupiter.api.Test;
import java.time.Duration;
import java.time.Instant;

import static org.junit.jupiter.api.Assertions.*;

class PortalRulesTest {
    private static final Instant NOW = Instant.parse("2026-11-20T10:00:00Z");

    @Test void newExpiryIsNinetyDaysOut() {
        assertEquals(NOW.plus(Duration.ofDays(90)), PortalRules.newExpiry(NOW));
    }
    @Test void futureIsLivePastAndNowAreExpired() {
        assertFalse(PortalRules.expired(NOW.plusSeconds(1), NOW));
        assertTrue(PortalRules.expired(NOW, NOW));
        assertTrue(PortalRules.expired(NOW.minusSeconds(1), NOW));
    }
    @Test void noExpiryRecordedCountsAsExpired() { assertTrue(PortalRules.expired(null, NOW)); }

    @Test void generateKeepsALiveLink() {
        assertTrue(PortalRules.keepExisting(true, "tok", NOW.plusSeconds(60), NOW, false));
    }
    @Test void regenerateReplacesIt() {
        assertFalse(PortalRules.keepExisting(true, "tok", NOW.plusSeconds(60), NOW, true));
    }
    @Test void expiredDisabledOrTokenlessIsReplaced() {
        assertFalse(PortalRules.keepExisting(true, "tok", NOW.minusSeconds(60), NOW, false));
        assertFalse(PortalRules.keepExisting(false, "tok", NOW.plusSeconds(60), NOW, false));
        assertFalse(PortalRules.keepExisting(true, null, NOW.plusSeconds(60), NOW, false));
    }
    @Test void pathUsesTheSecurityPortalRoute() {
        assertEquals("/site-portal/abc", PortalRules.path("abc"));
    }
}
