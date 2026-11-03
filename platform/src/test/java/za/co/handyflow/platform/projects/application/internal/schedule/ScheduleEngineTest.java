package za.co.handyflow.platform.projects.application.internal.schedule;

import org.junit.jupiter.api.Test;
import za.co.handyflow.platform.projects.application.internal.schedule.ScheduleEngine.*;

import java.math.BigDecimal;
import java.time.DayOfWeek;
import java.time.LocalDate;
import java.util.EnumSet;
import java.util.List;
import java.util.Set;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Calendar reference: 2026-11-02 is a Monday. Working days are Mon–Fri unless stated.
 *   Nov 2 Mon · 3 Tue · 4 Wed · 5 Thu · 6 Fri · (7,8 weekend) · 9 Mon · 10 Tue · 11 Wed · 12 Thu · 13 Fri
 */
class ScheduleEngineTest {

    static final SimpleWorkingCalendar CAL = SimpleWorkingCalendar.weekdays();

    static LocalDate d(String s) { return LocalDate.parse(s); }

    static Node node(UUID id, String start, String end) {
        return new Node(id, d(start), d(end), null, null, "NOT_STARTED", BigDecimal.ZERO);
    }
    static Dep dep(UUID a, UUID b, String type, int lag) { return new Dep(UUID.randomUUID(), a, b, type, lag); }
    static Dep fs(UUID a, UUID b) { return dep(a, b, "FS", 0); }

    UUID a = UUID.randomUUID(), b = UUID.randomUUID(), c = UUID.randomUUID();

    @Test
    void finishToStartChain_isAllCritical() {
        Forecast f = ScheduleEngine.forecast(
                List.of(node(a, "2026-11-02", "2026-11-04"), node(b, "2026-11-05", "2026-11-06")),
                List.of(fs(a, b)), CAL, d("2026-11-02"));

        assertEquals(d("2026-11-06"), f.finish());
        assertEquals(d("2026-11-04"), f.results().get(a).end());
        assertEquals(d("2026-11-05"), f.results().get(b).start());
        assertTrue(f.results().get(a).critical());
        assertTrue(f.results().get(b).critical());
    }

    @Test
    void lateStart_slipsEveryDependent() {
        // Nothing has started and today is Mon Nov 9 — the whole chain must move out.
        Forecast f = ScheduleEngine.forecast(
                List.of(node(a, "2026-11-02", "2026-11-04"), node(b, "2026-11-05", "2026-11-06")),
                List.of(fs(a, b)), CAL, d("2026-11-09"));

        assertEquals(d("2026-11-09"), f.results().get(a).start());
        assertEquals(d("2026-11-11"), f.results().get(a).end());
        assertEquals(d("2026-11-12"), f.results().get(b).start());
        assertEquals(d("2026-11-13"), f.finish());
    }

    @Test
    void parallelBranch_hasFloat_andIsNotCritical() {
        // A (3d) and B (2d) both feed C. B finishes a day earlier, so it has 1 day of float.
        Forecast f = ScheduleEngine.forecast(
                List.of(node(a, "2026-11-02", "2026-11-04"), node(b, "2026-11-02", "2026-11-03"),
                        node(c, "2026-11-05", "2026-11-06")),
                List.of(fs(a, c), fs(b, c)), CAL, d("2026-11-02"));

        assertEquals(0, f.results().get(a).floatDays());
        assertEquals(1, f.results().get(b).floatDays());
        assertFalse(f.results().get(b).critical());
        assertTrue(f.results().get(a).critical());
        assertTrue(f.results().get(c).critical());
    }

    @Test
    void weekendsAreSkipped() {
        // Fri Nov 6 start, 3 working days (Fri, Mon, Tue) → ends Tue Nov 10.
        Forecast f = ScheduleEngine.forecast(
                List.of(node(a, "2026-11-06", "2026-11-10")), List.of(), CAL, d("2026-11-02"));
        assertEquals(d("2026-11-06"), f.results().get(a).start());
        assertEquals(d("2026-11-10"), f.results().get(a).end());
    }

