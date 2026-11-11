package za.co.handyflow.platform.warehousing.application.internal;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import java.util.List;
import za.co.handyflow.platform.warehousing.application.internal.WhsePackageRules.Enforcement;
import za.co.handyflow.platform.warehousing.application.internal.WhsePackageRules.Limit;

import static org.junit.jupiter.api.Assertions.*;

class WhsePackageRulesTest {

    @Test @DisplayName("null limit is unlimited")
    void unlimited() {
        assertFalse(WhsePackageRules.wouldExceed(1_000_000, null));
        assertFalse(WhsePackageRules.isOver(1_000_000, null));
    }

    @Test @DisplayName("adding the record that reaches the limit is allowed, the next one is not")
    void boundary() {
        assertFalse(WhsePackageRules.wouldExceed(2, 3));
        assertTrue(WhsePackageRules.wouldExceed(3, 3));
        assertFalse(WhsePackageRules.isOver(3, 3));
        assertTrue(WhsePackageRules.isOver(4, 3));
    }

    @Test @DisplayName("a limit of zero refuses the first one")
    void zero() {
        assertTrue(WhsePackageRules.wouldExceed(0, 0));
    }

    @Test @DisplayName("only ENFORCE refuses, and the message names the package, limit and usage")
    void enforcement() {
        assertDoesNotThrow(() -> WhsePackageRules.checkAdd(Limit.WAREHOUSES, 1, 1, Enforcement.OFF, "Starter"));
        assertDoesNotThrow(() -> WhsePackageRules.checkAdd(Limit.WAREHOUSES, 1, 1, Enforcement.MONITOR, "Starter"));
        IllegalStateException e = assertThrows(IllegalStateException.class, () -> WhsePackageRules.checkAdd(Limit.WAREHOUSES, 1, 1, Enforcement.ENFORCE, "Starter"));
        assertTrue(e.getMessage().contains("Starter") && e.getMessage().contains("1 warehouses") && e.getMessage().contains("larger package"));
        assertDoesNotThrow(() -> WhsePackageRules.checkAdd(Limit.WAREHOUSES, 0, 1, Enforcement.ENFORCE, "Starter"));
    }

    @Test @DisplayName("enforcement parses case-insensitively and rejects the rest")
    void parse() {
        assertEquals(Enforcement.ENFORCE, WhsePackageRules.parseEnforcement(" enforce "));
        assertThrows(IllegalArgumentException.class, () -> WhsePackageRules.parseEnforcement("maybe"));
        assertEquals(Enforcement.MONITOR, WhsePackageRules.parseEnforcement("monitor"));
        assertThrows(IllegalArgumentException.class, () -> WhsePackageRules.parseEnforcement("REPORT")); // renamed to MONITOR
        assertThrows(IllegalArgumentException.class, () -> WhsePackageRules.parseEnforcement(null));
    }

    private static final java.time.LocalDate D = java.time.LocalDate.of(2026, 11, 11);

    @Test @DisplayName("a tier applies from its start date to its expiry, both inclusive; no date means no bound")
    void window() {
        assertEquals(WhsePackageRules.WindowState.ACTIVE, WhsePackageRules.windowState(null, null, D));
        assertEquals(WhsePackageRules.WindowState.SCHEDULED, WhsePackageRules.windowState(D.plusDays(1), null, D));
        assertEquals(WhsePackageRules.WindowState.ACTIVE, WhsePackageRules.windowState(D, D, D));
        assertEquals(WhsePackageRules.WindowState.EXPIRED, WhsePackageRules.windowState(null, D.minusDays(1), D));
        assertThrows(IllegalArgumentException.class, () -> WhsePackageRules.checkWindow(D, D.minusDays(1)));
        assertDoesNotThrow(() -> WhsePackageRules.checkWindow(null, D.minusDays(1)));
    }

    @Test @DisplayName("an override adds to a limit and leaves unlimited unlimited")
    void effective() {
        assertEquals(15, WhsePackageRules.effectiveLimit(10, 5));
        assertNull(WhsePackageRules.effectiveLimit(null, 5));
        assertEquals(10, WhsePackageRules.effectiveLimit(10, 0));
    }

    @Test @DisplayName("an override needs a sensible amount, an end date within a year and a real reason")
    void overrideChecks() {
        assertDoesNotThrow(() -> WhsePackageRules.checkOverride(50, D.plusDays(30), "Seasonal peak agreed with the customer", D));
        assertThrows(IllegalArgumentException.class, () -> WhsePackageRules.checkOverride(0, D.plusDays(30), "Seasonal peak agreed", D));
        assertThrows(IllegalArgumentException.class, () -> WhsePackageRules.checkOverride(50, D.minusDays(1), "Seasonal peak agreed", D));
        assertThrows(IllegalArgumentException.class, () -> WhsePackageRules.checkOverride(50, D.plusDays(400), "Seasonal peak agreed", D));
        assertThrows(IllegalArgumentException.class, () -> WhsePackageRules.checkOverride(50, D.plusDays(30), "short", D));
        assertThrows(IllegalArgumentException.class, () -> WhsePackageRules.checkOverride(50, D.plusDays(30), null, D));
    }

    @Test @DisplayName("upgrade options are the higher tiers that the current usage would fit")
    void upgrades() {
        List<WhsePackageRules.Tier> all = List.of(
                new WhsePackageRules.Tier("STARTER", "Starter", new java.math.BigDecimal("699"), 1, new Integer[]{1, 3, 500, 0}),
                new WhsePackageRules.Tier("SME", "SME", new java.math.BigDecimal("1999"), 2, new Integer[]{2, 10, 3000, 1}),
                new WhsePackageRules.Tier("PRO", "Professional", new java.math.BigDecimal("4999"), 3, new Integer[]{5, 40, 15000, 5}),
                new WhsePackageRules.Tier("ENT", "Enterprise", null, 5, new Integer[]{null, null, null, null}));
        // On Starter with 3 warehouses: SME (2) cannot hold that, Professional and Enterprise can.
        List<String> codes = WhsePackageRules.upgradeOptions(1, all, new long[]{3, 2, 100, 0}).stream().map(WhsePackageRules.Tier::code).toList();
        assertEquals(List.of("PRO", "ENT"), codes);
        assertTrue(WhsePackageRules.upgradeOptions(5, all, new long[]{9, 9, 9, 9}).isEmpty()); // nothing above the top
    }
}
