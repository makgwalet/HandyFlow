package za.co.handyflow.platform.security.application.internal;

import static org.junit.jupiter.api.Assertions.*;
import static za.co.handyflow.platform.security.application.internal.EnrollDeviceRules.Action.*;

import java.nio.file.*;
import java.util.UUID;
import org.junit.jupiter.api.Test;

class EnrollDeviceRulesTest {
    private static final UUID A = UUID.randomUUID(), B = UUID.randomUUID();
    private static final String P = "PERSONAL_GUARD_DEVICE";

    @Test void unknownPhoneIsCreated() { assertEquals(CREATE, EnrollDeviceRules.decide(false, null, null, null, A)); }

    @Test void sameGuardsOwnPhoneIsKept() {
        for (String st : new String[]{"PENDING", "ACTIVE", "REPLACED"})
            assertEquals(KEEP, EnrollDeviceRules.decide(true, P, st, A, A), st);
    }

    @Test void phoneWithNoOwnerIsClaimed() { assertEquals(KEEP, EnrollDeviceRules.decide(true, P, "ACTIVE", null, A)); }

    @Test void withdrawnPhonesAreNeverBroughtBack() {
        for (String st : new String[]{"LOST", "REVOKED", "BLOCKED", "DECOMMISSIONED"}) {
            assertEquals(REFUSE_UNAVAILABLE, EnrollDeviceRules.decide(true, P, st, A, A), st);
            assertEquals(REFUSE_UNAVAILABLE, EnrollDeviceRules.decide(true, P, st, B, A), st);
        }
    }

    @Test void sharedDeviceIsNotAPersonalPhone() {
        assertEquals(REFUSE_SHARED, EnrollDeviceRules.decide(true, "SHARED_SITE_DEVICE", "ACTIVE", null, A));
        assertEquals(REFUSE_SHARED, EnrollDeviceRules.decide(true, "SHARED_SITE_DEVICE", "LOST", B, A));
    }

    @Test void anotherGuardsPhoneOnlyMovesOnceTheyWereMovedOffIt() {
        assertEquals(REBIND, EnrollDeviceRules.decide(true, P, "REPLACED", B, A));
        assertEquals(REFUSE_OTHER_GUARD, EnrollDeviceRules.decide(true, P, "ACTIVE", B, A));
        assertEquals(REFUSE_OTHER_GUARD, EnrollDeviceRules.decide(true, P, "PENDING", B, A));
    }

    @Test void previousPhoneIsRetiredOnlyWhenDifferent() {
        assertTrue(EnrollDeviceRules.retirePrevious("old", "new"));
        assertFalse(EnrollDeviceRules.retirePrevious("same", "same"));
        assertFalse(EnrollDeviceRules.retirePrevious(null, "new"));
    }

    private static String svc() throws Exception {
        return Files.readString(Path.of("src/main/java/za/co/handyflow/platform/security/application/internal/GuardAuthService.java"));
    }

    @Test void enrolUsesTheRulesAndNoLongerJustActivates() throws Exception {
        String s = svc();
        assertTrue(s.contains("EnrollDeviceRules.decide("));
        assertFalse(s.contains("SecurityDevice::activate,"), "blind activate of a known device must be gone");
        for (String c : new String[]{"DEVICE_IS_SHARED", "DEVICE_UNAVAILABLE", "DEVICE_IN_USE"}) assertTrue(s.contains(c), c);
        assertTrue(s.contains("d.rebindTo(guard.getId())") && s.contains("EnrollDeviceRules.retirePrevious("));
        assertTrue(s.contains("String previousDeviceId = guard.getRegisteredDeviceId();"));
        assertTrue(s.indexOf("String previousDeviceId") < s.indexOf("guard.setRegisteredDeviceId(req.deviceHardwareId())"),
                "previous device read before it is overwritten");
    }

    @Test void deviceCanBeRebound() throws Exception {
        String d = Files.readString(Path.of("src/main/java/za/co/handyflow/platform/security/domain/model/SecurityDevice.java"));
        assertTrue(d.contains("void rebindTo(UUID newGuardId)") && d.contains("this.guardId   = newGuardId"));
    }
}
