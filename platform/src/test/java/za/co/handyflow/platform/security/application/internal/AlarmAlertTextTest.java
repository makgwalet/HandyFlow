package za.co.handyflow.platform.security.application.internal;

import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class AlarmAlertTextTest {
    @Test void duressNamesGuardAndSite() {
        var t = AlarmAlertText.duress("Thabo", "Sandton Mall");
        assertEquals("PANIC: Thabo at Sandton Mall", t.title());
        assertTrue(t.message().contains("Thabo at Sandton Mall"));
    }
    @Test void duressWithoutNamesStillReadable() {
        var t = AlarmAlertText.duress(null, " ");
        assertEquals("PANIC: A guard", t.title());
    }
    @Test void smsNeverExceedsOneSegment() {
        var t = AlarmAlertText.critical("ALARM_PANEL", "x".repeat(400));
        assertTrue(t.message().length() <= AlarmAlertText.SMS_MAX);
        assertTrue(t.message().endsWith("…"));
    }
    @Test void shortTextNotCut() {
        assertEquals("abc", AlarmAlertText.fit("abc"));
    }
    @Test void criticalReadsSourceWords() {
        var t = AlarmAlertText.critical("CCTV_MOTION", null);
        assertEquals("CRITICAL alarm: cctv motion", t.title());
    }

    @Test void incidentNamesSiteAndTitle() {
        var t = AlarmAlertText.incident("Armed robbery", "Sandton Mall");
        assertEquals("CRITICAL incident at Sandton Mall", t.title());
        assertTrue(t.message().contains("Armed robbery"));
    }
    @Test void incidentSmsIsCappedAndSurvivesNulls() {
        assertTrue(AlarmAlertText.incident("x".repeat(500), "Site").message().length() <= AlarmAlertText.SMS_MAX);
        assertEquals("CRITICAL incident", AlarmAlertText.incident(null, null).title());
    }
}
