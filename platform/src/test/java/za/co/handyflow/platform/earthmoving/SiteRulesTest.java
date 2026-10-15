package za.co.handyflow.platform.earthmoving;

import org.junit.jupiter.api.Test;
import za.co.handyflow.platform.earthmoving.domain.model.*;
import za.co.handyflow.platform.earthmoving.domain.model.SiteRules.MatchState;
import za.co.handyflow.platform.shared.TenantId;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;

class SiteRulesTest {
    @Test void normalizeIgnoresCaseAndSpacingOnly() {
        assertEquals("mine a", SiteRules.normalize("  Mine   A "));
        assertEquals(SiteRules.normalize("MINE A"), SiteRules.normalize("mine a"));
        assertNotEquals(SiteRules.normalize("Mine A"), SiteRules.normalize("Mine B"));
        assertEquals("", SiteRules.normalize(null));
    }
    @Test void classifyNeverGuesses() {
        assertEquals(MatchState.NONE, SiteRules.classify(null, 3));
        assertEquals(MatchState.NONE, SiteRules.classify("  ", 0));
        assertEquals(MatchState.MATCHED, SiteRules.classify("Mine A", 1));
        assertEquals(MatchState.AMBIGUOUS, SiteRules.classify("Mine A", 2));
        assertEquals(MatchState.UNMATCHED, SiteRules.classify("Mine A", 0));
    }
    @Test void siteChecks() {
        assertThrows(IllegalArgumentException.class, () -> SiteRules.checkSite(" ", null, null));
        assertThrows(IllegalArgumentException.class, () -> SiteRules.checkSite("A", BigDecimal.ONE, null));
        assertThrows(IllegalArgumentException.class, () -> SiteRules.checkSite("A", BigDecimal.valueOf(91), BigDecimal.ONE));
        assertThrows(IllegalArgumentException.class, () -> SiteRules.checkSite("A", BigDecimal.ONE, BigDecimal.valueOf(-181)));
        assertThrows(IllegalArgumentException.class, () -> SiteRules.checkSite("x".repeat(256), null, null));
        assertDoesNotThrow(() -> SiteRules.checkSite("A", BigDecimal.valueOf(-26.2), BigDecimal.valueOf(28.0)));
        assertDoesNotThrow(() -> SiteRules.checkSite("A", null, null));
    }
    @Test void siteCreateTrimsAndDeactivatesOnce() {
        EarthSite s = EarthSite.create(TenantId.generate(), "  Mine A ", " ", null, null, null, null, null, "");
        assertEquals("Mine A", s.getName());
        assertNull(s.getAddress());
        assertNull(s.getNotes());
        assertTrue(s.isActive());
        s.deactivate();
        assertFalse(s.isActive());
        assertThrows(IllegalStateException.class, s::deactivate);
    }
    @Test void deploymentLinksFillEmptyOnlyAndKeepTheText() {
        EarthDeployment d = EarthDeployment.create(TenantId.generate(), UUID.randomUUID(), "Mine A", "Acme", null, null,
                LocalDate.now(), null, null);
        UUID first = UUID.randomUUID(), other = UUID.randomUUID();
        assertTrue(d.linkSiteIfEmpty(first));
        assertFalse(d.linkSiteIfEmpty(other));
        assertEquals(first, d.getSiteId());
        assertFalse(d.linkCustomerIfEmpty(null));
        assertTrue(d.linkCustomerIfEmpty(other));
        assertTrue(d.linkProjectIfEmpty(other));
        assertEquals("Mine A", d.getSiteName());
        assertEquals("Acme", d.getClientName());
    }
}
