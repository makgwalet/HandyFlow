package za.co.handyflow.platform.payrollbureau.domain.rules;

import org.junit.jupiter.api.Test;

import java.time.LocalDate;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;

class DeadlineRulesTest {

    private static final Set<LocalDate> NONE = Set.of();

    @Test
    void aWeekdayStaysWhereItIs() {
        assertEquals(LocalDate.of(2026, 12, 7), DeadlineRules.lastBusinessDayOnOrBefore(LocalDate.of(2026, 12, 7), NONE::contains));
    }

    @Test
    void aSundayGoesBackToFriday() {
        assertEquals(LocalDate.of(2027, 3, 5), DeadlineRules.lastBusinessDayOnOrBefore(LocalDate.of(2027, 3, 7), NONE::contains));
        assertEquals(LocalDate.of(2027, 10, 29), DeadlineRules.lastBusinessDayOnOrBefore(LocalDate.of(2027, 10, 31), NONE::contains));
    }

    @Test
    void aHolidayChainGoesBackPastEveryHolidayAndWeekend() {
        Set<LocalDate> holidays = Set.of(LocalDate.of(2026, 4, 6), LocalDate.of(2026, 4, 3));
        assertEquals(LocalDate.of(2026, 4, 2), DeadlineRules.lastBusinessDayOnOrBefore(LocalDate.of(2026, 4, 6), holidays::contains));
    }
}
