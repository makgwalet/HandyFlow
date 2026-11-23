package za.co.handyflow.platform.security.application.internal;

import org.junit.jupiter.api.Test;
import za.co.handyflow.platform.security.application.internal.DeviceComplianceRules.*;
import za.co.handyflow.platform.shared.HandyFlowException;

import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

class DeviceComplianceRulesTest {

    private static String code(Runnable r) { return assertThrows(HandyFlowException.class, r::run).getErrorCode(); }
    private static Policy all() { return new Policy("2.4", 12, true, true); }
    private static Facts good() { return new Facts("2.4", "12", true, false); }

    // ---- cleanPolicy ----

    @Test void anEmptyPolicyAsksForNothing() {
        Policy p = DeviceComplianceRules.cleanPolicy(null, null, null, null);
        assertFalse(p.asksAnything());
        assertEquals(Policy.none(), p);
        assertFalse(DeviceComplianceRules.cleanPolicy("   ", null, false, false).asksAnything());
    }

    @Test void aGoodPolicyIsKeptAndTrimmed() {
        Policy p = DeviceComplianceRules.cleanPolicy(" 3.10.1 ", 14, true, true);
        assertEquals("3.10.1", p.minAppVersion());
        assertEquals(14, p.minOsMajor());
        assertTrue(p.requireScreenLock());
        assertTrue(p.blockRooted());
        assertTrue(p.asksAnything());
    }

    @Test void eachRequirementAloneCountsAsAskingSomething() {
        assertTrue(DeviceComplianceRules.cleanPolicy("1", null, false, false).asksAnything());
        assertTrue(DeviceComplianceRules.cleanPolicy(null, 1, false, false).asksAnything());
        assertTrue(DeviceComplianceRules.cleanPolicy(null, null, true, false).asksAnything());
        assertTrue(DeviceComplianceRules.cleanPolicy(null, null, false, true).asksAnything());
    }

    @Test void badAppVersionsAreRefusedNotChanged() {
        for (String v : List.of("2.x", "v2.4", "1.2.3.4.5", "2..4", ".5", "2.", "two", "2.4-beta", "2 4"))
            assertEquals("DEVICE_POLICY_APP_VERSION", code(() -> DeviceComplianceRules.cleanPolicy(v, null, null, null)), v);
    }

    @Test void aVersionLongerThanTheColumnIsRefused() {
        assertEquals("DEVICE_POLICY_APP_VERSION", code(() -> DeviceComplianceRules.cleanPolicy("1".repeat(31), null, null, null)));
        assertEquals("1".repeat(30), DeviceComplianceRules.cleanPolicy("1".repeat(30), null, null, null).minAppVersion());
    }

    @Test void osMajorMustBeOneToNinetyNine() {
        for (int v : new int[]{0, -1, 100, 1000})
            assertEquals("DEVICE_POLICY_OS_VERSION", code(() -> DeviceComplianceRules.cleanPolicy(null, v, null, null)), "" + v);
        assertEquals(1, DeviceComplianceRules.cleanPolicy(null, 1, null, null).minOsMajor());
        assertEquals(99, DeviceComplianceRules.cleanPolicy(null, 99, null, null).minOsMajor());
    }

    // ---- phone-reported OS ----

    @Test void osVersionIsCleanedOrDropped() {
        assertEquals("14", DeviceComplianceRules.cleanOsVersion("14"));
        assertEquals("Android 14", DeviceComplianceRules.cleanOsVersion("  Android 14 "));
        assertEquals("17.4.1", DeviceComplianceRules.cleanOsVersion("17.4.1"));
        assertNull(DeviceComplianceRules.cleanOsVersion(null));
        assertNull(DeviceComplianceRules.cleanOsVersion(""));
        assertNull(DeviceComplianceRules.cleanOsVersion("   "));
        assertNull(DeviceComplianceRules.cleanOsVersion("<script>14</script>"));
        assertNull(DeviceComplianceRules.cleanOsVersion("Android"));
        assertNull(DeviceComplianceRules.cleanOsVersion("1".repeat(31)));
        assertNull(DeviceComplianceRules.cleanOsVersion("0"));
        assertNull(DeviceComplianceRules.cleanOsVersion("1234"));
    }

    @Test void osMajorIsTheFirstNumber() {
        assertEquals(14, DeviceComplianceRules.osMajor("14"));
        assertEquals(14, DeviceComplianceRules.osMajor("Android 14"));
        assertEquals(17, DeviceComplianceRules.osMajor("17.4.1"));
        assertEquals(9, DeviceComplianceRules.osMajor("9"));
        assertNull(DeviceComplianceRules.osMajor(null));
        assertNull(DeviceComplianceRules.osMajor("Android"));
        assertNull(DeviceComplianceRules.osMajor("0"));
        assertNull(DeviceComplianceRules.osMajor("100"));
    }

