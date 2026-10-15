package za.co.handyflow.platform.earthmoving;

import org.junit.jupiter.api.Test;
import za.co.handyflow.platform.earthmoving.domain.model.DowntimeRules;
import za.co.handyflow.platform.earthmoving.domain.model.DowntimeRules.Change;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

class DowntimeRulesTest {
    static final Instant FROM = Instant.parse("2026-10-01T00:00:00Z"), TO = Instant.parse("2026-11-01T00:00:00Z"), NOW = Instant.parse("2026-12-01T00:00:00Z");
    static Instant h(int hoursAfterFrom) { return FROM.plusSeconds(hoursAfterFrom * 3600L); }

    @Test void breakdownAndMaintenanceAreKeptApart() {
        DowntimeRules.Result r = DowntimeRules.compute(List.of(new Change(h(10), "BREAKDOWN"), new Change(h(34), "MAINTENANCE"),
                new Change(h(40), "AVAILABLE")), FROM, TO, NOW);
        assertEquals(24 * 3600, r.breakdownSeconds());
        assertEquals(6 * 3600, r.maintenanceSeconds());
        assertEquals(1, r.breakdowns());
    }
    @Test void aMachineAlreadyDownAtTheStartCountsFromTheStart() {
        DowntimeRules.Result r = DowntimeRules.compute(List.of(new Change(FROM.minusSeconds(5 * 86400), "BREAKDOWN"), new Change(h(12), "AVAILABLE")), FROM, TO, NOW);
        assertEquals(12 * 3600, r.breakdownSeconds());
        assertEquals(0, r.breakdowns());
    }
    @Test void aMachineStillDownRunsToTheEndOfThePeriod() {
        DowntimeRules.Result r = DowntimeRules.compute(List.of(new Change(TO.minusSeconds(3600), "BREAKDOWN")), FROM, TO, NOW);
        assertEquals(3600, r.breakdownSeconds());
    }
    @Test void aPeriodThatHasNotFinishedOnlyCountsUpToNow() {
        Instant now = h(20);
        DowntimeRules.Result r = DowntimeRules.compute(List.of(new Change(h(10), "BREAKDOWN")), FROM, TO, now);
        assertEquals(10 * 3600, r.breakdownSeconds());
    }
    @Test void changesAfterThePeriodAndNoChangesAtAllCountForNothing() {
        assertEquals(0, DowntimeRules.compute(List.of(new Change(TO.plusSeconds(60), "BREAKDOWN")), FROM, TO, NOW).totalSeconds());
        assertEquals(0, DowntimeRules.compute(List.of(), FROM, TO, NOW).totalSeconds());
    }
    @Test void availabilityIsTheShareOfTheTimeInService() {
        assertEquals(0, new BigDecimal("75.0").compareTo(DowntimeRules.availabilityPercent(10 * 86400L, FROM, FROM.plusSeconds(40 * 86400L), NOW)));
        assertEquals(0, new BigDecimal("100.0").compareTo(DowntimeRules.availabilityPercent(0, FROM, TO, NOW)));
        assertNull(DowntimeRules.availabilityPercent(0, FROM, TO, FROM.minusSeconds(1)));
    }
}
