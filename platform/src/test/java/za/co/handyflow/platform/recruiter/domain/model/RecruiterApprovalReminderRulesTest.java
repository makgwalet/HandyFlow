package za.co.handyflow.platform.recruiter.domain.model;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class RecruiterApprovalReminderRulesTest {

    @Test
    void remindsOnTheSecondDayAndEveryThirdDayAfter() {
        for (long d : new long[]{0, 1, 3, 4, 6, 7, 9}) assertThat(RecruiterApprovalRules.reminderDue(d)).isFalse();
        for (long d : new long[]{2, 5, 8, 11, 14}) assertThat(RecruiterApprovalRules.reminderDue(d)).isTrue();
    }

    @Test
    void theMessageNamesTheRoleAndTheWaitButNotTheCandidateOrSalary() {
        String job = RecruiterApprovalRules.reminderMessage(true, "Site Clerk", 5);
        assertThat(job).contains("Site Clerk").contains("5 days").contains("job");
        String offer = RecruiterApprovalRules.reminderMessage(false, "Site Clerk", 2);
        assertThat(offer).contains("offer").contains("Site Clerk").contains("2 days");
        assertThat(RecruiterApprovalRules.reminderTitle(true)).contains("job");
        assertThat(RecruiterApprovalRules.reminderTitle(false)).contains("offer");
    }
}
