package za.co.handyflow.platform.facilitiesmanagement.application.internal;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class FmPriorityRulesTest {

    @Test
    void noPriorityDefaultsToNormal() {
        assertEquals("NORMAL", FmPriorityRules.resolve(null, "CORRECTIVE", false));
        assertEquals("NORMAL", FmPriorityRules.resolve("  ", "CORRECTIVE", false));
    }

    @Test
    void priorityIsCaseInsensitive() {
        assertEquals("HIGH", FmPriorityRules.resolve(" high ", "CORRECTIVE", false));
    }

    @Test
    void unknownPriorityIsRejected() {
        assertThrows(IllegalArgumentException.class, () -> FmPriorityRules.resolve("CRITICAL", "CORRECTIVE", false));
    }

    @Test
    void anEmergencyCategoryIsNeverBelowEmergency() {
        assertEquals("EMERGENCY", FmPriorityRules.resolve("LOW", "EMERGENCY", false));
        assertEquals("EMERGENCY", FmPriorityRules.resolve(null, "EMERGENCY", false));
        assertEquals("EMERGENCY", FmPriorityRules.resolve("EMERGENCY", "EMERGENCY", false));
    }

    @Test
    void aSafetyImpactIsNeverBelowHigh() {
        assertEquals("HIGH", FmPriorityRules.resolve("LOW", "CORRECTIVE", true));
        assertEquals("HIGH", FmPriorityRules.resolve("NORMAL", "CORRECTIVE", true));
        assertEquals("URGENT", FmPriorityRules.resolve("URGENT", "CORRECTIVE", true)); // can be raised above the floor
    }

    @Test
    void aRequesterCanRaiseButNotLowerPastTheFloor() {
        assertEquals("URGENT", FmPriorityRules.resolve("URGENT", "CORRECTIVE", false));
        assertEquals("LOW", FmPriorityRules.resolve("LOW", "CORRECTIVE", false));
    }
}
