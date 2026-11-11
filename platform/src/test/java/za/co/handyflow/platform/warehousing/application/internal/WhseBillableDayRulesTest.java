package za.co.handyflow.platform.warehousing.application.internal;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import za.co.handyflow.platform.warehousing.application.internal.WhseBillableDayRules.DayRule;
import za.co.handyflow.platform.warehousing.application.internal.WhseBillableDayRules.RuleVersion;

import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import java.util.Set;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;

class WhseBillableDayRulesTest {

    private final UUID a = UUID.randomUUID(), b = UUID.randomUUID();
    private static final LocalDate FRI = LocalDate.of(2026, 11, 13), SAT = FRI.plusDays(1), SUN = FRI.plusDays(2), MON = FRI.plusDays(3);

    private static RuleVersion v(UUID client, DayRule rule, LocalDate from, long created) {
        return new RuleVersion(client, rule, from, Instant.ofEpochSecond(created));
    }

    @Test @DisplayName("with no rule every day bills: Friday to Monday is four billable days")
    void standard() {
        DayRule r = WhseBillableDayRules.ruleFor(List.of(), a, FRI);
        assertEquals(DayRule.ALL_DAYS, r);
        long days = java.util.stream.Stream.of(FRI, SAT, SUN, MON).filter(d -> WhseBillableDayRules.isBillable(r, d, Set.of())).count();
        assertEquals(4, days);
    }

    @Test @DisplayName("weekdays only skips Saturday and Sunday: Friday to Monday is two billable days")
    void weekdays() {
        assertTrue(WhseBillableDayRules.isBillable(DayRule.WEEKDAYS_ONLY, FRI, Set.of()));
        assertFalse(WhseBillableDayRules.isBillable(DayRule.WEEKDAYS_ONLY, SAT, Set.of()));
        assertFalse(WhseBillableDayRules.isBillable(DayRule.WEEKDAYS_ONLY, SUN, Set.of()));
        assertTrue(WhseBillableDayRules.isBillable(DayRule.WEEKDAYS_ONLY, MON, Set.of()));
    }

    @Test @DisplayName("a holiday on a weekday is skipped only by the no-holidays rule")
    void holidays() {
        Set<LocalDate> hol = Set.of(MON);
        assertTrue(WhseBillableDayRules.isBillable(DayRule.WEEKDAYS_ONLY, MON, hol));
        assertFalse(WhseBillableDayRules.isBillable(DayRule.WEEKDAYS_NO_HOLIDAYS, MON, hol));
        assertTrue(WhseBillableDayRules.isBillable(DayRule.ALL_DAYS, MON, hol));
    }

    @Test @DisplayName("the client's own rule beats the warehouse default, which beats the standard")
    void precedence() {
        List<RuleVersion> vs = List.of(v(null, DayRule.WEEKDAYS_ONLY, FRI.minusDays(30), 1), v(a, DayRule.ALL_DAYS, FRI.minusDays(10), 2));
        assertEquals(DayRule.ALL_DAYS, WhseBillableDayRules.ruleFor(vs, a, FRI));
        assertEquals(DayRule.WEEKDAYS_ONLY, WhseBillableDayRules.ruleFor(vs, b, FRI));
    }

    @Test @DisplayName("a rule applies from its start date, the newest started version wins, and a tie goes to the later created")
    void versions() {
        List<RuleVersion> vs = List.of(v(a, DayRule.WEEKDAYS_ONLY, FRI, 1), v(a, DayRule.WEEKDAYS_NO_HOLIDAYS, FRI, 2), v(a, DayRule.ALL_DAYS, MON, 3));
        assertEquals(DayRule.ALL_DAYS, WhseBillableDayRules.ruleFor(vs, a, FRI.minusDays(1)));      // none started yet: standard
        assertEquals(DayRule.WEEKDAYS_NO_HOLIDAYS, WhseBillableDayRules.ruleFor(vs, a, SAT));       // same start date: later created
        assertEquals(DayRule.ALL_DAYS, WhseBillableDayRules.ruleFor(vs, a, MON));                   // newer version started
    }

    @Test @DisplayName("a rule cannot start in the past")
    void effective() {
        assertThrows(IllegalArgumentException.class, () -> WhseBillableDayRules.checkEffectiveFrom(FRI.minusDays(1), FRI));
        assertThrows(IllegalArgumentException.class, () -> WhseBillableDayRules.checkEffectiveFrom(null, FRI));
        assertDoesNotThrow(() -> WhseBillableDayRules.checkEffectiveFrom(FRI, FRI));
    }

    @Test @DisplayName("parse")
    void parse() {
        assertEquals(DayRule.WEEKDAYS_ONLY, WhseBillableDayRules.parse(" weekdays_only "));
        assertThrows(IllegalArgumentException.class, () -> WhseBillableDayRules.parse("sundays"));
        assertThrows(IllegalArgumentException.class, () -> WhseBillableDayRules.parse(null));
    }

    @Test @DisplayName("the minimum is pro-rated over every calendar day unless the rule says billable days")
    void minimumDays() {
        LocalDate after = FRI.minusDays(1), through = MON; // Fri, Sat, Sun, Mon
        assertEquals(4, WhseBillableDayRules.minimumDays(List.of(), a, after, through, Set.of()));
        List<RuleVersion> calendar = List.of(new RuleVersion(a, DayRule.WEEKDAYS_ONLY, FRI.minusDays(30), Instant.EPOCH, WhseBillableDayRules.MinimumBasis.CALENDAR_DAYS));
        assertEquals(4, WhseBillableDayRules.minimumDays(calendar, a, after, through, Set.of()));
        List<RuleVersion> billable = List.of(new RuleVersion(a, DayRule.WEEKDAYS_ONLY, FRI.minusDays(30), Instant.EPOCH, WhseBillableDayRules.MinimumBasis.BILLABLE_DAYS));
        assertEquals(2, WhseBillableDayRules.minimumDays(billable, a, after, through, Set.of()));
        assertEquals(4, WhseBillableDayRules.minimumDays(billable, b, after, through, Set.of())); // another client keeps the standard
        List<RuleVersion> starts = List.of(new RuleVersion(a, DayRule.WEEKDAYS_ONLY, SAT, Instant.EPOCH, WhseBillableDayRules.MinimumBasis.BILLABLE_DAYS));
        assertEquals(2, WhseBillableDayRules.minimumDays(starts, a, after, through, Set.of())); // Fri counts (rule not started), Sat and Sun do not, Mon does
    }

    @Test @DisplayName("minimum basis parses, and blank means the standard")
    void basis() {
        assertEquals(WhseBillableDayRules.MinimumBasis.CALENDAR_DAYS, WhseBillableDayRules.parseBasis(null));
        assertEquals(WhseBillableDayRules.MinimumBasis.BILLABLE_DAYS, WhseBillableDayRules.parseBasis(" billable_days "));
        assertThrows(IllegalArgumentException.class, () -> WhseBillableDayRules.parseBasis("weekly"));
    }
}
