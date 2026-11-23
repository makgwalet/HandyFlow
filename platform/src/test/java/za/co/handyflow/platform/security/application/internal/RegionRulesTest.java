package za.co.handyflow.platform.security.application.internal;

import org.junit.jupiter.api.Test;
import za.co.handyflow.platform.shared.HandyFlowException;

import static org.junit.jupiter.api.Assertions.*;

class RegionRulesTest {

    private static String code(Runnable r) { return assertThrows(HandyFlowException.class, r::run).getErrorCode(); }

    @Test void aNameIsTrimmedAndRunsOfSpacesBecomeOne() {
        assertEquals("Western Cape", RegionRules.cleanName("  Western   Cape \t"));
        assertEquals("KZN", RegionRules.cleanName("KZN"));
    }

    @Test void anEmptyOrMissingNameIsRefused() {
        assertEquals("REGION_NAME", code(() -> RegionRules.cleanName(null)));
        assertEquals("REGION_NAME", code(() -> RegionRules.cleanName("")));
        assertEquals("REGION_NAME", code(() -> RegionRules.cleanName("  \t ")));
    }

    @Test void theNameLimitMatchesTheColumn() {
        assertEquals(100, RegionRules.cleanName("x".repeat(100)).length());
        assertEquals("REGION_NAME", code(() -> RegionRules.cleanName("x".repeat(101))));
        assertEquals(100, RegionRules.NAME_MAX);
    }

    @Test void theLengthIsCountedAfterCleaning() {
        assertEquals(100, RegionRules.cleanName("  " + "x".repeat(100) + "  ").length());
        assertEquals(3, RegionRules.cleanName("a" + " ".repeat(500) + "b").length());
    }

    @Test void aDescriptionIsTrimmedOrNullWhenEmpty() {
        assertNull(RegionRules.cleanDescription(null));
        assertNull(RegionRules.cleanDescription("   "));
        assertEquals("Coastal sites", RegionRules.cleanDescription("  Coastal sites "));
        assertEquals(500, RegionRules.cleanDescription("d".repeat(500)).length());
        assertEquals("REGION_DESCRIPTION", code(() -> RegionRules.cleanDescription("d".repeat(501))));
    }
}
