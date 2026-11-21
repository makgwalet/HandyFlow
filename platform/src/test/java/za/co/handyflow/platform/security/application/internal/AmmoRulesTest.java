package za.co.handyflow.platform.security.application.internal;

import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class AmmoRulesTest {
    @Test void issueWithoutCountIsAllowed() { assertNull(AmmoRules.checkIssue(null)); }
    @Test void issueZeroAndMaxAllowed() { assertNull(AmmoRules.checkIssue(0)); assertNull(AmmoRules.checkIssue(2000)); }
    @Test void issueNegativeOrHugeRefused() {
        assertEquals("AMMO_COUNT_INVALID", AmmoRules.checkIssue(-1));
        assertEquals("AMMO_COUNT_INVALID", AmmoRules.checkIssue(2001));
    }

    @Test void returnAllRoundsIsFine() { assertNull(AmmoRules.checkReturn(30, 30, null)); }
    @Test void returnMustStateCountWhenRoundsWereIssued() { assertEquals("AMMO_COUNT_REQUIRED", AmmoRules.checkReturn(30, null, null)); }
    @Test void returnCannotExceedIssued() { assertEquals("AMMO_RETURN_EXCEEDS_ISSUED", AmmoRules.checkReturn(30, 31, null)); }
    @Test void shortReturnNeedsExplanation() {
        assertEquals("AMMO_EXPLANATION_REQUIRED", AmmoRules.checkReturn(30, 28, null));
        assertEquals("AMMO_EXPLANATION_REQUIRED", AmmoRules.checkReturn(30, 28, "   "));
        assertNull(AmmoRules.checkReturn(30, 28, "Fired 2 rounds at an intruder"));
    }
    @Test void returnNegativeRefused() { assertEquals("AMMO_COUNT_INVALID", AmmoRules.checkReturn(30, -1, "x")); }
    @Test void nothingRecordedAtIssueAcceptsSaneReturn() {
        assertNull(AmmoRules.checkReturn(null, null, null));
        assertNull(AmmoRules.checkReturn(null, 12, null));
        assertEquals("AMMO_COUNT_INVALID", AmmoRules.checkReturn(null, 5000, null));
    }
    @Test void shortfallIsIssuedMinusReturned() {
        assertEquals(2, AmmoRules.shortfall(30, 28));
        assertEquals(0, AmmoRules.shortfall(30, 30));
        assertEquals(0, AmmoRules.shortfall(10, 12));
        assertEquals(0, AmmoRules.shortfall(null, 5));
        assertEquals(0, AmmoRules.shortfall(30, null));
    }
    @Test void labelReads() {
        assertEquals("30 x 9mm", AmmoRules.label(" 9mm ", 30));
        assertEquals("30 rounds", AmmoRules.label(null, 30));
        assertEquals("—", AmmoRules.label("9mm", null));
    }
}
