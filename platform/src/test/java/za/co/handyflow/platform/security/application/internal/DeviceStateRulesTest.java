package za.co.handyflow.platform.security.application.internal;

import static org.junit.jupiter.api.Assertions.*;
import static za.co.handyflow.platform.security.application.internal.DeviceStateRules.Action.*;

import java.nio.file.*;
import org.junit.jupiter.api.Test;

class DeviceStateRulesTest {
    private static final String[] ALL = {"PENDING", "ACTIVE", "REVOKED", "LOST", "BLOCKED", "REPLACED", "DECOMMISSIONED"};

    private static String allowedFor(DeviceStateRules.Action a) {
        StringBuilder b = new StringBuilder();
        for (String s : ALL) if (DeviceStateRules.allowed(a, s)) b.append(s).append(',');
        return b.toString();
    }

    @Test void lostOnlyFromWorkingStates() { assertEquals("PENDING,ACTIVE,", allowedFor(LOST)); }
    @Test void revokeFromAnyStateStillInPlay() { assertEquals("PENDING,ACTIVE,LOST,", allowedFor(REVOKE)); }
    @Test void reinstateOnlyFromLostOrRevoked() { assertEquals("REVOKED,LOST,", allowedFor(REINSTATE)); }
    @Test void nullsAreRefused() {
        assertFalse(DeviceStateRules.allowed(null, "ACTIVE"));
        assertFalse(DeviceStateRules.allowed(LOST, null));
    }
    @Test void sessionsEndForLostAndRevokeOnly() {
        assertTrue(DeviceStateRules.endsSessions(LOST));
        assertTrue(DeviceStateRules.endsSessions(REVOKE));
        assertFalse(DeviceStateRules.endsSessions(REINSTATE));
    }
    @Test void reinstateNeedsTheGuardsCurrentDevice() {
        assertTrue(DeviceStateRules.isGuardsCurrentDevice("hw1", "hw1"));
        assertFalse(DeviceStateRules.isGuardsCurrentDevice("hw2", "hw1"));
        assertFalse(DeviceStateRules.isGuardsCurrentDevice(null, "hw1"));
    }

    private static String read(String p) throws Exception { return Files.readString(Path.of("src/main/java/za/co/handyflow/platform/security/" + p)); }

    @Test void controllerAuthorities() throws Exception {
        String c = read("api/PersonalDeviceController.java");
        assertTrue(c.contains("@PostMapping(\"/{id}/lost\")\n    @PreAuthorize(\"hasAuthority('SECURITY_MANAGE')\")"));
        assertTrue(c.contains("@PostMapping(\"/{id}/revoke\")\n    @PreAuthorize(\"hasAuthority('SECURITY_MANAGE')\")"));
        assertTrue(c.contains("@PostMapping(\"/{id}/reinstate\")\n    @PreAuthorize(\"hasAuthority('SECURITY_ADMIN')\")"));
        assertEquals(3, c.split("@PostMapping", -1).length - 1);
    }

    @Test void serviceChecksStateEndsSessionsAndOnlyForPersonalDevices() throws Exception {
        String s = read("application/internal/DeviceManagementService.java");
        assertTrue(s.contains("DeviceStateRules.allowed(action, status)"));
        assertTrue(s.contains("DeviceType.PERSONAL_GUARD_DEVICE"));
        assertTrue(s.contains("DeviceStateRules.endsSessions(action)") && s.contains("revokeActiveOnDevice("));
        assertTrue(s.contains("DeviceStateRules.isGuardsCurrentDevice("), "reinstate must check the guard's current device");
        assertTrue(s.contains("DEVICE_STATE_NOT_ALLOWED") && s.contains("DEVICE_NOT_CURRENT"));
    }
}