    @Test
    void publicHolidaysAreSkipped() {
        Set<DayOfWeek> weekdays = EnumSet.of(DayOfWeek.MONDAY, DayOfWeek.TUESDAY, DayOfWeek.WEDNESDAY,
                DayOfWeek.THURSDAY, DayOfWeek.FRIDAY);
        SimpleWorkingCalendar withHoliday = new SimpleWorkingCalendar(weekdays, Set.of(d("2026-11-04")));
        // Today is the holiday (Wed Nov 4): work starts Thu Nov 5; 3 working days → Thu, Fri, Mon.
        Forecast f = ScheduleEngine.forecast(
                List.of(node(a, "2026-11-03", "2026-11-06")), List.of(), withHoliday, d("2026-11-04"));
        assertEquals(d("2026-11-05"), f.results().get(a).start());
        assertEquals(d("2026-11-09"), f.results().get(a).end());
    }

    @Test
    void startToStartWithLag() {
        // B may start 2 working days after A starts: Wed Nov 4.
        Forecast f = ScheduleEngine.forecast(
                List.of(node(a, "2026-11-02", "2026-11-06"), node(b, "2026-11-02", "2026-11-04")),
                List.of(dep(a, b, "SS", 2)), CAL, d("2026-11-02"));
        assertEquals(d("2026-11-04"), f.results().get(b).start());
        assertEquals(d("2026-11-06"), f.results().get(b).end());
    }

    @Test
    void finishToFinish_pullsSuccessorFinishOut() {
        // B (2d) cannot finish before A finishes on Fri Nov 6 → runs Thu–Fri.
        Forecast f = ScheduleEngine.forecast(
                List.of(node(a, "2026-11-02", "2026-11-06"), node(b, "2026-11-02", "2026-11-03")),
                List.of(dep(a, b, "FF", 0)), CAL, d("2026-11-02"));
        assertEquals(d("2026-11-05"), f.results().get(b).start());
        assertEquals(d("2026-11-06"), f.results().get(b).end());
    }

    @Test
    void completedTask_isFixed_withNoFloat_andSuccessorStillWaitsForToday() {
        Node done = new Node(a, d("2026-11-02"), d("2026-11-04"), d("2026-11-02"), d("2026-11-04"),
                "COMPLETED", new BigDecimal("100"));
        Forecast f = ScheduleEngine.forecast(
                List.of(done, node(b, "2026-11-05", "2026-11-06")),
                List.of(fs(a, b)), CAL, d("2026-11-09"));

        NodeResult ra = f.results().get(a);
        assertTrue(ra.fixed());
        assertNull(ra.floatDays());
        assertFalse(ra.critical());
        assertEquals(d("2026-11-09"), f.results().get(b).start());   // can't start in the past
    }

    @Test
    void inProgressTask_remainingWorkRunsFromToday() {
        // 5-day task, 60% done, today Wed Nov 4 → 2 days left → finishes Thu Nov 5.
        Node wip = new Node(a, d("2026-11-02"), d("2026-11-06"), d("2026-11-02"), null,
                "IN_PROGRESS", new BigDecimal("60"));
        Forecast f = ScheduleEngine.forecast(List.of(wip), List.of(), CAL, d("2026-11-04"));
        assertEquals(d("2026-11-02"), f.results().get(a).start());
        assertEquals(d("2026-11-05"), f.results().get(a).end());
    }

    @Test
    void cancelledAndUndatedTasks_areLeftOut() {
        Node cancelled = new Node(a, d("2026-11-02"), d("2026-11-04"), null, null, "CANCELLED", BigDecimal.ZERO);
        Node undated = new Node(b, null, null, null, null, "NOT_STARTED", BigDecimal.ZERO);
        Forecast f = ScheduleEngine.forecast(List.of(cancelled, undated), List.of(), CAL, d("2026-11-02"));
        assertTrue(f.results().isEmpty());
        assertNull(f.finish());
    }

    @Test
    void cycleInExistingData_isReported_notLooped() {
        Forecast f = ScheduleEngine.forecast(
                List.of(node(a, "2026-11-02", "2026-11-03"), node(b, "2026-11-04", "2026-11-05")),
                List.of(fs(a, b), fs(b, a)), CAL, d("2026-11-02"));
        assertTrue(f.cyclic());
        assertEquals(2, f.unresolvedIds().size());
        assertTrue(f.results().isEmpty());
    }

    // ── Cycle check ──

