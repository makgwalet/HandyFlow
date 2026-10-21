package za.co.handyflow.platform.hr.domain.model;

import org.junit.jupiter.api.Test;

import java.time.LocalDate;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;

class LetterRulesTest {

    @Test
    void mergeFieldsAreFoundInOrderAndOnlyOnce() {
        assertThat(LetterRules.fieldsIn("Dear {{employee.firstName}}, {{ employee.name }} and {{employee.firstName}}"))
                .containsExactly("employee.firstName", "employee.name");
        assertThat(LetterRules.fieldsIn(null)).isEmpty();
    }

    @Test
    void aTemplateOnlyUsesFieldsThatExist() {
        assertThat(LetterRules.templateProblem("Hello", "GENERAL", "Hi {{employee.name}}", "Dear {{employee.firstName}}")).isNull();
        assertThat(LetterRules.templateProblem("Hello", "GENERAL", "Hi", "Your salary is {{employee.salary}}")).contains("employee.salary");
        assertThat(LetterRules.templateProblem("Hello", "GENERAL", "Hi {{employee.idNumber}}", "x")).contains("employee.idNumber");
    }

    @Test
    void sensitiveEmployeeDetailsAreNotMergeFields() {
        Set<String> keys = new HashSet<>();
        LetterRules.FIELDS.forEach(f -> keys.add(f.key()));
        for (String k : keys) assertThat(k.contains("salary") || k.contains("idNumber") || k.contains("tax") || k.contains("bank")).as(k).isFalse();
    }

    @Test
    void aTemplateMustBeCompleteAndWrittenCorrectly() {
        assertThat(LetterRules.templateProblem(" ", "GENERAL", "s", "b")).contains("name");
        assertThat(LetterRules.templateProblem("n", "OTHER", "s", "b")).contains("category");
        assertThat(LetterRules.templateProblem("n", "GENERAL", " ", "b")).contains("subject");
        assertThat(LetterRules.templateProblem("n", "GENERAL", "s", " ")).contains("wording");
        assertThat(LetterRules.templateProblem("n", "GENERAL", "s", "Dear {{employee.name")).contains("not written correctly");
        assertThat(LetterRules.templateProblem("n", "GENERAL", "s", "Dear }}employee.name{{")).contains("not written correctly");
        assertThat(LetterRules.templateProblem("n", "GENERAL", "s", "Fill in [[this]]")).contains("[[ ]]");
        assertThat(LetterRules.templateProblem("n", "GENERAL", "s".repeat(201), "b")).contains("too long");
    }

    @Test
    void renderingFillsFieldsAndMarksGaps() {
        LetterRules.Rendered r = LetterRules.render("Dear {{employee.firstName}}, your hearing is on {{hearing.date}} at {{hearing.time}}.",
                Map.of("employee.firstName", "Thandi", "hearing.date", "5 November 2026"));
        assertThat(r.text()).isEqualTo("Dear Thandi, your hearing is on 5 November 2026 at [[Hearing or meeting time]].");
        assertThat(r.missing()).containsExactly("hearing.time");
        assertThat(LetterRules.gaps(r.text())).containsExactly("Hearing or meeting time");
    }

    @Test
    void aBlankValueCountsAsAGapAndTheSameGapIsListedOnce() {
        LetterRules.Rendered r = LetterRules.render("{{manual.details}} / {{manual.details}}", Map.of("manual.details", "  "));
        assertThat(r.missing()).containsExactly("manual.details");
        assertThat(LetterRules.gaps(r.text())).containsExactly("Details (type in)");
    }

    @Test
    void textThatLooksLikeAGapOrADollarSignIsKeptAsIs() {
        LetterRules.Rendered r = LetterRules.render("Amount: {{manual.remuneration}}", Map.of("manual.remuneration", "R10 000 ($1 \\ 2) [[x]]"));
        assertThat(r.text()).startsWith("Amount: R10 000 ($1 \\ 2)");
        assertThat(LetterRules.gaps(r.text())).isEmpty();
    }

