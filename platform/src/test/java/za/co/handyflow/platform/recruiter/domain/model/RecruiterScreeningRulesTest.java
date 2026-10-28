package za.co.handyflow.platform.recruiter.domain.model;

import org.junit.jupiter.api.Test;

import java.math.BigDecimal;

import static org.assertj.core.api.Assertions.assertThat;

class RecruiterScreeningRulesTest {
    private static String q(String text, String type, boolean req, String exp, String min) {
        return RecruiterScreeningRules.questionProblem(text, type, req, exp, min == null ? null : new BigDecimal(min));
    }

    @Test
    void questionsAreChecked() {
        assertThat(q("Do you have a Code 14 licence?", "YES_NO", true, "YES", null)).isNull();
        assertThat(q("Years of experience", "NUMBER", true, null, "3")).isNull();
        assertThat(q("Why this role?", "TEXT", false, null, null)).isNull();
        assertThat(q("ab", "TEXT", false, null, null)).contains("Write the question");
        assertThat(q("x".repeat(301), "TEXT", false, null, null)).contains("300");
        assertThat(q("Valid question", "DATE", false, null, null)).contains("Choose");
        assertThat(q("Valid question", "TEXT", true, "YES", null)).contains("Only a Yes/No");
        assertThat(q("Valid question", "YES_NO", true, "MAYBE", null)).contains("Yes or No");
        assertThat(q("Valid question", "YES_NO", true, null, "2")).contains("Only a Number");
        assertThat(q("Valid question", "NUMBER", true, null, "-1")).contains("out of range");
        assertThat(q("Valid question", "YES_NO", false, "YES", null)).contains("must be required");
    }

    @Test
    void listLimit() {
        assertThat(RecruiterScreeningRules.listProblem(15)).isNull();
        assertThat(RecruiterScreeningRules.listProblem(16)).contains("15");
    }

    @Test
    void answersAreChecked() {
        assertThat(RecruiterScreeningRules.answerProblem("Licence?", "YES_NO", true, "yes")).isNull();
        assertThat(RecruiterScreeningRules.answerProblem("Licence?", "YES_NO", true, " ")).contains("Please answer");
        assertThat(RecruiterScreeningRules.answerProblem("Licence?", "YES_NO", false, null)).isNull();
        assertThat(RecruiterScreeningRules.answerProblem("Licence?", "YES_NO", true, "maybe")).contains("Yes or No");
        assertThat(RecruiterScreeningRules.answerProblem("Years", "NUMBER", true, "3.5")).isNull();
        assertThat(RecruiterScreeningRules.answerProblem("Years", "NUMBER", true, "three")).contains("Enter a number");
        assertThat(RecruiterScreeningRules.answerProblem("Years", "NUMBER", true, "99999999999")).contains("out of range");
        assertThat(RecruiterScreeningRules.answerProblem("Why", "TEXT", false, "x".repeat(1001))).contains("too long");
    }

    @Test
    void criteriaMarkAnswersButNeverReject() {
        assertThat(RecruiterScreeningRules.meets("YES_NO", "YES", null, "yes")).isTrue();
        assertThat(RecruiterScreeningRules.meets("YES_NO", "YES", null, "No")).isFalse();
        assertThat(RecruiterScreeningRules.meets("NUMBER", null, new BigDecimal("3"), "3")).isTrue();
        assertThat(RecruiterScreeningRules.meets("NUMBER", null, new BigDecimal("3"), "2.9")).isFalse();
        assertThat(RecruiterScreeningRules.meets("TEXT", null, null, "anything")).isNull();
        assertThat(RecruiterScreeningRules.meets("YES_NO", null, null, "yes")).isNull();
        assertThat(RecruiterScreeningRules.meets("YES_NO", "YES", null, null)).isNull();
        assertThat(RecruiterScreeningRules.isKnockout("YES", null)).isTrue();
        assertThat(RecruiterScreeningRules.isKnockout(null, null)).isFalse();
        assertThat(RecruiterScreeningRules.clean("  hi ")).isEqualTo("hi");
    }
}
