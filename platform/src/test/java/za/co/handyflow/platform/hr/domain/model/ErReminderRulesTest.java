package za.co.handyflow.platform.hr.domain.model;

import org.junit.jupiter.api.Test;
import za.co.handyflow.platform.hr.domain.model.ErReminderRules.When;

import java.time.LocalDate;

import static org.junit.jupiter.api.Assertions.*;

class ErReminderRulesTest {

    private static final LocalDate DUE = LocalDate.of(2026, 11, 10);

    @Test
    void oneReminderTheDayBeforeThenNothingUntilItIsLate() {
        assertEquals(When.NONE, ErReminderRules.when(DUE, DUE.minusDays(3)));
        assertEquals(When.NONE, ErReminderRules.when(DUE, DUE.minusDays(2)));
        assertEquals(When.TOMORROW, ErReminderRules.when(DUE, DUE.minusDays(1)));
        assertEquals(When.NONE, ErReminderRules.when(DUE, DUE));
    }

    @Test
    void lateFromTheDayAfterThenEverySeventhDay() {
        assertEquals(When.LATE, ErReminderRules.when(DUE, DUE.plusDays(1)));
        for (int i = 2; i <= 7; i++) assertEquals(When.NONE, ErReminderRules.when(DUE, DUE.plusDays(i)), "day " + i);
        assertEquals(When.LATE, ErReminderRules.when(DUE, DUE.plusDays(8)));
        assertEquals(When.NONE, ErReminderRules.when(DUE, DUE.plusDays(9)));
        assertEquals(When.LATE, ErReminderRules.when(DUE, DUE.plusDays(15)));
    }

    @Test
    void noDateMeansNoReminder() {
        assertEquals(When.NONE, ErReminderRules.when(null, DUE));
    }

    @Test
    void stepsAreNamedPlainly() {
        assertEquals("Meeting", ErReminderRules.step("Meeting"));
        assertEquals("Grievance response", ErReminderRules.step("Response due"));
        assertEquals("Telling the employee the decision", ErReminderRules.step("Tell the employee"));
        assertEquals("End of the appeal period", ErReminderRules.step("Appeal period ends"));
        assertEquals("Case step", ErReminderRules.step("Something new"));
        assertEquals("Case step", ErReminderRules.step(null));
    }

    @Test
    void wordsCarryTheCaseNumberAndStepOnly() {
        assertEquals("ER-2026-007: meeting is due tomorrow", ErReminderRules.title("ER-2026-007", "Meeting", When.TOMORROW));
        assertEquals("ER-2026-007: grievance response is late", ErReminderRules.title("ER-2026-007", "Response due", When.LATE));
        assertEquals("Meeting for case ER-2026-007 is due on 2026-11-10. Open the case in Employee relations.",
                ErReminderRules.message("ER-2026-007", "Meeting", DUE, When.TOMORROW, DUE.minusDays(1)));
        assertEquals("Grievance response for case ER-2026-007 was due on 2026-11-10 and is 1 day late. Open the case in Employee relations.",
                ErReminderRules.message("ER-2026-007", "Response due", DUE, When.LATE, DUE.plusDays(1)));
        assertTrue(ErReminderRules.message("ER-2026-007", "Response due", DUE, When.LATE, DUE.plusDays(8)).contains("is 8 days late"));
    }
}
