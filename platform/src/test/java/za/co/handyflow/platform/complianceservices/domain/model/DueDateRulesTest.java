package za.co.handyflow.platform.complianceservices.domain.model;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.time.LocalDate;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class DueDateRulesTest {

    private static final LocalDate TODAY = LocalDate.of(2026, 11, 24);
    private static final LocalDate END = TODAY.plusMonths(12);
    private static final DueDateRules.Inputs NONE = new DueDateRules.Inputs(null, null);

    private static LocalDate d(int y, int m, int day) { return LocalDate.of(y, m, day); }

    @Test @DisplayName("a fixed yearly date falls once in the window")
    void annualFixed() {
        var r = DueDateRules.between(new DueDateRules.Rule("ANNUAL_FIXED", 1, 31, null, null), NONE, TODAY, END);
        assertThat(r.dates()).containsExactly(d(2027, 1, 31));
    }

    @Test @DisplayName("29 February falls on the last day of February in other years")
    void leapDay() {
        var r = DueDateRules.between(new DueDateRules.Rule("ANNUAL_FIXED", 2, 29, null, null), NONE, TODAY, END);
        assertThat(r.dates()).containsExactly(d(2027, 2, 28));
    }

    @Test @DisplayName("a monthly day is clamped to short months and starts from the window")
    void monthly() {
        var r = DueDateRules.between(new DueDateRules.Rule("MONTHLY_DAY", null, 31, null, null), NONE, d(2027, 1, 15), d(2027, 3, 31));
        assertThat(r.dates()).containsExactly(d(2027, 1, 31), d(2027, 2, 28), d(2027, 3, 31));
    }

    @Test @DisplayName("months after the financial year end needs that month, and says so when it is missing")
    void afterFyeMissing() {
        var r = DueDateRules.between(new DueDateRules.Rule("ANNUAL_AFTER_FYE", null, null, null, 5), NONE, TODAY, END);
        assertThat(r.dates().isEmpty()).isTrue();
        assertThat(r.reason()).contains("financial year end");
    }

    @Test @DisplayName("five months after a February year end is the end of July")
    void afterFye() {
        var r = DueDateRules.between(new DueDateRules.Rule("ANNUAL_AFTER_FYE", null, null, null, 5), new DueDateRules.Inputs(2, null), TODAY, END);
        assertThat(r.dates()).containsExactly(d(2027, 7, 31));
    }

    @Test @DisplayName("a December year end with a two month offset falls at the end of February")
    void afterFyeNextYear() {
        var r = DueDateRules.between(new DueDateRules.Rule("ANNUAL_AFTER_FYE", null, null, null, 2), new DueDateRules.Inputs(12, null), TODAY, END);
        assertThat(r.dates()).containsExactly(d(2027, 2, 28));
    }

    @Test @DisplayName("quarter end plus days gives each quarter in the window")
    void quarterly() {
        var r = DueDateRules.between(new DueDateRules.Rule("QUARTER_END_PLUS", null, null, 25, null), NONE, TODAY, d(2027, 6, 30));
        // quarter ends plus 25 days: 30 Sep -> 25 Oct (already past), 31 Dec -> 25 Jan, 31 Mar -> 25 Apr, 30 Jun -> 25 Jul (outside)
        assertThat(r.dates()).containsExactly(d(2027, 1, 25), d(2027, 4, 25));
    }

    @Test @DisplayName("an anniversary rule needs the registration date")
    void anniversary() {
        var missing = DueDateRules.between(new DueDateRules.Rule("ANNIVERSARY", null, null, null, 1), NONE, TODAY, END);
        assertThat(missing.reason()).contains("registered");
        var r = DueDateRules.between(new DueDateRules.Rule("ANNIVERSARY", null, null, null, 1), new DueDateRules.Inputs(null, d(2019, 3, 15)), TODAY, END);
        assertThat(r.dates()).containsExactly(d(2027, 4, 15));
    }

    @Test @DisplayName("an anniversary never falls before the registration")
    void anniversaryNotBeforeRegistration() {
        var r = DueDateRules.between(new DueDateRules.Rule("ANNIVERSARY", null, null, null, 0), new DueDateRules.Inputs(null, d(2027, 3, 1)), TODAY, d(2027, 12, 31));
        assertThat(r.dates()).containsExactly(d(2027, 3, 1));
        var before = DueDateRules.between(new DueDateRules.Rule("ANNIVERSARY", null, null, null, 0), new DueDateRules.Inputs(null, d(2027, 3, 1)), TODAY, d(2027, 2, 1));
        assertThat(before.dates().isEmpty()).isTrue();
    }

    @Test @DisplayName("event and expiry rules are reported, not guessed")
    void eventRules() {
        assertThat(DueDateRules.between(new DueDateRules.Rule("AFTER_EVENT", null, null, 30, null), NONE, TODAY, END).reason()).contains("event");
        assertThat(DueDateRules.between(new DueDateRules.Rule("BEFORE_EXPIRY", null, null, 30, null), NONE, TODAY, END).reason()).contains("expiry");
        assertThat(DueDateRules.between(new DueDateRules.Rule("NONE", null, null, null, null), NONE, TODAY, END).reason()).contains("No due date");
    }

    @Test @DisplayName("the window must make sense and stay within two years")
    void window() {
        var rule = new DueDateRules.Rule("ANNUAL_FIXED", 1, 31, null, null);
        assertThatThrownBy(() -> DueDateRules.between(rule, NONE, TODAY, TODAY.minusDays(1))).isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> DueDateRules.between(rule, NONE, TODAY, TODAY.plusMonths(25))).isInstanceOf(IllegalArgumentException.class);
    }

    @Test @DisplayName("key dates are checked")
    void keyDates() {
        DueDateRules.keyDates(2, d(2019, 3, 15), TODAY);
        DueDateRules.keyDates(null, null, TODAY);
        assertThatThrownBy(() -> DueDateRules.keyDates(13, null, TODAY)).isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> DueDateRules.keyDates(null, TODAY.plusDays(1), TODAY)).isInstanceOf(IllegalArgumentException.class);
    }

    @Test @DisplayName("regeneration creates what is missing and cancels pending dates no longer wanted")
    void diff() {
        List<LocalDate> wanted = List.of(d(2027, 1, 25), d(2027, 4, 25));
        assertThat(DueDateRules.toCreate(List.of(d(2027, 1, 25)), wanted)).containsExactly(d(2027, 4, 25));
        assertThat(DueDateRules.toCancel(List.of(d(2027, 1, 25), d(2027, 3, 1)), wanted)).containsExactly(d(2027, 3, 1));
        assertThat(DueDateRules.toCreate(List.of(d(2027, 1, 25), d(2027, 4, 25)), wanted).isEmpty()).isTrue();
    }
}
