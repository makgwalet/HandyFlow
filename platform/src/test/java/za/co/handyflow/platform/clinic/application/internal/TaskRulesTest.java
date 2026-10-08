package za.co.handyflow.platform.clinic.application.internal;

import org.junit.jupiter.api.Test;

import java.time.LocalDate;

import static org.junit.jupiter.api.Assertions.*;

class TaskRulesTest {

    static final LocalDate TODAY = LocalDate.of(2026, 10, 8);

    @Test void titleNeedsThreeToTwoHundredCharacters() {
        assertEquals("Call Mrs Dlamini", TaskRules.title("  Call Mrs Dlamini "));
        assertThrows(IllegalArgumentException.class, () -> TaskRules.title("ab"));
        assertThrows(IllegalArgumentException.class, () -> TaskRules.title(null));
        assertThrows(IllegalArgumentException.class, () -> TaskRules.title("x".repeat(201)));
    }

    @Test void detailIsOptionalAndCapped() {
        assertNull(TaskRules.detail("   "));
        assertNull(TaskRules.detail(null));
        assertEquals("ok", TaskRules.detail(" ok "));
        assertThrows(IllegalArgumentException.class, () -> TaskRules.detail("x".repeat(1001)));
    }

    @Test void kindDefaultsToGeneralAndRejectsUnknown() {
        assertEquals("GENERAL", TaskRules.kind(null));
        assertEquals("RESULT_FOLLOW_UP", TaskRules.kind(" result_follow_up "));
        assertThrows(IllegalArgumentException.class, () -> TaskRules.kind("SELL_THINGS"));
    }

    @Test void aNewTaskCannotBeDueInThePast() {
        assertNull(TaskRules.due(null, TODAY));
        assertEquals(TODAY, TaskRules.due(TODAY, TODAY));
        assertThrows(IllegalArgumentException.class, () -> TaskRules.due(TODAY.minusDays(1), TODAY));
    }

    @Test void dismissingNeedsAReason() {
        assertEquals("Patient already seen", TaskRules.dismissReason(" Patient already seen "));
        assertThrows(IllegalArgumentException.class, () -> TaskRules.dismissReason("no"));
        assertThrows(IllegalArgumentException.class, () -> TaskRules.dismissReason(null));
        assertNull(TaskRules.doneNote(""));
        assertThrows(IllegalArgumentException.class, () -> TaskRules.doneNote("x".repeat(501)));
    }

    @Test void onlyOpenTasksAreOverdueOrCloseable() {
        assertTrue(TaskRules.overdue("OPEN", TODAY.minusDays(1), TODAY));
        assertFalse(TaskRules.overdue("OPEN", TODAY, TODAY));
        assertFalse(TaskRules.overdue("OPEN", null, TODAY));
        assertFalse(TaskRules.overdue("DONE", TODAY.minusDays(5), TODAY));
        assertDoesNotThrow(() -> TaskRules.requireOpen("OPEN"));
        assertThrows(IllegalStateException.class, () -> TaskRules.requireOpen("DONE"));
        assertThrows(IllegalStateException.class, () -> TaskRules.requireOpen("DISMISSED"));
    }
}