    // ---- evaluate ----

    @Test void withNoPolicyEveryPhoneIsCompliantWhatItReports() {
        for (Facts f : List.of(new Facts(null, null, null, null), new Facts("0.1", "1", false, true))) {
            Result r = DeviceComplianceRules.evaluate(Policy.none(), f);
            assertEquals(Status.COMPLIANT, r.status());
            assertTrue(r.violations().isEmpty());
            assertTrue(r.missing().isEmpty());
        }
    }

    @Test void aPhoneThatMeetsEverythingIsCompliantIncludingExactlyAtTheLimits() {
        Result r = DeviceComplianceRules.evaluate(all(), good());
        assertEquals(Status.COMPLIANT, r.status());
        assertEquals(Status.COMPLIANT, DeviceComplianceRules.evaluate(all(), new Facts("2.10", "15", true, false)).status());
    }

    @Test void oneOldValueIsOneViolation() {
        assertEquals(List.of(Violation.APP_TOO_OLD), DeviceComplianceRules.evaluate(all(), new Facts("2.3.9", "12", true, false)).violations());
        assertEquals(List.of(Violation.OS_TOO_OLD), DeviceComplianceRules.evaluate(all(), new Facts("2.4", "11", true, false)).violations());
        assertEquals(List.of(Violation.NO_SCREEN_LOCK), DeviceComplianceRules.evaluate(all(), new Facts("2.4", "12", false, false)).violations());
        assertEquals(List.of(Violation.ROOTED), DeviceComplianceRules.evaluate(all(), new Facts("2.4", "12", true, true)).violations());
    }

    @Test void everythingWrongListsEverythingInOrder() {
        Result r = DeviceComplianceRules.evaluate(all(), new Facts("1.0", "8", false, true));
        assertEquals(Status.NON_COMPLIANT, r.status());
        assertEquals(List.of(Violation.APP_TOO_OLD, Violation.OS_TOO_OLD, Violation.NO_SCREEN_LOCK, Violation.ROOTED), r.violations());
        assertTrue(r.missing().isEmpty());
    }

    @Test void whatThePolicyDoesNotAskForIsNeverHeldAgainstThePhone() {
        Policy appOnly = new Policy("2.4", null, false, false);
        assertEquals(Status.COMPLIANT, DeviceComplianceRules.evaluate(appOnly, new Facts("2.4", "5", false, true)).status());
        Policy rootOnly = new Policy(null, null, false, true);
        assertEquals(Status.COMPLIANT, DeviceComplianceRules.evaluate(rootOnly, new Facts("0.1", "1", false, false)).status());
    }

    @Test void nothingReportedIsUnknownNotFailedAndNotFine() {
        Result r = DeviceComplianceRules.evaluate(all(), new Facts(null, null, null, null));
        assertEquals(Status.UNKNOWN, r.status());
        assertTrue(r.violations().isEmpty());
        assertEquals(List.of(Missing.APP_VERSION, Missing.OS_VERSION, Missing.SCREEN_LOCK, Missing.ROOT_STATUS), r.missing());
    }

    @Test void blankAppVersionAndJunkOsCountAsNotReported() {
        Result r = DeviceComplianceRules.evaluate(all(), new Facts("  ", "Android", true, false));
        assertEquals(Status.UNKNOWN, r.status());
        assertEquals(List.of(Missing.APP_VERSION, Missing.OS_VERSION), r.missing());
    }

    @Test void aKnownFailureBeatsAGap() {
        Result r = DeviceComplianceRules.evaluate(all(), new Facts("2.4", "12", false, null));
        assertEquals(Status.NON_COMPLIANT, r.status());
        assertEquals(List.of(Violation.NO_SCREEN_LOCK), r.violations());
        assertEquals(List.of(Missing.ROOT_STATUS), r.missing());
    }

    @Test void aMissingValueOnlyMattersWhenThePolicyAsksForIt() {
        Policy lockOnly = new Policy(null, null, true, false);
        assertEquals(Status.COMPLIANT, DeviceComplianceRules.evaluate(lockOnly, new Facts(null, null, true, null)).status());
        assertEquals(Status.UNKNOWN, DeviceComplianceRules.evaluate(lockOnly, new Facts(null, null, null, null)).status());
    }

    @Test void theResultListsCannotBeChangedByTheCaller() {
        Result r = DeviceComplianceRules.evaluate(all(), new Facts("1.0", "8", false, true));
        assertThrows(UnsupportedOperationException.class, () -> r.violations().add(Violation.ROOTED));
        assertThrows(UnsupportedOperationException.class, () -> r.missing().add(Missing.APP_VERSION));
    }
}
