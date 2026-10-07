package za.co.handyflow.platform.clinic.application.internal;

import org.junit.jupiter.api.Test;
import za.co.handyflow.platform.clinic.application.internal.RecallWorklist.Action;
import za.co.handyflow.platform.clinic.dto.RecallPage;
import za.co.handyflow.platform.clinic.dto.RecallResponse;

import java.time.Instant;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;

class RecallWorklistTest {

    static final LocalDate TODAY = LocalDate.of(2026, 10, 8);
    static final Instant T = Instant.parse("2026-10-07T08:00:00Z");

    private static Action act(String type, String outcome, LocalDate until) { return new Action(type, outcome, until, T); }

    private static RecallResponse recall(String name, String phone, UUID doc, int overdue, String status, int attempts) {
        return new RecallResponse(UUID.randomUUID(), UUID.randomUUID(), name, phone, doc, "Dr X", T, 7,
                TODAY.minusDays(overdue), overdue, "Flu", status, null, attempts, null, null);
    }

    @Test void noHistoryIsOpen() {
        var s = RecallWorklist.stateOf(List.of(), TODAY);
        assertEquals("OPEN", s.status());
        assertEquals(0, s.attempts());
    }

    @Test void contactsAreCountedAndDoNotChangeStatus() {
        var s = RecallWorklist.stateOf(List.of(act("CONTACT", "NO_ANSWER", null), act("CONTACT", "REACHED", null)), TODAY);
        assertEquals("OPEN", s.status());
        assertEquals(2, s.attempts());
        assertEquals("NO_ANSWER", s.lastContactOutcome());
    }

    @Test void activeSnoozeHidesUntilItsDateThenReopens() {
        assertEquals("SNOOZED", RecallWorklist.stateOf(List.of(act("SNOOZE", null, TODAY.plusDays(3))), TODAY).status());
        assertEquals("OPEN", RecallWorklist.stateOf(List.of(act("SNOOZE", null, TODAY)), TODAY).status());
    }

    @Test void dismissStaysDismissedUntilReopened() {
        assertEquals("DISMISSED", RecallWorklist.stateOf(List.of(act("CONTACT", "REACHED", null), act("DISMISS", null, null)), TODAY).status());
        assertEquals("OPEN", RecallWorklist.stateOf(List.of(act("REOPEN", null, null), act("DISMISS", null, null)), TODAY).status());
    }

    @Test void validatesEachAction() {
        assertThrows(IllegalArgumentException.class, () -> RecallWorklist.validate("NOPE", null, null, null, TODAY));
        assertThrows(IllegalArgumentException.class, () -> RecallWorklist.validate("CONTACT", null, null, null, TODAY));
        assertThrows(IllegalArgumentException.class, () -> RecallWorklist.validate("CONTACT", "SHRUG", null, null, TODAY));
        assertDoesNotThrow(() -> RecallWorklist.validate("CONTACT", "REACHED", null, null, TODAY));
        assertThrows(IllegalArgumentException.class, () -> RecallWorklist.validate("SNOOZE", null, null, TODAY, TODAY));
        assertThrows(IllegalArgumentException.class, () -> RecallWorklist.validate("SNOOZE", null, null, TODAY.plusDays(181), TODAY));
        assertDoesNotThrow(() -> RecallWorklist.validate("SNOOZE", null, null, TODAY.plusDays(180), TODAY));
        assertThrows(IllegalArgumentException.class, () -> RecallWorklist.validate("DISMISS", null, "  ", null, TODAY));
        assertDoesNotThrow(() -> RecallWorklist.validate("DISMISS", null, "Moved away", null, TODAY));
        assertDoesNotThrow(() -> RecallWorklist.validate("REOPEN", null, null, null, TODAY));
    }

    @Test void filtersByTabSearchAndDoctor() {
        UUID d1 = UUID.randomUUID(), d2 = UUID.randomUUID();
        var a = recall("Ada Lovelace", "0821112222", d1, 5, "OPEN", 0);
        var b = recall("Bheki Nkosi", "0833334444", d2, 0, "OPEN", 2);
        var c = recall("Cara Smith", null, d1, 9, "SNOOZED", 1);
        var d = recall("Dan Jones", null, d1, 9, "DISMISSED", 0);
        var all = List.of(a, b, c, d);
        assertEquals(List.of(a, b), RecallWorklist.filter(all, null, "ALL", null));
        assertEquals(List.of(a), RecallWorklist.filter(all, "", "OVERDUE", null));
        assertEquals(List.of(b), RecallWorklist.filter(all, "", "TODAY", null));
        assertEquals(List.of(a), RecallWorklist.filter(all, "", "NOT_CONTACTED", null));
        assertEquals(List.of(c), RecallWorklist.filter(all, "", "SNOOZED", null));
        assertEquals(List.of(d), RecallWorklist.filter(all, "", "DISMISSED", null));
        assertEquals(List.of(b), RecallWorklist.filter(all, "0833", "ALL", null));
        assertEquals(List.of(a), RecallWorklist.filter(all, " LOVE ", "ALL", null));
        assertEquals(List.of(b), RecallWorklist.filter(all, "", "ALL", d2));
    }

    @Test void countsCoverTheWholeList() {
        var all = List.of(recall("A", null, null, 5, "OPEN", 0), recall("B", null, null, 0, "OPEN", 2),
                recall("C", null, null, 1, "SNOOZED", 0), recall("D", null, null, 1, "DISMISSED", 0));
        var c = RecallWorklist.counts(all);
        assertEquals(new RecallPage.Counts(2, 1, 1, 1, 1), c);
    }

    @Test void pagesAndClampsSize() {
        List<RecallResponse> rows = new ArrayList<>();
        for (int i = 0; i < 30; i++) rows.add(recall("P" + i, null, null, 1, "OPEN", 0));
        var c = RecallWorklist.counts(rows);
        var p1 = RecallWorklist.page(rows, 1, 25, c);
        assertEquals(5, p1.content().size());
        assertEquals(30, p1.total());
        assertEquals(0, RecallWorklist.page(rows, 9, 25, c).content().size());
        assertEquals(30, RecallWorklist.page(rows, 0, 1000, c).content().size());
        assertEquals(1, RecallWorklist.page(rows, 0, 0, c).size());
    }
}
