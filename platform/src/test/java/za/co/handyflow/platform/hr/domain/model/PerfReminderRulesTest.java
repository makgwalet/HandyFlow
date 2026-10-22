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
}
