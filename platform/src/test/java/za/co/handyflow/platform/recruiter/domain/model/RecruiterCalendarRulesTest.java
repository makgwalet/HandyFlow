package za.co.handyflow.platform.recruiter.domain.model;

import org.junit.jupiter.api.Test;

import java.time.LocalDate;

import static org.assertj.core.api.Assertions.assertThat;

class RecruiterCalendarRulesTest {
    @Test
    void theRangeMustBeWholeDaysAndShort() {
        LocalDate d = LocalDate.of(2026, 10, 26);
        assertThat(RecruiterCalendarRules.rangeProblem(d, d.plusDays(6))).isNull();
        assertThat(RecruiterCalendarRules.rangeProblem(d, d.plusDays(41))).isNull();
        assertThat(RecruiterCalendarRules.rangeProblem(d, d.plusDays(42))).contains("at most 42");
        assertThat(RecruiterCalendarRules.rangeProblem(d, d.minusDays(1))).contains("cannot be before");
        assertThat(RecruiterCalendarRules.rangeProblem(null, d)).isNotNull();
    }

    @Test
    void weeksStartOnMonday() {
        assertThat(RecruiterCalendarRules.weekStart(LocalDate.of(2026, 10, 28))).isEqualTo(LocalDate.of(2026, 10, 26));
        assertThat(RecruiterCalendarRules.weekStart(LocalDate.of(2026, 10, 26))).isEqualTo(LocalDate.of(2026, 10, 26));
        assertThat(RecruiterCalendarRules.weekStart(LocalDate.of(2026, 11, 1))).isEqualTo(LocalDate.of(2026, 10, 26));
    }

    @Test
    void movedOrCalledOffInterviewsDoNotStand() {
        assertThat(RecruiterCalendarRules.stands(null)).isTrue();
        assertThat(RecruiterCalendarRules.stands("PENDING")).isTrue();
        assertThat(RecruiterCalendarRules.stands("PASSED")).isTrue();
        assertThat(RecruiterCalendarRules.stands("RESCHEDULED")).isFalse();
        assertThat(RecruiterCalendarRules.stands("CANCELLED")).isFalse();
    }
}
