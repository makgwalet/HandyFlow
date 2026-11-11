package za.co.handyflow.platform.events.application.internal;

import org.junit.jupiter.api.Test;
import za.co.handyflow.platform.events.application.internal.SessionLogic.RunItem;
import za.co.handyflow.platform.events.application.internal.SessionLogic.RunState;
import za.co.handyflow.platform.events.application.internal.SessionLogic.Slot;

import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

/** Pins the programme's scheduling rules: what counts as a clash, and where the day has got to. */
class SessionLogicTest {

    private static final LocalDateTime DAY = LocalDateTime.of(2026, 11, 5, 0, 0);

    private static LocalDateTime t(int hour, int minute) {
        return DAY.withHour(hour).withMinute(minute);
    }

    private static Slot slot(String title, String room, String speaker, int sh, int sm, int eh, int em) {
        return new Slot(UUID.randomUUID(), title, room, speaker, t(sh, sm), t(eh, em));
    }

    private static RunItem run(String status, int sh, int sm, int eh, int em) {
        return new RunItem(UUID.randomUUID(), status, t(sh, sm), t(eh, em));
    }

    // ── Overlap ───────────────────────────────────────────────────────────────

    @Test
    void backToBackSessions_doNotOverlap() {
        assertThat(SessionLogic.overlaps(t(9, 0), t(10, 0), t(10, 0), t(11, 0))).isFalse();
        assertThat(SessionLogic.overlaps(t(10, 0), t(11, 0), t(9, 0), t(10, 0))).isFalse();
    }

    @Test
    void partialContainedAndIdenticalRanges_overlap() {
        assertThat(SessionLogic.overlaps(t(9, 0), t(10, 0), t(9, 30), t(10, 30))).isTrue();   // partial
        assertThat(SessionLogic.overlaps(t(9, 0), t(12, 0), t(10, 0), t(11, 0))).isTrue();    // one inside the other
        assertThat(SessionLogic.overlaps(t(9, 0), t(10, 0), t(9, 0), t(10, 0))).isTrue();     // identical
    }

    @Test
    void separateRanges_doNotOverlap() {
        assertThat(SessionLogic.overlaps(t(9, 0), t(10, 0), t(13, 0), t(14, 0))).isFalse();
    }

    // ── Conflicts ─────────────────────────────────────────────────────────────

    @Test
    void sameRoomAtTheSameTime_isAConflict_andTheMessageNamesTheClash() {
        Slot existing = slot("Keynote", "Hall A", null, 9, 0, 10, 0);
        Slot candidate = slot("Panel", "Hall A", null, 9, 30, 10, 30);

        List<String> found = SessionLogic.conflicts(candidate, List.of(existing));

        assertThat(found).hasSize(1);
        assertThat(found.get(0)).contains("Hall A").contains("Keynote").contains("09:00").contains("10:00");
    }

    @Test
    void differentRooms_doNotConflict() {
        Slot existing = slot("Keynote", "Hall A", null, 9, 0, 10, 0);
        Slot candidate = slot("Workshop", "Hall B", null, 9, 0, 10, 0);

        assertThat(SessionLogic.conflicts(candidate, List.of(existing))).isEmpty();
    }

    @Test
    void roomComparison_ignoresCaseAndSurroundingSpaces() {
        Slot existing = slot("Keynote", "Hall A", null, 9, 0, 10, 0);
        Slot candidate = slot("Panel", "  hall a ", null, 9, 30, 10, 30);

        assertThat(SessionLogic.conflicts(candidate, List.of(existing))).hasSize(1);
    }

    @Test
    void sessionsWithNoRoom_neverConflictOnRoom() {
        Slot existing = slot("Registration", null, null, 8, 0, 9, 0);
        Slot candidate = slot("Welcome coffee", "", null, 8, 0, 9, 0);

        assertThat(SessionLogic.conflicts(candidate, List.of(existing))).isEmpty();
    }

    @Test
    void theSameSpeakerInTwoPlacesAtOnce_isAConflict_evenInDifferentRooms() {
        Slot existing = slot("Keynote", "Hall A", "Dr Nkosi", 9, 0, 10, 0);
        Slot candidate = slot("Workshop", "Hall B", "dr nkosi", 9, 30, 10, 30);

        List<String> found = SessionLogic.conflicts(candidate, List.of(existing));

        assertThat(found).hasSize(1);
        assertThat(found.get(0)).contains("Dr Nkosi").contains("Keynote");
    }

