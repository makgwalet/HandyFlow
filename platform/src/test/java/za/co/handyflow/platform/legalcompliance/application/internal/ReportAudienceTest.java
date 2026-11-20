package za.co.handyflow.platform.legalcompliance.application.internal;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import za.co.handyflow.platform.legalcompliance.application.internal.ReportService.Audience;

import java.time.Instant;

import static org.junit.jupiter.api.Assertions.*;

class ReportAudienceTest {

    @Test @DisplayName("A report is visible to its minimum audience and above, never below, and never to nobody")
    void visibility() {
        assertTrue(ReportService.visible(Audience.PROVIDER, Audience.PROVIDER));
        assertTrue(ReportService.visible(Audience.PROVIDER, Audience.ADMIN));
        assertFalse(ReportService.visible(Audience.STAFF, Audience.PROVIDER));
        assertTrue(ReportService.visible(Audience.STAFF, Audience.STAFF));
        assertFalse(ReportService.visible(Audience.ADMIN, Audience.STAFF));
        assertFalse(ReportService.visible(Audience.ADMIN, Audience.PROVIDER));
        assertTrue(ReportService.visible(Audience.ADMIN, Audience.ADMIN));
        assertFalse(ReportService.visible(Audience.PROVIDER, null));
    }

    @Test @DisplayName("The audiences are ordered from least to most trusted, so a new one cannot silently slip below provider")
    void ordering() {
        assertEquals("PROVIDER,STAFF,ADMIN", String.join(",", java.util.Arrays.stream(Audience.values()).map(Enum::name).toList()));
    }

    @Test @DisplayName("An instant is reported as its South African calendar date")
    void dateOf() {
        assertEquals("2026-11-20", ReportRules.dateOf(Instant.parse("2026-11-19T22:30:00Z")));
        assertEquals("", ReportRules.dateOf(null));
    }
}
