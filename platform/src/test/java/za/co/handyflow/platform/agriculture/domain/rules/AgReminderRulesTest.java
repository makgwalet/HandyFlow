package za.co.handyflow.platform.agriculture.domain.rules;

import org.junit.jupiter.api.Test;

import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneOffset;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

class AgReminderRulesTest {

    private static final LocalDate TODAY = LocalDate.of(2026, 11, 23);

    @Test void aReminderIsNotSentBeforeItIsDue() {
        assertFalse(AgReminderRules.shouldNotifyDue(TODAY.plusDays(1), false, null, TODAY));
        assertFalse(AgReminderRules.shouldNotifyDue(null, false, null, TODAY));
    }

    @Test void aDueReminderIsSentTheFirstTime() {
        assertTrue(AgReminderRules.shouldNotifyDue(TODAY, false, null, TODAY));
        assertTrue(AgReminderRules.shouldNotifyDue(TODAY.minusDays(30), false, null, TODAY));
    }

    @Test void aReminderAPersonHasDealtWithIsNeverSent() {
        assertFalse(AgReminderRules.shouldNotifyDue(TODAY.minusDays(3), true, null, TODAY));
        assertFalse(AgReminderRules.shouldNotifyDue(TODAY.minusDays(30), true, TODAY.minusDays(20), TODAY));
    }

    @Test void anOpenReminderIsRepeatedAWeekLaterNotEveryDay() {
        assertFalse(AgReminderRules.shouldNotifyDue(TODAY.minusDays(5), false, TODAY, TODAY), "told today");
        assertFalse(AgReminderRules.shouldNotifyDue(TODAY.minusDays(9), false, TODAY.minusDays(1), TODAY));
        assertFalse(AgReminderRules.shouldNotifyDue(TODAY.minusDays(9), false, TODAY.minusDays(6), TODAY));
        assertTrue(AgReminderRules.shouldNotifyDue(TODAY.minusDays(9), false, TODAY.minusDays(7), TODAY));
        assertTrue(AgReminderRules.shouldNotifyDue(TODAY.minusDays(30), false, TODAY.minusDays(40), TODAY));
    }

    @Test void theQueryCutOffMatchesTheRepeatRule() {
        LocalDate cutOff = AgReminderRules.repeatBefore(TODAY);
        assertEquals(TODAY.minusDays(7), cutOff);
        assertTrue(AgReminderRules.isNoticeDue(cutOff, TODAY));
        assertFalse(AgReminderRules.isNoticeDue(cutOff.plusDays(1), TODAY));
        assertTrue(AgReminderRules.isNoticeDue(null, TODAY));
    }

    @Test void theManagerIsToldInsteadOfTheAdminsNotAsWellAsThem() {
        assertEquals(List.of("manager"), AgReminderRules.managerOrFallback(List.of("manager"), List.of("admin1", "admin2")));
        assertEquals(List.of("admin1", "admin2"), AgReminderRules.managerOrFallback(List.of(), List.of("admin1", "admin2")));
        assertEquals(List.of("admin1"), AgReminderRules.managerOrFallback(null, List.of("admin1")));
        assertEquals(List.of(), AgReminderRules.managerOrFallback(List.of(), null));
    }

    @Test void messagesNameTheAnimalAndGroupNotADatabaseId() {
        assertEquals("animal tag AN-001 (Bessie)", AgReminderRules.animalLabel("AN-001", "Bessie"));
        assertEquals("animal tag AN-001", AgReminderRules.animalLabel(" AN-001 ", " "));
        assertEquals("animal tag (no tag)", AgReminderRules.animalLabel(null, null));
        assertEquals("group batch B-12", AgReminderRules.groupLabel(" B-12 "));
        assertEquals("group batch (no number)", AgReminderRules.groupLabel(""));
    }

    @Test void dueWording() {
        assertEquals("due today", AgReminderRules.dueWording(TODAY, TODAY));
        assertEquals("due today", AgReminderRules.dueWording(TODAY.plusDays(2), TODAY));
        assertEquals("1 day overdue", AgReminderRules.dueWording(TODAY.minusDays(1), TODAY));
        assertEquals("12 days overdue", AgReminderRules.dueWording(TODAY.minusDays(12), TODAY));
    }

    @Test void todayIsTheSouthAfricanDateNotUtc() {
        // 22:30 UTC on the 22nd is 00:30 SAST on the 23rd
        Clock lateUtc = Clock.fixed(Instant.parse("2026-11-22T22:30:00Z"), ZoneOffset.UTC);
        assertEquals(LocalDate.of(2026, 11, 22), LocalDate.now(lateUtc));
        assertEquals(LocalDate.of(2026, 11, 23), AgDates.today(lateUtc));
        Clock noon = Clock.fixed(Instant.parse("2026-11-23T10:00:00Z"), ZoneOffset.UTC);
        assertEquals(LocalDate.of(2026, 11, 23), AgDates.today(noon));
    }
}