    @Test
    void aDraftIsFinalWordingWithNoMergeFieldsLeft() {
        assertThat(LetterRules.draftProblem("Subject", "Body")).isNull();
        assertThat(LetterRules.draftProblem("Subject", "Dear {{employee.name}}")).contains("merge fields");
        assertThat(LetterRules.draftProblem("", "Body")).contains("subject");
        assertThat(LetterRules.draftProblem("Subject", " ")).contains("wording");
    }

    @Test
    void aLetterWithGapsCannotBeIssued() {
        assertThat(LetterRules.issueProblem("DRAFT", "S", "Hearing at [[Venue]] and [[Chair]]")).contains("Venue").contains("Chair");
        assertThat(LetterRules.issueProblem("DRAFT", "Notice [[Case number]]", "b")).contains("Case number");
        assertThat(LetterRules.issueProblem("DRAFT", "S", "All filled in")).isNull();
        assertThat(LetterRules.issueProblem("ISSUED", "S", "b")).contains("draft");
        assertThat(LetterRules.issueProblem("VOID", "S", "b")).contains("draft");
    }

    @Test
    void voidingNeedsAReasonAndHappensOnce() {
        assertThat(LetterRules.voidProblem("ISSUED", "Wrong hearing date")).isNull();
        assertThat(LetterRules.voidProblem("DRAFT", "Not needed")).isNull();
        assertThat(LetterRules.voidProblem("ISSUED", " ")).contains("reason");
        assertThat(LetterRules.voidProblem("VOID", "again")).contains("already void");
    }

    @Test
    void onlyAnIssuedLetterThatAsksForItCanBeAcknowledgedOnce() {
        assertThat(LetterRules.mayAcknowledge("ISSUED", true, null)).isTrue();
        assertThat(LetterRules.mayAcknowledge("ISSUED", false, null)).isFalse();
        assertThat(LetterRules.mayAcknowledge("ISSUED", true, "already")).isFalse();
        assertThat(LetterRules.mayAcknowledge("DRAFT", true, null)).isFalse();
        assertThat(LetterRules.mayAcknowledge("VOID", true, null)).isFalse();
    }

    @Test
    void theButtonsFollowTheState() {
        assertThat(LetterRules.actions("DRAFT")).containsExactly("EDIT", "ISSUE", "VOID");
        assertThat(LetterRules.actions("ISSUED")).containsExactly("DOWNLOAD", "VOID");
        assertThat(LetterRules.actions("VOID")).isEmpty();
    }

    @Test
    void permissionsAreChecked() {
        assertThat(LetterRules.canRead(Set.of("HR_LETTER_READ"))).isTrue();
        assertThat(LetterRules.canRead(Set.of("HR_LETTER_ISSUE"))).isFalse();
        assertThat(LetterRules.canManage(Set.of("HR_LETTER_MANAGE"))).isTrue();
        assertThat(LetterRules.canIssue(Set.of("HR_MANAGE"))).isFalse();
        assertThat(LetterRules.canIssue(null)).isFalse();
    }

    @Test
    void datesAndListsAreWordedTheSameOnEveryServer() {
        assertThat(LetterRules.longDate(LocalDate.of(2026, 10, 5))).isEqualTo("5 October 2026");
        assertThat(LetterRules.longDate(null)).isNull();
        assertThat(LetterRules.words("FINAL_WRITTEN_WARNING")).isEqualTo("Final written warning");
        assertThat(LetterRules.words(" ")).isNull();
        assertThat(LetterRules.numbered(List.of("Late on 1 Oct", "Late on 2 Oct"))).isEqualTo("1. Late on 1 Oct\n2. Late on 2 Oct");
        assertThat(LetterRules.numbered(List.of())).isNull();
    }

    @Test
    void aCustomTemplateGetsAShortUniqueCode() {
        assertThat(LetterRules.codeFrom("Letter of good standing", Set.of())).isEqualTo("LETTER_OF_GOOD_STANDING");
        assertThat(LetterRules.codeFrom("Letter of good standing", Set.of("LETTER_OF_GOOD_STANDING"))).isEqualTo("LETTER_OF_GOOD_STANDING_2");
        assertThat(LetterRules.codeFrom("!!!", Set.of())).isEqualTo("LETTER");
        assertThat(LetterRules.codeFrom("a".repeat(60), Set.of()).length()).isLessThanOrEqualTo(30);
    }
}
