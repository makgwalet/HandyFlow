package za.co.handyflow.platform.recruiter.domain.model;

import org.junit.jupiter.api.Test;

import java.nio.charset.StandardCharsets;

import static org.assertj.core.api.Assertions.assertThat;

class RecruiterSecurityRulesTest {

    private static byte[] pdf(int size) {
        byte[] b = new byte[size];
        byte[] head = "%PDF-1.4\n".getBytes(StandardCharsets.US_ASCII);
        System.arraycopy(head, 0, b, 0, Math.min(head.length, size));
        return b;
    }

    @Test
    void aRealPdfWithAPlainNameIsAccepted() {
        assertThat(RecruiterSecurityRules.cvProblem(pdf(2000), "Thandi Nkosi CV.pdf")).isNull();
        assertThat(RecruiterSecurityRules.cvProblem(pdf(2000), null)).isNull();
        assertThat(RecruiterSecurityRules.cvProblem(pdf(2000), " ")).isNull();
    }

    @Test
    void anEmptyFileIsRefused() {
        assertThat(RecruiterSecurityRules.cvProblem(new byte[0], "cv.pdf")).isEqualTo("The uploaded CV is empty.");
        assertThat(RecruiterSecurityRules.cvProblem(null, "cv.pdf")).isEqualTo("The uploaded CV is empty.");
    }

    @Test
    void aFileOverFiveMegabytesIsRefusedAndExactlyFiveIsAccepted() {
        assertThat(RecruiterSecurityRules.cvProblem(pdf(RecruiterSecurityRules.MAX_CV_BYTES), "cv.pdf")).isNull();
        assertThat(RecruiterSecurityRules.cvProblem(pdf(RecruiterSecurityRules.MAX_CV_BYTES + 1), "cv.pdf")).contains("too large");
    }

    @Test
    void textOrAProgramPretendingToBeAPdfIsRefused() {
        assertThat(RecruiterSecurityRules.cvProblem("plain text".getBytes(StandardCharsets.US_ASCII), "cv.pdf")).isEqualTo("The CV must be a PDF file.");
        assertThat(RecruiterSecurityRules.cvProblem(new byte[]{'M', 'Z', 1, 2, 3, 4}, "cv.pdf")).isEqualTo("The CV must be a PDF file.");
        assertThat(RecruiterSecurityRules.cvProblem(new byte[]{'%', 'P', 'D'}, "cv.pdf")).isEqualTo("The CV must be a PDF file.");
    }

    @Test
    void unsafeFileNamesAreRefused() {
        assertThat(RecruiterSecurityRules.cvProblem(pdf(100), "../../etc/passwd.pdf")).contains("not allowed");
        assertThat(RecruiterSecurityRules.cvProblem(pdf(100), "a\\b.pdf")).contains("not allowed");
        assertThat(RecruiterSecurityRules.cvProblem(pdf(100), "a/b.pdf")).contains("not allowed");
        assertThat(RecruiterSecurityRules.cvProblem(pdf(100), "cv\n.pdf")).contains("not allowed");
        assertThat(RecruiterSecurityRules.cvProblem(pdf(100), "cv.exe")).contains("must end in .pdf");
        assertThat(RecruiterSecurityRules.cvProblem(pdf(100), "a".repeat(201) + ".pdf")).contains("too long");
    }

    @Test
    void onlyKnownInterviewResultsAreAccepted() {
        assertThat(RecruiterSecurityRules.outcomeProblem("PASSED", 80, "Strong")).isNull();
        assertThat(RecruiterSecurityRules.outcomeProblem("failed", null, null)).isNull();
        assertThat(RecruiterSecurityRules.outcomeProblem("NO_SHOW", null, null)).isNull();
        assertThat(RecruiterSecurityRules.outcomeProblem("MAYBE", null, null)).contains("Choose the result");
        assertThat(RecruiterSecurityRules.outcomeProblem(null, null, null)).contains("Choose the result");
        assertThat(RecruiterSecurityRules.outcomeProblem("PENDING", null, null)).contains("Choose the result");
    }

    @Test
    void theScoreAndNotesHaveLimits() {
        assertThat(RecruiterSecurityRules.outcomeProblem("PASSED", 0, null)).isNull();
        assertThat(RecruiterSecurityRules.outcomeProblem("PASSED", 100, null)).isNull();
        assertThat(RecruiterSecurityRules.outcomeProblem("PASSED", -1, null)).contains("between 0 and 100");
        assertThat(RecruiterSecurityRules.outcomeProblem("PASSED", 101, null)).contains("between 0 and 100");
        assertThat(RecruiterSecurityRules.outcomeProblem("PASSED", 50, "x".repeat(2001))).contains("too long");
    }

    @Test
    void aResultCanOnlyBeRecordedOnceOnAPendingInterviewOfALiveApplication() {
        assertThat(RecruiterSecurityRules.outcomeStateProblem("PENDING", true)).isNull();
        assertThat(RecruiterSecurityRules.outcomeStateProblem("PASSED", true)).contains("already has a result");
        assertThat(RecruiterSecurityRules.outcomeStateProblem("RESCHEDULED", true)).contains("already has a result or was rescheduled");
        assertThat(RecruiterSecurityRules.outcomeStateProblem("PENDING", false)).contains("no longer active");
    }
}
