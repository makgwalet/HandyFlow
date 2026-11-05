package za.co.handyflow.platform.earthmoving;

import org.junit.jupiter.api.Test;
import za.co.handyflow.platform.earthmoving.domain.model.AssetStatus;

import static org.junit.jupiter.api.Assertions.*;

class AssetStatusTest {

    @Test void anIdleMachineCanBeTakenOutOfServiceByABreakdown() {
        assertTrue(AssetStatus.AVAILABLE.canTransitionTo(AssetStatus.BREAKDOWN));
    }

    @Test void aBrokenMachineIsNotDeployableUntilReleased() {
        assertFalse(AssetStatus.BREAKDOWN.canTransitionTo(AssetStatus.DEPLOYED));
        assertFalse(AssetStatus.BREAKDOWN.canTransitionTo(AssetStatus.HIRED_OUT));
        assertTrue(AssetStatus.BREAKDOWN.canTransitionTo(AssetStatus.AVAILABLE));
    }

    @Test void retiredIsTerminal() {
        for (AssetStatus to : AssetStatus.values()) assertFalse(AssetStatus.RETIRED.canTransitionTo(to), to.name());
    }
}
