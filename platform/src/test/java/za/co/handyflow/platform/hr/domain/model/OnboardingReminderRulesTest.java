package za.co.handyflow.platform.hr.domain.model;

import org.junit.jupiter.api.Test;
import za.co.handyflow.platform.hr.domain.model.OnboardingReminderRules.Late;

import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static za.co.handyflow.platform.hr.domain.model.OnboardingReminderRules.*;

class OnboardingReminderRulesTest {

    private static final LocalDate TODAY = LocalDate.of(2026, 10, 22);

    @Test
    void aTaskIsNotMentionedBeforeItIsLate() {
        assertThat(remindToday(true, TODAY.plusDays(3), TODAY)).isFalse();
        assertThat(remindToday(true, TODAY, TODAY)).isFalse();
        assertThat(remindToday(true, null, TODAY)).isFalse();
    }

    @Test
    void aTaskIsMentionedTheDayAfterItFallsDue() {
        assertThat(remindToday(true, TODAY.minusDays(1), TODAY)).isTrue();
        assertThat(remindToday(false, TODAY.minusDays(1), TODAY)).isTrue();
    }

    @Test
    void requiredTasksRepeatWeeklyAndOptionalOnesDoNot() {
        assertThat(remindToday(true, TODAY.minusDays(2), TODAY)).isFalse();
        assertThat(remindToday(true, TODAY.minusDays(7), TODAY)).isTrue();
        assertThat(remindToday(true, TODAY.minusDays(14), TODAY)).isTrue();
        assertThat(remindToday(true, TODAY.minusDays(10), TODAY)).isFalse();
        assertThat(remindToday(false, TODAY.minusDays(7), TODAY)).isFalse();
    }

    @Test
    void onlyTheTasksDueForAMentionAreListedMostLateFirst() {
        Late fresh = new Late("Fresh", false, TODAY.minusDays(1));
        Late weekly = new Late("Weekly", true, TODAY.minusDays(14));
        Late quiet = new Late("Quiet", true, TODAY.minusDays(3));
        assertThat(toMention(List.of(fresh, quiet, weekly), TODAY)).containsExactly(weekly, fresh);
    }

    @Test
    void theMessageNamesTheTasksAndHowLateTheyAre() {
        List<Late> two = List.of(new Late("Bank details recorded", true, TODAY.minusDays(14)), new Late("Equipment issued", false, TODAY.minusDays(1)));
        assertThat(title("Thandi Mokoena", 2)).isEqualTo("Onboarding overdue: Thandi Mokoena (2 tasks)");
        assertThat(title("Thandi Mokoena", 1)).contains("(1 task)");
        String m = message("Thandi Mokoena", two, TODAY);
        assertThat(m).contains("2 tasks").contains("Bank details recorded (14 days late)").contains("Equipment issued (1 day late, optional)");
    }

    @Test
    void aLongListIsCutOffAndSaysHowManyMore() {
        List<Late> many = new ArrayList<>();
        for (int i = 0; i < 8; i++) many.add(new Late("Task " + i, true, TODAY.minusDays(1)));
        String m = message("Sam", many, TODAY);
        assertThat(m).contains("Task 4").doesNotContain("Task 5").contains("and 3 more");
    }
}
