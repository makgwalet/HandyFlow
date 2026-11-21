package za.co.handyflow.platform.security.application.internal;

import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.time.LocalDate;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;

class IssueChecksTest {

    private static final LocalDate TODAY = LocalDate.of(2026, 11, 20);
    private static final UUID G = UUID.randomUUID();
    private static final Instant START = Instant.parse("2026-11-20T18:00:00Z");
    private static final Instant END = Instant.parse("2026-11-21T06:00:00Z");

    @Test void psiraValidPasses() { assertNull(IssueChecks.psira(TODAY, "1234", TODAY.plusDays(30))); }
    @Test void psiraExpiringTodayStillValid() { assertNull(IssueChecks.psira(TODAY, "1234", TODAY)); }
    @Test void psiraExpiredYesterdayBlocks() { assertEquals("PSIRA_EXPIRED", IssueChecks.psira(TODAY, "1234", TODAY.minusDays(1))); }
    @Test void psiraMissingNumberBlocks() { assertEquals("PSIRA_NOT_ON_FILE", IssueChecks.psira(TODAY, " ", TODAY.plusDays(5))); }
    @Test void psiraMissingExpiryBlocks() { assertEquals("PSIRA_NOT_ON_FILE", IssueChecks.psira(TODAY, "1234", null)); }

    @Test void noShiftAllowedByDefault() { assertNull(IssueChecks.shift(false, G, null, null, null, null, START)); }
    @Test void noShiftRefusedWhenRequired() { assertEquals("SHIFT_REQUIRED", IssueChecks.shift(true, G, null, null, null, null, START)); }
    @Test void shiftOfAnotherGuardRefused() {
        assertEquals("SHIFT_NOT_GUARDS", IssueChecks.shift(false, G, UUID.randomUUID(), "SCHEDULED", START, END, START));
    }
    @Test void cancelledAndFinishedShiftsRefused() {
        for (String s : new String[]{"CANCELLED", "MISSED", "COMPLETED", "PULLED"})
            assertEquals("SHIFT_NOT_ACTIVE", IssueChecks.shift(false, G, G, s, START, END, START), s);
    }
    @Test void duringShiftOk() { assertNull(IssueChecks.shift(false, G, G, "ACTIVE", START, END, START.plusSeconds(3600))); }
    @Test void collectingTwoHoursEarlyOk() { assertNull(IssueChecks.shift(false, G, G, "SCHEDULED", START, END, START.minusSeconds(7200))); }
    @Test void collectingTooEarlyRefused() {
        assertEquals("SHIFT_NOT_NOW", IssueChecks.shift(false, G, G, "SCHEDULED", START, END, START.minusSeconds(7201)));
    }
    @Test void afterShiftEndRefused() {
        assertEquals("SHIFT_NOT_NOW", IssueChecks.shift(false, G, G, "ACTIVE", START, END, END.plusSeconds(1)));
    }
}
