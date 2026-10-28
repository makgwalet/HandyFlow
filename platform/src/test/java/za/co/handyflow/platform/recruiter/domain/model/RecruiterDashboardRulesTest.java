package za.co.handyflow.platform.recruiter.domain.model;

import org.junit.jupiter.api.Test;

import java.time.Instant;

import static org.assertj.core.api.Assertions.assertThat;

class RecruiterDashboardRulesTest {
    @Test
    void onlyOfferedPeriodsAreAllowed() {
        assertThat(RecruiterDashboardRules.daysProblem(null)).isNull();
        assertThat(RecruiterDashboardRules.daysProblem(7)).isNull();
        assertThat(RecruiterDashboardRules.daysProblem(90)).isNull();
        assertThat(RecruiterDashboardRules.daysProblem(45)).isEqualTo("Choose 7, 30 or 90 days.");
        assertThat(RecruiterDashboardRules.daysOrDefault(null)).isEqualTo(30);
        assertThat(RecruiterDashboardRules.daysOrDefault(7)).isEqualTo(7);
    }

    @Test
    void periodsSitBackToBack() {
        Instant now = Instant.parse("2026-10-28T10:00:00Z");
        assertThat(RecruiterDashboardRules.periodStart(now, 30)).isEqualTo(Instant.parse("2026-09-28T10:00:00Z"));
        assertThat(RecruiterDashboardRules.previousStart(now, 30)).isEqualTo(Instant.parse("2026-08-29T10:00:00Z"));
    }

    @Test
    void changeIsAPercentageOfThePreviousPeriod() {
        assertThat(RecruiterDashboardRules.changePercent(12.0, 10.0)).isEqualTo(20.0);
        assertThat(RecruiterDashboardRules.changePercent(8.0, 10.0)).isEqualTo(-20.0);
        assertThat(RecruiterDashboardRules.changePercent(10.0, 3.0)).isEqualTo(233.3);
        assertThat(RecruiterDashboardRules.changePercent(5.0, 0.0)).isNull();
        assertThat(RecruiterDashboardRules.changePercent(null, 4.0)).isNull();
        assertThat(RecruiterDashboardRules.changePercent(4.0, null)).isNull();
    }
}
