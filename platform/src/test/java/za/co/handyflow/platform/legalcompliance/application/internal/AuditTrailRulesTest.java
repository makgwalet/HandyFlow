package za.co.handyflow.platform.legalcompliance.application.internal;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.time.Duration;
import java.time.Instant;
import java.time.LocalDate;

import static org.junit.jupiter.api.Assertions.*;

class AuditTrailRulesTest {

    @Test @DisplayName("Restricted areas are not on the allow-list, so their events can never appear")
    void restrictedExcluded() {
        for (String restricted : new String[]{"FICA_PROFILE", "FICA_PARTY", "FICA_ESCALATION", "COI_DECLARATION", "GIFT_ENTRY", "ATTESTATION", "PAIA_REQUEST", "WATCH_ITEM", "SOMETHING_NEW"}) {
            assertFalse(AuditTrailRules.allowedTypes().contains(restricted), restricted);
            assertNull(AuditTrailRules.area(restricted));
        }
        assertNull(AuditTrailRules.area(null));
    }

    @Test @DisplayName("Every allowed area has a readable name")
    void allowedNamed() {
        for (String t : AuditTrailRules.allowedTypes()) assertFalse(AuditTrailRules.area(t).isBlank(), t);
        assertEquals("Control test", AuditTrailRules.area("CONTROL_TEST"));
    }

    @Test @DisplayName("The window is 90 days ending at the close of the chosen day, in South African time")
    void window() {
        LocalDate today = LocalDate.of(2026, 11, 19);
        Instant[] w = AuditTrailRules.window(null, today);
        assertEquals(Duration.ofDays(90), Duration.between(w[0], w[1]));
        // midnight at the start of 20 November in Johannesburg (UTC+2) is 22:00 UTC on the 19th
        assertEquals(Instant.parse("2026-11-19T22:00:00Z"), w[1]);
        Instant[] past = AuditTrailRules.window(LocalDate.of(2026, 3, 31), today);
        assertEquals(Instant.parse("2026-03-31T22:00:00Z"), past[1]);
        assertTrue(past[1].isBefore(w[1]));
    }

    @Test @DisplayName("Timestamps are shown in SAST and event codes are made readable")
    void labels() {
        assertEquals("2026-11-19 22:30", AuditTrailRules.stamp(Instant.parse("2026-11-19T20:30:00Z")));
        assertEquals("", AuditTrailRules.stamp(null));
        assertEquals("Resolution rejected", AuditTrailRules.eventLabel("RESOLUTION_REJECTED"));
        assertEquals("Created", AuditTrailRules.eventLabel("CREATED"));
        assertEquals("", AuditTrailRules.eventLabel(null));
    }
}
