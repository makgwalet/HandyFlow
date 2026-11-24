package za.co.handyflow.platform.accounting.domain.rules;

import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.TimeZone;

import static org.junit.jupiter.api.Assertions.*;
import static za.co.handyflow.platform.accounting.domain.rules.PostingDateRules.documentDate;

class PostingDateRulesTest {

    private static final ZoneId SAST = ZoneId.of("Africa/Johannesburg");
    private static final LocalDate TODAY = LocalDate.of(2030, 1, 1);

    @Test void justBeforeMidnightInJohannesburgIsStillThatDay() {
        assertEquals(LocalDate.of(2026, 2, 28), documentDate(SAST, Instant.parse("2026-02-28T21:59:59Z"), TODAY));
    }

    @Test void midnightInJohannesburgIsTheNextDay() {
        assertEquals(LocalDate.of(2026, 3, 1), documentDate(SAST, Instant.parse("2026-02-28T22:00:00Z"), TODAY));
    }

    @Test void anInvoiceIssuedAt0130SastOnTheFirstBelongsToThatMonthNotTheLastOne() {
        // 01:30 SAST on 1 April is 23:30 UTC on 31 March: a UTC server would date it into March.
        Instant issued = Instant.parse("2026-03-31T23:30:00Z");
        assertEquals(LocalDate.of(2026, 4, 1), documentDate(SAST, issued, TODAY));
        assertEquals(LocalDate.of(2026, 3, 31), issued.atZone(ZoneId.of("UTC")).toLocalDate());
    }

    @Test void theDateIsFixedByTheDocumentNotByWhenTheListenerRuns() {
        Instant issued = Instant.parse("2026-05-10T09:00:00Z");
        assertEquals(LocalDate.of(2026, 5, 10), documentDate(SAST, issued, LocalDate.of(2026, 5, 11)));
        assertEquals(LocalDate.of(2026, 5, 10), documentDate(SAST, issued, LocalDate.of(2026, 6, 2)));
    }

    @Test void aCompanyInAnotherZoneGetsItsOwnDay() {
        Instant issued = Instant.parse("2026-05-10T20:00:00Z");
        assertEquals(LocalDate.of(2026, 5, 10), documentDate(SAST, issued, TODAY));
        assertEquals(LocalDate.of(2026, 5, 11), documentDate(ZoneId.of("Pacific/Auckland"), issued, TODAY));
    }

    @Test void anOldEventWithNoTimeFallsBackToTheCompanysToday() {
        assertEquals(TODAY, documentDate(SAST, null, TODAY));
    }

    @Test void theServersOwnZoneDoesNotChangeTheAnswer() {
        TimeZone before = TimeZone.getDefault();
        try {
            TimeZone.setDefault(TimeZone.getTimeZone("Pacific/Auckland"));
            assertEquals(LocalDate.of(2026, 3, 1), documentDate(SAST, Instant.parse("2026-02-28T22:30:00Z"), TODAY));
            TimeZone.setDefault(TimeZone.getTimeZone("UTC"));
            assertEquals(LocalDate.of(2026, 3, 1), documentDate(SAST, Instant.parse("2026-02-28T22:30:00Z"), TODAY));
        } finally { TimeZone.setDefault(before); }
    }
}
