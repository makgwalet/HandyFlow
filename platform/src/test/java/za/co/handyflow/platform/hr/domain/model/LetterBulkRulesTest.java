package za.co.handyflow.platform.hr.domain.model;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class LetterBulkRulesTest {

    @Test
    void aGeneralTemplateWithEmployeeAndCompanyFieldsCanBeMadeForMany() {
        assertThat(LetterRules.bulkProblem("GENERAL", "Policy update for {{employee.firstName}}", "Dear {{employee.firstName}}, from {{company.name}} on {{today}}.", 10)).isNull();
        assertThat(LetterRules.bulkProblem("EMPLOYMENT", "Confirmation", "{{employee.name}} {{manual.remuneration}}", 1)).isNull();
    }

    @Test
    void aTemplateAboutACaseCannotBeMadeForMany() {
        assertThat(LetterRules.bulkProblem("DISCIPLINARY", "S", "Dear {{employee.firstName}}", 5)).contains("case");
        assertThat(LetterRules.bulkProblem("GENERAL", "S", "Hearing on {{hearing.date}}", 5)).contains("Hearing or meeting date");
        assertThat(LetterRules.bulkProblem("GENERAL", "Outcome {{outcome}}", "Dear {{employee.firstName}}", 5)).contains("Outcome");
        assertThat(LetterRules.bulkProblem("GENERAL", "S", "Suspended from {{suspension.from}}", 5)).contains("Suspension from");
    }

    @Test
    void theNumberOfPeopleIsBounded() {
        assertThat(LetterRules.bulkProblem("GENERAL", "S", "B", 0)).contains("at least one");
        assertThat(LetterRules.bulkProblem("GENERAL", "S", "B", LetterRules.BULK_MAX)).isNull();
        assertThat(LetterRules.bulkProblem("GENERAL", "S", "B", LetterRules.BULK_MAX + 1)).contains("At most");
        assertThat(LetterRules.bulkIssueProblem(0)).contains("at least one");
        assertThat(LetterRules.bulkIssueProblem(LetterRules.BULK_ISSUE_MAX)).isNull();
        assertThat(LetterRules.bulkIssueProblem(LetterRules.BULK_ISSUE_MAX + 1)).contains("At most");
    }

    @Test
    void theIssuedMessageGivesTheReferenceAndNothingOfTheContent() {
        String m = LetterRules.issuedMessage("LT-2026-0007", true);
        assertThat(m).contains("LT-2026-0007").contains("confirm that you received it");
        assertThat(LetterRules.issuedMessage("LT-2026-0008", false)).doesNotContain("confirm");
    }
}
