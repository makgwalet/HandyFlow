package za.co.handyflow.platform.earthmoving;

import org.junit.jupiter.api.Test;
import za.co.handyflow.platform.earthmoving.domain.model.UtilisationRules;
import za.co.handyflow.platform.earthmoving.domain.model.UtilisationRules.Shift;

import java.math.BigDecimal;
import java.time.*;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

class UtilisationRulesTest {
    static final ZoneId Z = ZoneId.of("Africa/Johannesburg");
    static final LocalDate TODAY = LocalDate.of(2026, 10, 15);
    static BigDecimal n(String s) { return new BigDecimal(s); }
    static Instant at(LocalDate d, int hour) { return d.atTime(hour, 0).atZone(Z).toInstant(); }

    @Test void oneEntryPerDayOldestFirstWithZerosBetween() {
        List<BigDecimal> r = UtilisationRules.perDay(List.of(
                new Shift(at(TODAY, 7), n("8")), new Shift(at(TODAY, 13), n("2.5")),
                new Shift(at(TODAY.minusDays(6), 6), n("9"))), TODAY, 7, Z);
        assertEquals(7, r.size());
        assertEquals(n("9.0"), r.get(0));
        assertEquals(n("0.0"), r.get(3));
        assertEquals(n("10.5"), r.get(6));
    }
    @Test void shiftsOutsideTheWindowAndEmptyValuesAreIgnored() {
        List<BigDecimal> r = UtilisationRules.perDay(java.util.Arrays.asList(
                new Shift(at(TODAY.minusDays(7), 6), n("9")), new Shift(at(TODAY.plusDays(1), 6), n("9")),
                new Shift(at(TODAY, 6), null), null), TODAY, 7, Z);
        assertTrue(r.stream().allMatch(b -> b.signum() == 0));
    }
    @Test void aShiftCountsOnTheDayItStartedInSouthAfricanTime() {
        Instant lateNight = TODAY.minusDays(1).atTime(23, 30).atZone(Z).toInstant();
        List<BigDecimal> r = UtilisationRules.perDay(List.of(new Shift(lateNight, n("5"))), TODAY, 7, Z);
        assertEquals(n("5.0"), r.get(5));
        assertEquals(n("0.0"), r.get(6));
    }
    @Test void theNumberOfDaysIsLimited() {
        assertEquals(30, UtilisationRules.checkDays(30));
        assertThrows(IllegalArgumentException.class, () -> UtilisationRules.checkDays(6));
        assertThrows(IllegalArgumentException.class, () -> UtilisationRules.checkDays(91));
    }
}
