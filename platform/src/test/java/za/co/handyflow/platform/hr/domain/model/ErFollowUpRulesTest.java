package za.co.handyflow.platform.hr.domain.model;

import org.junit.jupiter.api.Test;

import java.time.LocalDate;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;

class ErFollowUpRulesTest {

    private static LocalDate d(int y, int m, int day) { return LocalDate.of(y, m, day); }
    private static final LocalDate TOLD = d(2026, 10, 12);

    // ── Public holidays in working-day counts ─────────────────────────────────────────────────────

    @Test
    void aPublicHolidayIsNotAWorkingDay() {
        LocalDate friday = d(2026, 10, 16);
        // Monday 19 October is a holiday, so one working day after the Friday is the Tuesday
        assertThat(ErRules.addWorkingDays(friday, 1, Set.of(d(2026, 10, 19)))).isEqualTo(d(2026, 10, 20));
        assertThat(ErRules.addWorkingDays(friday, 1, Set.of())).isEqualTo(d(2026, 10, 19));
        assertThat(ErRules.addWorkingDays(friday, 1)).isEqualTo(d(2026, 10, 19));
    }

    @Test
    void aHolidayOnAWeekendChangesNothing() {
        assertThat(ErRules.addWorkingDays(d(2026, 10, 16), 1, Set.of(d(2026, 10, 17)))).isEqualTo(d(2026, 10, 19));
    }

    // ── Ending employment after a dismissal ───────────────────────────────────────────────────────

    @Test
    void employmentEndsOnlyAfterADismissalThatWasCommunicated() {
        assertThat(DisciplineRules.endEmploymentProblem("DECIDED", "DISMISSAL", TOLD, "ACTIVE", d(2026, 10, 13))).isNull();
        assertThat(DisciplineRules.endEmploymentProblem("CLOSED", "DISMISSAL", TOLD, "ACTIVE", TOLD)).isNull();
        assertThat(DisciplineRules.endEmploymentProblem("DECIDED", "FINAL_WRITTEN_WARNING", TOLD, "ACTIVE", TOLD)).contains("dismissal");
        assertThat(DisciplineRules.endEmploymentProblem("DECIDED", null, TOLD, "ACTIVE", TOLD)).contains("dismissal");
        assertThat(DisciplineRules.endEmploymentProblem("DECIDED", "DISMISSAL", null, "ACTIVE", TOLD)).contains("Tell the employee");
    }

    @Test
    void anAppealThatIsStillWaitingStopsIt() {
        assertThat(DisciplineRules.endEmploymentProblem("APPEALED", "DISMISSAL", TOLD, "ACTIVE", TOLD)).contains("appeal");
        assertThat(DisciplineRules.endEmploymentProblem("INVESTIGATING", "DISMISSAL", TOLD, "ACTIVE", TOLD)).contains("no decision");
    }

    @Test
    void theLastDayCannotPrecedeTheDayTheEmployeeWasToldAndEmploymentEndsOnce() {
        assertThat(DisciplineRules.endEmploymentProblem("DECIDED", "DISMISSAL", TOLD, "ACTIVE", TOLD.minusDays(1))).contains("cannot be before");
        assertThat(DisciplineRules.endEmploymentProblem("DECIDED", "DISMISSAL", TOLD, "ACTIVE", null)).contains("last day");
        assertThat(DisciplineRules.endEmploymentProblem("DECIDED", "DISMISSAL", TOLD, "TERMINATED", TOLD)).contains("already ended");
    }

    // ── Validity of an old warning ────────────────────────────────────────────────────────────────

    @Test
    void anOldWarningCanBeGivenAValidityOnce() {
        assertThat(DisciplineRules.validityProblem("WRITTEN_WARNING", d(2026, 3, 1), null, 6)).isNull();
        assertThat(DisciplineRules.validityProblem("WRITTEN_WARNING", d(2026, 3, 1), d(2026, 9, 1), 6)).contains("already has");
        assertThat(DisciplineRules.validityProblem("DISMISSAL", d(2026, 3, 1), null, 6)).contains("Only a warning");
        assertThat(DisciplineRules.validityProblem("VERBAL_WARNING", null, null, 6)).contains("no date");
        assertThat(DisciplineRules.validityProblem("VERBAL_WARNING", d(2026, 3, 1), null, 0)).contains("1 to 60");
        assertThat(DisciplineRules.validityProblem("VERBAL_WARNING", d(2026, 3, 1), null, 61)).contains("1 to 60");
        assertThat(DisciplineRules.validityProblem("VERBAL_WARNING", d(2026, 3, 1), null, null)).contains("1 to 60");
    }
}
