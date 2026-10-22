package za.co.handyflow.platform.hr.domain.model;

import org.junit.jupiter.api.Test;

import java.time.LocalDate;

import static org.assertj.core.api.Assertions.assertThat;
import static za.co.handyflow.platform.hr.domain.model.PerfReminderRules.*;

class PerfReminderRulesTest {

    private static final LocalDate TODAY = LocalDate.of(2026, 10, 22);

    @Test
    void selfReviewIsChasedOnlyWhileWaitingForTheEmployee() {
        assertThat(selfReminderToday(PerfRules.AWAITING_SELF, TODAY.minusDays(1), TODAY)).isTrue();
        assertThat(selfReminderToday(PerfRules.AWAITING_MANAGER, TODAY.minusDays(1), TODAY)).isFalse();
        assertThat(selfReminderToday(PerfRules.SHARED, TODAY.minusDays(1), TODAY)).isFalse();
        assertThat(selfReminderToday(PerfRules.AWAITING_SELF, TODAY.plusDays(1), TODAY)).isFalse();
    }

    @Test
    void theCadenceIsTheDayAfterThenEverySeventhDay() {
        assertThat(selfReminderToday(PerfRules.AWAITING_SELF, TODAY.minusDays(2), TODAY)).isFalse();
        assertThat(selfReminderToday(PerfRules.AWAITING_SELF, TODAY.minusDays(7), TODAY)).isTrue();
        assertThat(selfReminderToday(PerfRules.AWAITING_SELF, TODAY.minusDays(8), TODAY)).isFalse();
    }

    @Test
    void theReviewerIsChasedUntilTheyHaveRated() {
        assertThat(managerReminderToday(PerfRules.AWAITING_SELF, TODAY.minusDays(1), TODAY)).isTrue();
        assertThat(managerReminderToday(PerfRules.AWAITING_MANAGER, TODAY.minusDays(14), TODAY)).isTrue();
        assertThat(managerReminderToday(PerfRules.SUBMITTED, TODAY.minusDays(1), TODAY)).isFalse();
        assertThat(managerReminderToday(PerfRules.SHARED, TODAY.minusDays(1), TODAY)).isFalse();
        assertThat(managerReminderToday(PerfRules.EXCLUDED, TODAY.minusDays(1), TODAY)).isFalse();
    }

    @Test
    void theWordsNameWhoseTurnItIsAndNeverAnythingElse() {
        assertThat(readyMessage("Ann Adams", "Mid-year", TODAY)).contains("Ann Adams").contains("Mid-year").contains("2026-10-22");
        assertThat(overdueTitle(1)).isEqualTo("1 review waiting for your rating");
        assertThat(overdueTitle(3)).isEqualTo("3 reviews waiting for your rating");
        assertThat(overdueMessage("Mid-year", 2, TODAY.minusDays(3), TODAY)).contains("2 reviews").contains("are past").contains("3 days ago");
        assertThat(selfLateMessage("Mid-year", TODAY.minusDays(1), TODAY)).contains("1 day ago");
        assertThat(releasedMessage("Mid-year")).doesNotContain("rating of");
    }

    @Test
    void aPlanIsChasedFortnightlyFromTheLastCheckInOrFromTheDayTheEmployeeWasTold() {
        LocalDate told = TODAY.minusDays(15);
        assertThat(checkinReminderToday(PerfRules.PLAN_ACTIVE, told, told, null, TODAY)).isTrue();             // 14 days + 1
        assertThat(checkinReminderToday(PerfRules.PLAN_ACTIVE, told, told, null, TODAY.minusDays(1))).isFalse(); // exactly 14
        assertThat(checkinReminderToday(PerfRules.PLAN_ACTIVE, told, told, TODAY.minusDays(3), TODAY)).isFalse();
        assertThat(checkinReminderToday(PerfRules.PLAN_ACTIVE, told, told, TODAY.minusDays(15), TODAY)).isTrue();
        assertThat(checkinReminderToday(PerfRules.PLAN_ACTIVE, told, told, TODAY.minusDays(21), TODAY)).isTrue();  // weekly after
        assertThat(checkinReminderToday(PerfRules.PLAN_ACTIVE, told, told, TODAY.minusDays(16), TODAY)).isFalse();
    }

    @Test
    void onlyARunningPlanIsChased() {
        LocalDate told = TODAY.minusDays(15);
        for (String status : new String[]{PerfRules.PLAN_DRAFT, PerfRules.MET, PerfRules.NOT_MET, PerfRules.PLAN_CANCELLED}) {
            assertThat(checkinReminderToday(status, told, told, null, TODAY)).isFalse();
            assertThat(endedReminderToday(status, TODAY.minusDays(1), TODAY)).isFalse();
        }
        assertThat(endedReminderToday(PerfRules.PLAN_ACTIVE, TODAY.minusDays(1), TODAY)).isTrue();
        assertThat(endedReminderToday(PerfRules.PLAN_ACTIVE, TODAY, TODAY)).isFalse();
    }

    @Test
    void planAndGoalWordsKeepTheDetailsOut() {
        assertThat(checkinMessage("Ann Adams", null, TODAY)).contains("none has been recorded").contains("Ann Adams");
        assertThat(checkinMessage("Ann Adams", TODAY.minusDays(16), TODAY)).contains("16 days ago");
        assertThat(planEndedMessage("Ann Adams", TODAY.minusDays(1), TODAY)).contains("1 day ago");
        assertThat(goalProposedMessage("Ann", "x".repeat(200)).length()).isLessThan(200);
        assertThat(goalSetMessage("Cut errors", TODAY)).contains("Cut errors").contains(TODAY.toString());
    }
}
