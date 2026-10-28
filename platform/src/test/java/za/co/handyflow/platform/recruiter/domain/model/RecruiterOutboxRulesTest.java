package za.co.handyflow.platform.recruiter.domain.model;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class RecruiterOutboxRulesTest {
    @Test
    void retriesThenGivesUp() {
        assertThat(RecruiterOutboxRules.statusAfter(1, true)).isEqualTo("SENT");
        assertThat(RecruiterOutboxRules.statusAfter(1, false)).isEqualTo("PENDING");
        assertThat(RecruiterOutboxRules.statusAfter(4, false)).isEqualTo("PENDING");
        assertThat(RecruiterOutboxRules.statusAfter(5, false)).isEqualTo("FAILED");
        assertThat(RecruiterOutboxRules.statusAfter(5, true)).isEqualTo("SENT");
    }

    @Test
    void waitsLongerEachTime() {
        assertThat(RecruiterOutboxRules.delayMinutes(1)).isEqualTo(1);
        assertThat(RecruiterOutboxRules.delayMinutes(2)).isEqualTo(5);
        assertThat(RecruiterOutboxRules.delayMinutes(3)).isEqualTo(30);
        assertThat(RecruiterOutboxRules.delayMinutes(4)).isEqualTo(120);
        assertThat(RecruiterOutboxRules.delayMinutes(9)).isEqualTo(120);
        assertThat(RecruiterOutboxRules.delayMinutes(0)).isEqualTo(1);
    }

    @Test
    void errorTextIsShortAndOnOneLine() {
        assertThat(RecruiterOutboxRules.errorText(null)).isNull();
        assertThat(RecruiterOutboxRules.errorText(new RuntimeException("550 rejected\r\nTry later"))).isEqualTo("550 rejected Try later");
        assertThat(RecruiterOutboxRules.errorText(new IllegalStateException())).isEqualTo("IllegalStateException");
        assertThat(RecruiterOutboxRules.errorText(new RuntimeException("x".repeat(400)))).hasSize(300);
    }

    @Test
    void retryAndAddresses() {
        assertThat(RecruiterOutboxRules.retryProblem("FAILED")).isNull();
        assertThat(RecruiterOutboxRules.retryProblem("SENT")).contains("could not be sent");
        assertThat(RecruiterOutboxRules.deliverable("thandi@example.co.za")).isTrue();
        assertThat(RecruiterOutboxRules.deliverable("not an email")).isFalse();
        assertThat(RecruiterOutboxRules.deliverable(null)).isFalse();
    }
}
