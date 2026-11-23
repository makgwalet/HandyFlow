package za.co.handyflow.platform.agriculture.domain.rules;

import org.junit.jupiter.api.Test;

import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;

class AgMovementRulesTest {

    private static final UUID FARM = UUID.randomUUID();
    private static final UUID OTHER_FARM = UUID.randomUUID();

    @Test void typesAreNormalised() {
        assertEquals("AREA_TRANSFER", AgMovementRules.normaliseType(" area_transfer "));
        assertEquals("SALE", AgMovementRules.normaliseType("SALE"));
        assertThrows(IllegalArgumentException.class, () -> AgMovementRules.normaliseType("TELEPORT"));
        assertThrows(IllegalArgumentException.class, () -> AgMovementRules.normaliseType(" "));
        assertThrows(IllegalArgumentException.class, () -> AgMovementRules.normaliseType(null));
    }

    @Test void onlyAreaTransferMovesThings() {
        assertTrue(AgMovementRules.isAreaTransfer("AREA_TRANSFER"));
        for (String t : new String[]{"FARM_TRANSFER", "SALE", "TRANSFER_OUT"}) assertFalse(AgMovementRules.isAreaTransfer(t));
    }

    @Test void areaMustBeOnTheSameFarm() {
        AgMovementRules.requireUsableArea(FARM, "ACTIVE", FARM);
        IllegalArgumentException e = assertThrows(IllegalArgumentException.class, () -> AgMovementRules.requireUsableArea(OTHER_FARM, "ACTIVE", FARM));
        assertTrue(e.getMessage().contains("different farm"));
        assertThrows(IllegalArgumentException.class, () -> AgMovementRules.requireUsableArea(null, "ACTIVE", FARM));
    }

    @Test void inactiveAreaIsRefusedButRestingAndQuarantineAreNot() {
        assertThrows(IllegalArgumentException.class, () -> AgMovementRules.requireUsableArea(FARM, "INACTIVE", FARM));
        AgMovementRules.requireUsableArea(FARM, "RESTING", FARM);
        AgMovementRules.requireUsableArea(FARM, "QUARANTINE", FARM);
    }

    @Test void targetAreaIsRequiredAndMustDiffer() {
        UUID a = UUID.randomUUID(), b = UUID.randomUUID();
        AgMovementRules.requireDifferentArea(a, b);
        AgMovementRules.requireDifferentArea(null, b);
        assertThrows(IllegalArgumentException.class, () -> AgMovementRules.requireDifferentArea(a, a));
        assertThrows(IllegalArgumentException.class, () -> AgMovementRules.requireDifferentArea(a, null));
    }

    @Test void groupCountMustFitTheGroup() {
        AgMovementRules.requireCountWithinGroup(null, 50);
        AgMovementRules.requireCountWithinGroup(1, 50);
        AgMovementRules.requireCountWithinGroup(50, 50);
        assertThrows(IllegalArgumentException.class, () -> AgMovementRules.requireCountWithinGroup(0, 50));
        assertThrows(IllegalArgumentException.class, () -> AgMovementRules.requireCountWithinGroup(-3, 50));
        IllegalArgumentException e = assertThrows(IllegalArgumentException.class, () -> AgMovementRules.requireCountWithinGroup(51, 50));
        assertEquals("Count moved (51) is more than the group has (50)", e.getMessage());
    }

    @Test void onlyAWholeGroupMoveChangesTheGroupsArea() {
        assertTrue(AgMovementRules.movesWholeGroup(null, 50));
        assertTrue(AgMovementRules.movesWholeGroup(50, 50));
        assertFalse(AgMovementRules.movesWholeGroup(20, 50));
    }
}
