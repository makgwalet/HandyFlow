package za.co.handyflow.platform.agriculture.domain.rules;

import org.junit.jupiter.api.Test;

import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;

class AgCalendarRulesTest {

    private static LocalDate d(String s) { return LocalDate.parse(s); }

    @Test void theWindowNeedsBothDatesInOrderAndNoMoreThanSixtyTwoDays() {
        AgCalendarRules.requireWindow(d("2026-11-01"), d("2026-11-01"));
        AgCalendarRules.requireWindow(d("2026-11-01"), d("2026-12-31"));   // 61 days
        AgCalendarRules.requireWindow(d("2026-11-01"), d("2027-01-01"));   // exactly 62 days
        assertThrows(IllegalArgumentException.class, () -> AgCalendarRules.requireWindow(d("2026-11-01"), d("2027-01-02")));
        assertThrows(IllegalArgumentException.class, () -> AgCalendarRules.requireWindow(d("2026-11-02"), d("2026-11-01")));
        assertThrows(IllegalArgumentException.class, () -> AgCalendarRules.requireWindow(null, d("2026-11-01")));
        assertThrows(IllegalArgumentException.class, () -> AgCalendarRules.requireWindow(d("2026-11-01"), null));
    }

    @Test void inWindowIncludesBothEnds() {
        assertTrue(AgCalendarRules.inWindow(d("2026-11-01"), d("2026-11-01"), d("2026-11-07")));
        assertTrue(AgCalendarRules.inWindow(d("2026-11-07"), d("2026-11-01"), d("2026-11-07")));
        assertFalse(AgCalendarRules.inWindow(d("2026-10-31"), d("2026-11-01"), d("2026-11-07")));
        assertFalse(AgCalendarRules.inWindow(d("2026-11-08"), d("2026-11-01"), d("2026-11-07")));
        assertFalse(AgCalendarRules.inWindow(null, d("2026-11-01"), d("2026-11-07")));
    }

    @Test void aWithdrawalEndsOnTheFirstClearDay() {
        // treated 1 Nov with 14 days meat, 3 days milk: clear from 15 Nov (meat) and 4 Nov (milk)
        var t = new AgCalendarRules.Treatment(d("2026-11-01"), false, 14, 3, null);
        var all = AgCalendarRules.withdrawalEnds(t, d("2026-11-01"), d("2026-11-30"));
        assertEquals(2, all.size());
        assertEquals(d("2026-11-15"), all.stream().filter(w -> w.what().equals("meat")).findFirst().get().clearOn());
        assertEquals(d("2026-11-04"), all.stream().filter(w -> w.what().equals("milk")).findFirst().get().clearOn());
    }

    @Test void onlyTheEndsInsideTheWindowAreListed() {
        var t = new AgCalendarRules.Treatment(d("2026-11-01"), false, 14, 3, 7);
        var week = AgCalendarRules.withdrawalEnds(t, d("2026-11-04"), d("2026-11-08"));
        assertEquals(2, week.size());                                   // milk 4 Nov, eggs 8 Nov
        assertEquals(List.of("milk", "eggs"), week.stream().map(AgCalendarRules.WithdrawalEnd::what).toList());
        assertTrue(AgCalendarRules.withdrawalEnds(t, d("2026-12-01"), d("2026-12-07")).isEmpty());
    }

    @Test void aScheduledTreatmentOrNoPeriodWithholdsNothing() {
        assertTrue(AgCalendarRules.withdrawalEnds(new AgCalendarRules.Treatment(d("2026-11-01"), true, 14, 3, 7), d("2026-11-01"), d("2026-11-30")).isEmpty());
        assertTrue(AgCalendarRules.withdrawalEnds(new AgCalendarRules.Treatment(d("2026-11-01"), false, null, 0, -1), d("2026-11-01"), d("2026-11-30")).isEmpty());
        assertTrue(AgCalendarRules.withdrawalEnds(null, d("2026-11-01"), d("2026-11-30")).isEmpty());
    }

    @Test void onlyThingsToDoAreOverdue() {
        LocalDate today = d("2026-11-23");
        assertTrue(AgCalendarRules.isOverdue(AgCalendarRules.HEALTH_DUE, d("2026-11-22"), today));
        assertTrue(AgCalendarRules.isOverdue(AgCalendarRules.HEALTH_SCHEDULED, d("2026-11-01"), today));
        assertTrue(AgCalendarRules.isOverdue(AgCalendarRules.SCOUTING_FOLLOWUP, d("2026-11-22"), today));
        assertFalse(AgCalendarRules.isOverdue(AgCalendarRules.HEALTH_DUE, today, today), "today is not late yet");
        assertFalse(AgCalendarRules.isOverdue(AgCalendarRules.HEALTH_DUE, d("2026-11-24"), today));
        assertFalse(AgCalendarRules.isOverdue(AgCalendarRules.WITHDRAWAL_END, d("2026-11-01"), today));
        assertFalse(AgCalendarRules.isOverdue(AgCalendarRules.BIRTH_DUE, d("2026-11-01"), today));
        assertFalse(AgCalendarRules.isOverdue(AgCalendarRules.HARVEST, d("2026-11-01"), today));
    }

    @Test void itemsComeOutByDayThenKindThenTitle() {
        UUID id = UUID.randomUUID();
        var items = List.of(
                new AgCalendarRules.Item(d("2026-11-05"), AgCalendarRules.HARVEST, "Harvest expected: Maize", null, "CROP_CYCLE", id),
                new AgCalendarRules.Item(d("2026-11-02"), AgCalendarRules.BIRTH_DUE, "Birth expected: B", null, "ANIMAL", id),
                new AgCalendarRules.Item(d("2026-11-02"), AgCalendarRules.HEALTH_DUE, "Vaccination due: Z", null, "ANIMAL", id),
                new AgCalendarRules.Item(d("2026-11-02"), AgCalendarRules.HEALTH_DUE, "dosing due: a", null, "ANIMAL", id));
        var out = AgCalendarRules.sorted(items);
        assertEquals(List.of("dosing due: a", "Vaccination due: Z", "Birth expected: B", "Harvest expected: Maize"), out.stream().map(AgCalendarRules.Item::title).toList());
        assertEquals(4, items.size(), "the input list is left alone");
    }

    @Test void codesBecomeReadableNames() {
        assertEquals("Vaccination", AgCalendarRules.humanize("VACCINATION"));
        assertEquals("Tick control", AgCalendarRules.humanize("tick_control"));
        assertEquals("Treatment", AgCalendarRules.humanize(" "));
        assertEquals("Treatment", AgCalendarRules.humanize(null));
    }
}
