package za.co.handyflow.platform.recruiter.domain.model;

import org.junit.jupiter.api.Test;

import java.time.LocalDate;

import static org.assertj.core.api.Assertions.assertThat;

class RecruiterPublicRulesTest {

    private static String form(String first, String last, String email, String phone, String loc, String li, String pf, String src, String ref) {
        return RecruiterPublicRules.applicationProblem(first, last, email, phone, loc, li, pf, src, ref);
    }

    @Test
    void aNormalFormIsAccepted() {
        assertThat(form("Thandi", "Nkosi", "Thandi.Nkosi@Example.co.za", "082 555 1001", "Johannesburg", "https://linkedin.com/in/thandi",
                "https://thandi.example/work", "LINKEDIN", null)).isNull();
        assertThat(form("Thandi", "Nkosi", "t@e.co", null, null, null, null, null, null)).isNull();
    }

    @Test
    void namesAreRequiredAndLimited() {
        assertThat(form("", "Nkosi", "t@e.co", null, null, null, null, null, null)).contains("first name");
        assertThat(form("Thandi", " ", "t@e.co", null, null, null, null, null, null)).contains("last name");
        assertThat(form("x".repeat(101), "Nkosi", "t@e.co", null, null, null, null, null, null)).contains("first name");
    }

    @Test
    void theEmailMustLookLikeOne() {
        assertThat(form("T", "N", null, null, null, null, null, null, null)).isEqualTo("Enter a valid email address.");
        assertThat(form("T", "N", "not-an-email", null, null, null, null, null, null)).isEqualTo("Enter a valid email address.");
        assertThat(form("T", "N", "a@b", null, null, null, null, null, null)).isEqualTo("Enter a valid email address.");
        assertThat(form("T", "N", "a b@c.co", null, null, null, null, null, null)).isEqualTo("Enter a valid email address.");
        assertThat(form("T", "N", "a@" + "b".repeat(260) + ".co", null, null, null, null, null, null)).isEqualTo("Enter a valid email address.");
    }

    @Test
    void emailIsKeptInLowerCaseWithoutSpaces() {
        assertThat(RecruiterPublicRules.normaliseEmail("  Thandi@Example.CO.za ")).isEqualTo("thandi@example.co.za");
        assertThat(RecruiterPublicRules.normaliseEmail(null)).isNull();
    }

    @Test
    void phoneAndLocationAreChecked() {
        assertThat(RecruiterPublicRules.profileProblem("+27 82 555 1001", "Cape Town", null, null)).isNull();
        assertThat(RecruiterPublicRules.profileProblem("call me", null, null, null)).contains("valid phone number");
        assertThat(RecruiterPublicRules.profileProblem("123", null, null, null)).contains("valid phone number");
        assertThat(RecruiterPublicRules.profileProblem(null, "x".repeat(151), null, null)).contains("location is too long");
        assertThat(RecruiterPublicRules.profileProblem(" ", " ", " ", " ")).isNull();
    }

    @Test
    void linksMustBeWebAddresses() {
        assertThat(RecruiterPublicRules.profileProblem(null, null, "javascript:alert(1)", null)).contains("LinkedIn link").contains("http");
        assertThat(RecruiterPublicRules.profileProblem(null, null, null, "ftp://files.example")).contains("Portfolio link").contains("http");
        assertThat(RecruiterPublicRules.profileProblem(null, null, "https://a.example/<script>", null)).contains("not allowed");
        assertThat(RecruiterPublicRules.profileProblem(null, null, "https://a.example/has space", null)).contains("not allowed");
        assertThat(RecruiterPublicRules.profileProblem(null, null, "https://a.example/" + "x".repeat(500), null)).contains("too long");
        assertThat(RecruiterPublicRules.profileProblem(null, null, "HTTPS://A.EXAMPLE/x", null)).isNull();
    }

    @Test
    void sourceAndReferrerAreLimited() {
        assertThat(form("T", "N", "t@e.co", null, null, null, null, "LINKEDIN_JOBS", "Mary")).isNull();
        assertThat(form("T", "N", "t@e.co", null, null, null, null, "bad source!", null)).isEqualTo("The source is not valid.");
        assertThat(form("T", "N", "t@e.co", null, null, null, null, "A".repeat(31), null)).isEqualTo("The source is not valid.");
        assertThat(form("T", "N", "t@e.co", null, null, null, null, null, "x".repeat(101))).contains("referrer");
    }

    @Test
    void theClosingDayItselfIsStillOpen() {
        LocalDate day = LocalDate.of(2026, 10, 31);
        assertThat(RecruiterPublicRules.closedProblem(null, day)).isNull();
        assertThat(RecruiterPublicRules.closedProblem(day, day)).isNull();
        assertThat(RecruiterPublicRules.closedProblem(day, day.minusDays(5))).isNull();
        assertThat(RecruiterPublicRules.closedProblem(day, day.plusDays(1))).isEqualTo("The closing date for this position has passed.");
    }

    @Test
    void aHiredOrClosedApplicationCannotBeWithdrawn() {
        assertThat(RecruiterPublicRules.withdrawProblem("APPLIED")).isNull();
        assertThat(RecruiterPublicRules.withdrawProblem("INTERVIEW")).isNull();
        assertThat(RecruiterPublicRules.withdrawProblem("OFFER")).isNull();
        assertThat(RecruiterPublicRules.withdrawProblem("HIRED")).contains("led to a hire");
        assertThat(RecruiterPublicRules.withdrawProblem("REJECTED")).contains("already closed");
        assertThat(RecruiterPublicRules.withdrawProblem("WITHDRAWN")).contains("already closed");
    }
}