    @Test
    void sameRoomAndSameSpeaker_reportsBoth() {
        Slot existing = slot("Keynote", "Hall A", "Dr Nkosi", 9, 0, 10, 0);
        Slot candidate = slot("Q&A", "Hall A", "Dr Nkosi", 9, 30, 10, 30);

        assertThat(SessionLogic.conflicts(candidate, List.of(existing))).hasSize(2);
    }

    @Test
    void backToBackInTheSameRoom_isFine() {
        Slot existing = slot("Keynote", "Hall A", "Dr Nkosi", 9, 0, 10, 0);
        Slot candidate = slot("Panel", "Hall A", "Dr Nkosi", 10, 0, 11, 0);

        assertThat(SessionLogic.conflicts(candidate, List.of(existing))).isEmpty();
    }

    @Test
    void aSession_neverConflictsWithItself_soAnEditKeepsItsOwnSlot() {
        UUID id = UUID.randomUUID();
        Slot stored = new Slot(id, "Keynote", "Hall A", "Dr Nkosi", t(9, 0), t(10, 0));
        Slot edited = new Slot(id, "Keynote (extended)", "Hall A", "Dr Nkosi", t(9, 0), t(10, 30));

        assertThat(SessionLogic.conflicts(edited, List.of(stored))).isEmpty();
    }

    // ── Event window ──────────────────────────────────────────────────────────

    @Test
    void withinWindow_includesExactBoundaries_butNotAnythingOutside() {
        LocalDateTime start = t(8, 0), end = t(18, 0);
        assertThat(SessionLogic.withinWindow(t(8, 0), t(9, 0), start, end)).isTrue();
        assertThat(SessionLogic.withinWindow(t(17, 0), t(18, 0), start, end)).isTrue();
        assertThat(SessionLogic.withinWindow(t(7, 59), t(9, 0), start, end)).isFalse();
        assertThat(SessionLogic.withinWindow(t(17, 0), t(18, 1), start, end)).isFalse();
    }

    // ── Run state ─────────────────────────────────────────────────────────────

    @Test
    void anEmptyProgramme_hasNothingCurrentNothingNextAndIsNotLate() {
        RunState s = SessionLogic.runState(List.of(), t(10, 0));
        assertThat(s.currentId()).isNull();
        assertThat(s.nextId()).isNull();
        assertThat(s.runningLateMinutes()).isZero();
    }

    @Test
    void aSessionRunningWithinItsPlannedTime_meansOnTime() {
        RunItem current = run("IN_PROGRESS", 9, 0, 10, 0);
        RunItem next = run("SCHEDULED", 10, 0, 11, 0);

        RunState s = SessionLogic.runState(List.of(current, next), t(9, 30));

        assertThat(s.currentId()).isEqualTo(current.id());
        assertThat(s.nextId()).isEqualTo(next.id());
        assertThat(s.runningLateMinutes()).isZero();
    }

    @Test
    void aSessionOverrunningItsPlannedEnd_isLateByTheOverrun() {
        RunItem current = run("IN_PROGRESS", 9, 0, 10, 0);
        RunItem next = run("SCHEDULED", 10, 0, 11, 0);

        assertThat(SessionLogic.runState(List.of(current, next), t(10, 25)).runningLateMinutes()).isEqualTo(25);
    }

    @Test
    void nothingRunning_andTheNextSessionShouldHaveStarted_isLateByHowLongItHasWaited() {
        RunItem next = run("SCHEDULED", 10, 0, 11, 0);

        assertThat(SessionLogic.runState(List.of(next), t(10, 7)).runningLateMinutes()).isEqualTo(7);
        assertThat(SessionLogic.runState(List.of(next), t(9, 50)).runningLateMinutes()).isZero();   // not due yet
    }

    @Test
    void completedAndCancelledSessions_areNeitherCurrentNorNext() {
        RunItem done = run("COMPLETED", 8, 0, 9, 0);
        RunItem cancelled = run("CANCELLED", 9, 0, 10, 0);
        RunItem later = run("SCHEDULED", 11, 0, 12, 0);

        RunState s = SessionLogic.runState(List.of(done, cancelled, later), t(10, 0));

        assertThat(s.currentId()).isNull();
        assertThat(s.nextId()).isEqualTo(later.id());
    }

    @Test
    void next_isTheEarliestScheduledSession_regardlessOfListOrder() {
        RunItem lunch = run("SCHEDULED", 13, 0, 14, 0);
        RunItem morning = run("SCHEDULED", 10, 0, 11, 0);

        assertThat(SessionLogic.runState(List.of(lunch, morning), t(9, 0)).nextId()).isEqualTo(morning.id());
    }
}
