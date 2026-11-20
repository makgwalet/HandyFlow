package za.co.handyflow.platform.legalpractice.application.internal;

import org.junit.jupiter.api.Test;

import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

class TaskRulesTest {

    private static final LocalDate TODAY = LocalDate.of(2026, 6, 10);

    @Test
    void onlyOpenAndInProgressAreLive() {
        assertTrue(TaskRules.live("OPEN"));
        assertTrue(TaskRules.live("IN_PROGRESS"));
        assertFalse(TaskRules.live("DONE"));
        assertFalse(TaskRules.live("CANCELLED"));
    }

    @Test
    void overdueNeedsALiveTaskWithADueDateBeforeToday() {
        assertTrue(TaskRules.overdue("OPEN", TODAY.minusDays(1), TODAY));
        assertFalse(TaskRules.overdue("OPEN", TODAY, TODAY));
        assertFalse(TaskRules.overdue("OPEN", null, TODAY));
        assertFalse(TaskRules.overdue("DONE", TODAY.minusDays(5), TODAY));
        assertFalse(TaskRules.overdue("CANCELLED", TODAY.minusDays(5), TODAY));
    }

    @Test
    void reasonsNeedTenCharacters() {
        assertNotNull(TaskRules.reasonProblem(null, "cancelling"));
        assertNotNull(TaskRules.reasonProblem("too short", "cancelling"));
        assertNotNull(TaskRules.reasonProblem("         x ", "cancelling"));
        assertNull(TaskRules.reasonProblem("Client withdrew instruction", "cancelling"));
    }

    @Test
    void taskTitleAndPriority() {
        assertNull(TaskRules.taskProblem("Draft affidavit", "HIGH"));
        assertNotNull(TaskRules.taskProblem(null, "HIGH"));
        assertNotNull(TaskRules.taskProblem(" a ", "HIGH"));
        assertNotNull(TaskRules.taskProblem("x".repeat(201), "HIGH"));
        assertNotNull(TaskRules.taskProblem("Draft affidavit", "SOON"));
    }

    @Test
    void longTextIsRefusedBeforeItReachesTheDatabase() {
        assertNotNull(TaskRules.taskProblem("Draft affidavit", "x".repeat(2001), "HIGH"));
        assertNull(TaskRules.taskProblem("Draft affidavit", "x".repeat(2000), "HIGH"));
        assertNotNull(TaskRules.templateProblem("Open file", "x".repeat(1001), null));
        assertNotNull(TaskRules.templateProblem("Open file", null, "x".repeat(51)));
        assertNull(TaskRules.templateProblem("Open file", "ok", "LITIGATION"));
        assertNotNull(TaskRules.stepsProblem(List.of(new TaskRules.Step("Open file", "x".repeat(2001), "NORMAL", 0, "STARTER"))));
    }

    @Test
    void statusMoves() {
        assertEquals(List.of("IN_PROGRESS", "DONE", "CANCELLED"), TaskRules.nextStatuses("OPEN"));
        assertEquals(List.of("OPEN", "DONE", "CANCELLED"), TaskRules.nextStatuses("IN_PROGRESS"));
        assertEquals(List.of("OPEN"), TaskRules.nextStatuses("DONE"));
        assertTrue(TaskRules.nextStatuses("CANCELLED").isEmpty());
        assertNull(TaskRules.moveProblem("OPEN", "DONE"));
        assertNotNull(TaskRules.moveProblem("DONE", "CANCELLED"));
        assertNotNull(TaskRules.moveProblem("CANCELLED", "OPEN"));
    }

    @Test
    void templateNeedsAName() {
        assertNull(TaskRules.templateProblem("Opening a litigation file"));
        assertNotNull(TaskRules.templateProblem(null));
        assertNotNull(TaskRules.templateProblem(" x "));
        assertNotNull(TaskRules.templateProblem("x".repeat(121)));
    }

    private static TaskRules.Step step(String title, int offset) { return new TaskRules.Step(title, null, "NORMAL", offset, "STARTER"); }

    @Test
    void stepsAreChecked() {
        assertNull(TaskRules.stepsProblem(List.of(step("Open file", 0), step("Send letter of demand", 7))));
        assertNotNull(TaskRules.stepsProblem(List.of()));
        assertNotNull(TaskRules.stepsProblem(List.of(step("", 0))));
        assertNotNull(TaskRules.stepsProblem(List.of(step("Open file", -1))));
        assertNotNull(TaskRules.stepsProblem(List.of(step("Open file", 731))));
        assertNotNull(TaskRules.stepsProblem(List.of(new TaskRules.Step("Open file", null, "SOON", 0, "STARTER"))));
        assertNotNull(TaskRules.stepsProblem(List.of(new TaskRules.Step("Open file", null, "NORMAL", 0, "EVERYONE"))));
        List<TaskRules.Step> many = new ArrayList<>();
        for (int i = 0; i < 31; i++) many.add(step("Step " + i, i));
        assertNotNull(TaskRules.stepsProblem(many));
        many.remove(30);
        assertNull(TaskRules.stepsProblem(many));
    }

    @Test
    void startDateMustBeWithinAYear() {
        assertNull(TaskRules.applyProblem(TODAY, TODAY));
        assertNull(TaskRules.applyProblem(TODAY.minusDays(30), TODAY));
        assertNotNull(TaskRules.applyProblem(null, TODAY));
        assertNotNull(TaskRules.applyProblem(TODAY.plusDays(400), TODAY));
        assertNotNull(TaskRules.applyProblem(TODAY.minusDays(400), TODAY));
    }

    @Test
    void dueDatesCountFromTheStart() {
        assertEquals(LocalDate.of(2026, 6, 17), TaskRules.dueFor(TODAY, 7));
        assertEquals(TODAY, TaskRules.dueFor(TODAY, 0));
    }
}
