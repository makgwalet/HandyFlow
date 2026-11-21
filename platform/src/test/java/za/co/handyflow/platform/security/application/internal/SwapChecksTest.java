package za.co.handyflow.platform.security.application.internal;

import org.junit.jupiter.api.Test;
import java.time.LocalDate;

import static org.junit.jupiter.api.Assertions.*;

class SwapChecksTest {
    private static final LocalDate SHIFT = LocalDate.of(2026, 11, 25);

    @Test void allGood() {
        var r = SwapChecks.evaluate(true, true, "ACTIVE", SHIFT.plusDays(100), SHIFT, false);
        assertTrue(r.allPassed()); assertFalse(r.blocked());
    }
    @Test void expiredPsiraBlocks() {
        var r = SwapChecks.evaluate(true, true, "ACTIVE", SHIFT.minusDays(1), SHIFT, false);
        assertTrue(r.blocked());
        assertTrue(r.blocking().get(0).contains("PSiRA"));
    }
    @Test void psiraExpiringOnShiftDateIsFine() {
        assertFalse(SwapChecks.evaluate(true, true, "ACTIVE", SHIFT, SHIFT, false).blocked());
    }
    @Test void noPsiraDateDoesNotBlock() {
        assertFalse(SwapChecks.evaluate(true, true, "ACTIVE", null, SHIFT, false).blocked());
    }
    @Test void suspendedBlocks() {
        var r = SwapChecks.evaluate(true, false, "SUSPENDED", null, SHIFT, false);
        assertTrue(r.blocked()); assertTrue(r.blocking().get(0).contains("SUSPENDED"));
    }
    @Test void missingGuardBlocks() {
        assertTrue(SwapChecks.evaluate(false, false, null, null, SHIFT, false).blocked());
    }
    @Test void overlapIsOnlyAWarning() {
        var r = SwapChecks.evaluate(true, true, "ACTIVE", null, SHIFT, true);
        assertFalse(r.blocked()); assertFalse(r.allPassed()); assertEquals(1, r.warnings().size());
    }
}
