package za.co.handyflow.platform.security.application.internal;

import org.junit.jupiter.api.Test;
import za.co.handyflow.platform.security.domain.model.ResourceCustody.ResourceType;

import java.time.Instant;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

class ClockOutRulesTest {
    @Test void nothingOpenNeverBlocks() {
        assertFalse(ClockOutRules.blocksClockOut(List.of(), false, true));
    }
    @Test void firearmBlocksEvenWithFlag() {
        assertTrue(ClockOutRules.blocksClockOut(List.of(ResourceType.FIREARM), true, false));
        assertTrue(ClockOutRules.blocksClockOut(List.of(ResourceType.FIREARM), true, true));
    }
    @Test void guardRouteBlocksAnyOpenItemEvenWithFlag() {
        assertTrue(ClockOutRules.blocksClockOut(List.of(ResourceType.RADIO), true, true));
    }
    @Test void supervisorMayClearNonFirearmWithFlag() {
        assertFalse(ClockOutRules.blocksClockOut(List.of(ResourceType.RADIO), true, false));
        assertTrue(ClockOutRules.blocksClockOut(List.of(ResourceType.RADIO), false, false));
    }

    private static final Instant NOW = Instant.parse("2026-11-20T10:00:00Z");
    @Test void startingSoonMatches() {
        assertTrue(ClockOutRules.shiftMatches(NOW.plusSeconds(20 * 60), NOW.plusSeconds(8 * 3600), NOW, 30));
    }
    @Test void startingTooFarAheadDoesNot() {
        assertFalse(ClockOutRules.shiftMatches(NOW.plusSeconds(31 * 60), NOW.plusSeconds(8 * 3600), NOW, 30));
    }
    @Test void lateButShiftStillRunningMatches() {
        assertTrue(ClockOutRules.shiftMatches(NOW.minusSeconds(90 * 60), NOW.plusSeconds(6 * 3600), NOW, 30));
    }
    @Test void shiftAlreadyEndedDoesNotMatch() {
        assertFalse(ClockOutRules.shiftMatches(NOW.minusSeconds(10 * 3600), NOW.minusSeconds(2 * 3600), NOW, 30));
    }
}
