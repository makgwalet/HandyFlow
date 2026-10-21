package za.co.handyflow.platform.hr.domain.model;

import org.junit.jupiter.api.Test;

import java.util.HashSet;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;

class LetterDefaultsTest {

    @Test
    void everyStandardTemplateIsValid() {
        for (LetterDefaults.Template t : LetterDefaults.ALL)
            assertThat(LetterRules.templateProblem(t.name(), t.category(), t.subject(), t.body())).as(t.code()).isNull();
    }

    @Test
    void codesAreUniqueAndFitTheColumn() {
        Set<String> seen = new HashSet<>();
        for (LetterDefaults.Template t : LetterDefaults.ALL) {
            assertThat(seen.add(t.code())).as("duplicate " + t.code()).isTrue();
            assertThat(t.code().length()).isLessThanOrEqualTo(40);
            assertThat(t.name().length()).isLessThanOrEqualTo(120);
            assertThat(t.subject().length()).isLessThanOrEqualTo(200);
        }
    }

    @Test
    void theNoticeOfHearingCarriesWhatTheCodeExpects() {
        LetterDefaults.Template t = LetterDefaults.byCode("NOTICE_OF_HEARING").orElseThrow();
        assertThat(t.requiresAck()).isTrue();
        assertThat(LetterRules.fieldsIn(t.body())).contains("hearing.date", "hearing.time", "hearing.venue", "hearing.chair", "charges", "hearing.noticeHours");
    }

    @Test
    void aDismissalLetterNamesTheCcmaDeadlineAndTheAppeal() {
        LetterDefaults.Template t = LetterDefaults.byCode("DISMISSAL_LETTER").orElseThrow();
        assertThat(LetterRules.fieldsIn(t.body())).contains("ccma.deadline", "appeal.deadline", "outcome.reasons");
        assertThat(t.body()).contains("30 days");
    }

    @Test
    void theCertificateOfServiceCoversEveryItemSection42Lists() {
        LetterDefaults.Template t = LetterDefaults.byCode("CERTIFICATE_OF_SERVICE").orElseThrow();
        assertThat(LetterRules.fieldsIn(t.body())).contains("employee.name", "company.name", "manual.address", "manual.sectoral", "employee.startDate",
                "employee.endDate", "employee.jobTitle", "manual.remuneration", "manual.reason");
    }

    @Test
    void lettersThatTellTheEmployeeSomethingImportantAskForConfirmation() {
        for (String code : new String[]{"NOTICE_OF_HEARING", "NOTICE_OF_MEETING", "SUSPENSION_LETTER", "WARNING_LETTER", "DISMISSAL_LETTER",
                "GRIEVANCE_OUTCOME", "INCAPACITY_OUTCOME", "APPEAL_OUTCOME"})
            assertThat(LetterDefaults.byCode(code).orElseThrow().requiresAck()).as(code).isTrue();
        assertThat(LetterDefaults.byCode("EMPLOYMENT_CONFIRMATION").orElseThrow().requiresAck()).isFalse();
    }

    @Test
    void anUnknownCodeIsNotAStandardTemplate() {
        assertThat(LetterDefaults.byCode("MY_OWN_LETTER")).isEmpty();
    }
}