    @Test
    void wouldCreateCycle_detectsIndirectLoops() {
        List<Dep> existing = List.of(fs(a, b), fs(b, c));
        assertTrue(ScheduleEngine.wouldCreateCycle(existing, c, a).isPresent());   // C→A closes A→B→C→A
        assertTrue(ScheduleEngine.wouldCreateCycle(existing, b, a).isPresent());
        assertTrue(ScheduleEngine.wouldCreateCycle(existing, a, a).isPresent());   // self
        assertTrue(ScheduleEngine.wouldCreateCycle(existing, a, c).isEmpty());     // shortcut, no loop
    }

    @Test
    void wouldCreateCycle_namesThePath() {
        List<UUID> path = ScheduleEngine.wouldCreateCycle(List.of(fs(a, b), fs(b, c)), c, a).orElseThrow();
        assertEquals(List.of(a, b, c), path);
    }

    // ── Violations ──

    @Test
    void violation_whenSuccessorStartsBeforePredecessorAllows() {
        // A ends Wed Nov 4; B (FS) should start Thu Nov 5 but is planned for Wed Nov 4 → 1 day short.
        List<Violation> v = ScheduleEngine.violations(
                List.of(node(a, "2026-11-02", "2026-11-04"), node(b, "2026-11-04", "2026-11-05")),
                List.of(fs(a, b)), CAL);
        assertEquals(1, v.size());
        assertEquals(1, v.get(0).shortfallWorkingDays());
    }

    @Test
    void noViolation_whenDatesRespectTheDependency() {
        assertTrue(ScheduleEngine.violations(
                List.of(node(a, "2026-11-02", "2026-11-04"), node(b, "2026-11-05", "2026-11-06")),
                List.of(fs(a, b)), CAL).isEmpty());
    }

    // ── Cascade ──

    @Test
    void cascade_pushesDescendantsOut_preservingDuration() {
        // A was extended to Fri Nov 6. B (Nov 5–6) and C (Nov 9–10) must slide behind it.
        Cascade c1 = ScheduleEngine.cascade(
                List.of(node(a, "2026-11-02", "2026-11-06"),
                        node(b, "2026-11-05", "2026-11-06"),
                        node(c, "2026-11-09", "2026-11-10")),
                List.of(fs(a, b), fs(b, c)), CAL, a);

        assertEquals(2, c1.shifts().size());
        Shift sb = c1.shifts().stream().filter(s -> s.taskId().equals(b)).findFirst().orElseThrow();
        Shift sc = c1.shifts().stream().filter(s -> s.taskId().equals(c)).findFirst().orElseThrow();
        assertEquals(d("2026-11-09"), sb.newStart());
        assertEquals(d("2026-11-10"), sb.newEnd());
        assertEquals(d("2026-11-11"), sc.newStart());
        assertEquals(d("2026-11-12"), sc.newEnd());
        assertTrue(c1.blocked().isEmpty());
    }

    @Test
    void cascade_neverMovesStartedWork_reportsItBlocked() {
        Node started = new Node(b, d("2026-11-05"), d("2026-11-06"), d("2026-11-05"), null,
                "IN_PROGRESS", new BigDecimal("30"));
        Cascade c1 = ScheduleEngine.cascade(
                List.of(node(a, "2026-11-02", "2026-11-06"), started, node(c, "2026-11-09", "2026-11-10")),
                List.of(fs(a, b), fs(b, c)), CAL, a);

        assertTrue(c1.shifts().isEmpty());
        assertEquals(List.of(b), c1.blocked());
    }

    @Test
    void cascade_neverPullsTasksEarlier() {
        // A moved EARLIER; B is already comfortably after it, so nothing changes.
        Cascade c1 = ScheduleEngine.cascade(
                List.of(node(a, "2026-11-02", "2026-11-03"), node(b, "2026-11-09", "2026-11-10")),
                List.of(fs(a, b)), CAL, a);
        assertTrue(c1.shifts().isEmpty());
    }

    @Test
    void cascade_onlyTouchesDescendantsOfTheEditedTask() {
        UUID unrelated = UUID.randomUUID();
        Cascade c1 = ScheduleEngine.cascade(
                List.of(node(a, "2026-11-02", "2026-11-06"), node(b, "2026-11-05", "2026-11-06"),
                        node(unrelated, "2026-11-02", "2026-11-02")),
                List.of(fs(a, b), fs(unrelated, c)), CAL, a);
        assertTrue(c1.shifts().stream().noneMatch(s -> s.taskId().equals(unrelated)));
    }
}