package za.co.handyflow.platform.recruiter.domain.model;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class RecruiterApprovalMessagesTest {

    @Test
    void theRequestNamesTheRoleAndNeverTheSalary() {
        String m = RecruiterApprovalRules.requestedMessage(false, "Site Clerk", "Thandi Mokoena");
        assertThat(m).contains("Thandi Mokoena").contains("Site Clerk").contains("offer");
        assertThat(m).doesNotContain("R ").doesNotContain("salary");
        assertThat(RecruiterApprovalRules.requestedMessage(true, "Site Clerk", null)).startsWith("A colleague").contains("published");
    }

    @Test
    void theOutcomeSaysWhatToDoNext() {
        assertThat(RecruiterApprovalRules.decidedMessage(true, true, "Clerk", "Sam")).contains("publish it now");
        assertThat(RecruiterApprovalRules.decidedMessage(false, true, "Clerk", "Sam")).contains("send the letter now");
        assertThat(RecruiterApprovalRules.decidedMessage(false, false, "Clerk", null)).startsWith("An approver").contains("Open it to read why");
        assertThat(RecruiterApprovalRules.decidedTitle(true, false)).isEqualTo("Your job was not approved");
    }

    @Test
    void theOfferPdfRuleFollowsTheSendRule() {
        assertThat(RecruiterApprovalRules.offerPdfProblem(false, "NOT_SUBMITTED")).isNull();
        assertThat(RecruiterApprovalRules.offerPdfProblem(true, RecruiterApprovalRules.APPROVED)).isNull();
        assertThat(RecruiterApprovalRules.offerPdfProblem(true, RecruiterApprovalRules.SUBMITTED)).contains("waiting");
        assertThat(RecruiterApprovalRules.offerPdfProblem(true, RecruiterApprovalRules.REJECTED)).contains("not approved");
        assertThat(RecruiterApprovalRules.offerPdfProblem(true, "NOT_SUBMITTED")).contains("Send it for approval");
    }

    @Test
    void consentMustBeGivenExplicitly() {
        assertThat(RecruiterConsentRules.problem(null)).isNotNull();
        assertThat(RecruiterConsentRules.problem(false)).isNotNull();
        assertThat(RecruiterConsentRules.problem(true)).isNull();
        assertThat(RecruiterConsentRules.STATEMENT).isNotBlank();
        assertThat(RecruiterConsentRules.VERSION).isNotBlank();
    }
}
