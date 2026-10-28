package za.co.handyflow.platform.recruiter.domain.model;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class RecruiterConsentRulesTest {

    @Test
    void theStatementNamesTheCompanysMonths() {
        assertThat(RecruiterConsentRules.statementFor(12)).contains("removed 12 months after my last activity unless I am hired");
        assertThat(RecruiterConsentRules.statementFor(1)).contains("removed 1 month after");
    }

    @Test
    void withoutAUsableNumberItIsTheGeneralStatement() {
        assertThat(RecruiterConsentRules.statementFor(null)).isEqualTo(RecruiterConsentRules.STATEMENT);
        assertThat(RecruiterConsentRules.statementFor(0)).isEqualTo(RecruiterConsentRules.STATEMENT);
    }

    @Test
    void noPrivacyLinkIsFine() {
        assertThat(RecruiterConsentRules.privacyUrlProblem(null)).isNull();
        assertThat(RecruiterConsentRules.privacyUrlProblem("  ")).isNull();
        assertThat(RecruiterConsentRules.normaliseUrl("  ")).isNull();
    }

    @Test
    void aWebAddressIsAccepted() {
        assertThat(RecruiterConsentRules.privacyUrlProblem("https://acme.co.za/privacy")).isNull();
        assertThat(RecruiterConsentRules.privacyUrlProblem(" http://acme.co.za ")).isNull();
        assertThat(RecruiterConsentRules.privacyUrlProblem("HTTPS://acme.co.za/a?b=1#c")).isNull();
        assertThat(RecruiterConsentRules.normaliseUrl(" https://acme.co.za/privacy ")).isEqualTo("https://acme.co.za/privacy");
    }

    @Test
    void anythingElseIsRefused() {
        assertThat(RecruiterConsentRules.privacyUrlProblem("javascript:alert(1)")).contains("https://");
        assertThat(RecruiterConsentRules.privacyUrlProblem("data:text/html,x")).contains("https://");
        assertThat(RecruiterConsentRules.privacyUrlProblem("ftp://acme.co.za")).contains("https://");
        assertThat(RecruiterConsentRules.privacyUrlProblem("acme.co.za/privacy")).contains("https://");
        assertThat(RecruiterConsentRules.privacyUrlProblem("https://")).contains("web address");
        assertThat(RecruiterConsentRules.privacyUrlProblem("https:///path")).contains("web address");
        assertThat(RecruiterConsentRules.privacyUrlProblem("https://user@acme.co.za")).contains("web address");
        assertThat(RecruiterConsentRules.privacyUrlProblem("https://acme .co.za")).contains("web address");
        assertThat(RecruiterConsentRules.privacyUrlProblem("https://acme.co.za/" + "x".repeat(500))).contains("too long");
    }
}
