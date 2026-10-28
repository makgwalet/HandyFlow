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
}
