package za.co.handyflow.platform.security.application.internal;

import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;
import static za.co.handyflow.platform.security.application.internal.DeviceLock.Verdict.*;

class DeviceLockTest {
    @Test void unboundGuardIsUnrestricted() {
        assertEquals(OK, DeviceLock.check(null, null));
        assertEquals(OK, DeviceLock.check(" ", "any"));
    }
    @Test void boundGuardOnOwnDevice() { assertEquals(OK, DeviceLock.check("dev-1", "dev-1")); }
    @Test void boundGuardOnOtherDevice() { assertEquals(MISMATCH, DeviceLock.check("dev-1", "dev-2")); }
    @Test void omittingTheDeviceIdIsNotAWayRound() {
        assertEquals(REQUIRED, DeviceLock.check("dev-1", null));
        assertEquals(REQUIRED, DeviceLock.check("dev-1", ""));
    }
}
