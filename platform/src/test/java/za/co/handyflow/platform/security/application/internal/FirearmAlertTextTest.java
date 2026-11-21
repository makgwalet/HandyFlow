package za.co.handyflow.platform.security.application.internal;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class FirearmAlertTextTest {
    @Test void namesSerialAndDeadline() {
        var t = AlarmAlertText.firearmLost("SN-123", false);
        assertEquals("Firearm lost or stolen: SN-123", t.title());
        assertTrue(t.message().contains("SAPS within 24 hours"));
        assertFalse(t.message().contains("issued to a guard"));
    }
    @Test void saysWhenItWasIssued() { assertTrue(AlarmAlertText.firearmLost("SN-123", true).message().contains("issued to a guard")); }
    @Test void staysInOneSmsSegment() { assertTrue(AlarmAlertText.firearmLost("S".repeat(300), true).message().length() <= AlarmAlertText.SMS_MAX); }
    @Test void missingSerialReadable() { assertEquals("Firearm lost or stolen: unknown serial", AlarmAlertText.firearmLost(" ", false).title()); }
}
