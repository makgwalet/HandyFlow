package za.co.handyflow.platform.internalaudit.application.internal;

import org.junit.jupiter.api.Test;
import za.co.handyflow.platform.internalaudit.application.internal.MyTasks.*;

import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;

class MyTasksTest {
    static final UUID ME = UUID.randomUUID(), OTHER = UUID.randomUUID(), ENG = UUID.randomUUID();
    static final LocalDate TODAY = LocalDate.of(2026, 10, 28);

    static ActionIn action(UUID owner, String status, LocalDate due, String text) { return new ActionIn(UUID.randomUUID(), ENG, "Payroll audit", "Late approvals", text, owner, due, status); }
    static FindingIn finding(UUID owner, String status, LocalDate due, String title) { return new FindingIn(UUID.randomUUID(), ENG, "Payroll audit", title, "HIGH", owner, due, status); }
    static NoteIn note(String status, UUID raisedBy, UUID preparedBy, String text) { return new NoteIn(UUID.randomUUID(), ENG, "Payroll audit", "Wage test.xlsx", text, raisedBy, preparedBy, status); }

    @Test void onlyMyOpenWorkIsListed() {
        Result r = MyTasks.build(ME,
                List.of(action(ME, "OPEN", TODAY.plusDays(3), "mine"), action(OTHER, "OPEN", TODAY, "theirs"), action(ME, "VALIDATED", TODAY, "done"), action(ME, "COMPLETED", TODAY, "awaiting check")),
                List.of(finding(ME, "IN_PROGRESS", null, "f mine"), finding(ME, "CLOSED", null, "f closed"), finding(OTHER, "OPEN", null, "f theirs")),
                List.of(), TODAY);
        assertEquals(List.of("mine", "f mine"), r.tasks().stream().map(Task::title).toList());
    }

    @Test void notesGoToThePreparerToAnswerAndTheReviewerToClear() {
        List<NoteIn> notes = List.of(
                note("OPEN", OTHER, ME, "answer me"), note("OPEN", ME, OTHER, "not mine to answer"),
                note("RESPONDED", ME, OTHER, "clear me"), note("RESPONDED", OTHER, ME, "not mine to clear"), note("CLEARED", ME, ME, "finished"));
        Result r = MyTasks.build(ME, List.of(), List.of(), notes, TODAY);
        assertEquals(List.of(Kind.NOTE_ANSWER, Kind.NOTE_CLEAR), r.tasks().stream().map(Task::kind).toList());
        assertEquals("Wage test.xlsx · Payroll audit", r.tasks().get(0).context());
    }

    @Test void lateFirstThenSoonestThenUndated() {
        Result r = MyTasks.build(ME,
                List.of(action(ME, "OPEN", TODAY.plusDays(2), "soon"), action(ME, "OPEN", TODAY.minusDays(2), "late 2"), action(ME, "OPEN", TODAY.minusDays(9), "late 9")),
                List.of(finding(ME, "OPEN", null, "undated")), List.of(), TODAY);
        assertEquals(List.of("late 9", "late 2", "soon", "undated"), r.tasks().stream().map(Task::title).toList());
        assertEquals(9, r.tasks().get(0).daysOverdue());
        assertTrue(r.tasks().get(1).overdue());
        assertFalse(r.tasks().get(2).overdue());
    }

    @Test void countsOverdueAndDueWithinAWeek() {
        Result r = MyTasks.build(ME,
                List.of(action(ME, "OPEN", TODAY.minusDays(1), "a"), action(ME, "OPEN", TODAY, "b"), action(ME, "OPEN", TODAY.plusDays(7), "c"), action(ME, "OPEN", TODAY.plusDays(8), "d")),
                List.of(), List.of(), TODAY);
        assertEquals(new Summary(4, 1, 2), r.summary());
    }

    @Test void noUserMeansNoTasks() {
        Result r = MyTasks.build(null, List.of(action(null, "OPEN", TODAY, "orphan")), List.of(), List.of(), TODAY);
        assertEquals(0, r.summary().total());
    }
}
