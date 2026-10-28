package za.co.handyflow.platform.hr.domain.model;

import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

class OnboardingRulesTest {

    private static final LocalDate START = LocalDate.of(2026, 11, 2);
    private static final LocalDate TODAY = LocalDate.of(2026, 11, 10);

    private static OnboardingRules.Facts none() { return new OnboardingRules.Facts(false, false, false, false, false, false, false); }

    @Test
    void theStandardListCoversTheEssentials() {
        var specs = OnboardingRules.defaults();
        assertThat(specs.size() >= 15).isTrue();
        assertThat(specs.stream().anyMatch(s -> s.title().startsWith("Written particulars"))).isTrue();
        assertThat(specs.stream().anyMatch(s -> s.title().contains("UIF"))).isTrue();
        assertThat(specs.stream().anyMatch(s -> s.title().contains("POPIA"))).isTrue();
        assertThat(specs.stream().anyMatch(s -> s.title().startsWith("Probation review") && s.dueBasis().equals("PROBATION_END"))).isTrue();
    }

    @Test
    void everyDefaultIsValidAndEveryCheckIsKnown() {
        for (var s : OnboardingRules.defaults()) {
            assertThat(OnboardingRules.templateProblem(s.title(), s.category(), s.dueBasis(), s.check(), s.dueOffsetDays())).isNull();
        }
    }

    @Test
    void duesCountFromTheStartOrFromTheEndOfProbation() {
        LocalDate end = OnboardingRules.probationEnd(START, 3);
        assertThat(end).isEqualTo(LocalDate.of(2027, 2, 2));
        assertThat(OnboardingRules.dueDate(START, end, 7, "START")).isEqualTo(LocalDate.of(2026, 11, 9));
        assertThat(OnboardingRules.dueDate(START, end, -7, "PROBATION_END")).isEqualTo(LocalDate.of(2027, 1, 26));
        assertThat(OnboardingRules.dueDate(START, null, 3, "PROBATION_END")).isEqualTo(LocalDate.of(2026, 11, 5));
    }

    @Test
    void probationMustEndAfterTheStartAndWithinAYear() {
        assertThat(OnboardingRules.probationProblem(START, LocalDate.of(2027, 2, 2))).isNull();
        assertThat(OnboardingRules.probationProblem(START, null)).contains("Choose");
        assertThat(OnboardingRules.probationProblem(START, START)).contains("after the start");
        assertThat(OnboardingRules.probationProblem(START, LocalDate.of(2028, 1, 1))).contains("typing mistake");
    }

    @Test
    void factsComeFromTheEmployeeRecord() {
        var f = OnboardingRules.facts("8001015009087", "0123456789", "FNB", "62001234567", "250655", new BigDecimal("30000"),
                UUID.randomUUID(), null, "Sipho", "0821234567", TODAY);
        assertThat(f.idNumber()).isTrue();
        assertThat(f.taxNumber()).isTrue();
        assertThat(f.bankDetails()).isTrue();
        assertThat(f.salary()).isTrue();
        assertThat(f.manager()).isTrue();
        assertThat(f.login()).isFalse();
        assertThat(f.emergencyContact()).isTrue();
    }

    @Test
    void invalidOrMissingDataDoesNotPassACheck() {
        var f = OnboardingRules.facts("1234", "12", "FNB", "ab", null, BigDecimal.ZERO, null, null, "Sipho", null, TODAY);
        assertThat(f.idNumber()).isFalse();
        assertThat(f.taxNumber()).isFalse();
        assertThat(f.bankDetails()).isFalse();
        assertThat(f.salary()).isFalse();
        assertThat(f.emergencyContact()).isFalse();
        assertThat(OnboardingRules.facts(null, null, null, null, null, null, null, null, null, null, TODAY).idNumber()).isFalse();
    }

    @Test
    void aCheckedTaskTicksItselfOnceTheRecordIsFixed() {
        var ok = new OnboardingRules.Facts(true, false, false, false, false, false, false);
        assertThat(OnboardingRules.effective("OPEN", "ID_NUMBER", ok)).isEqualTo("DONE");
        assertThat(OnboardingRules.effective("OPEN", "ID_NUMBER", none())).isEqualTo("OPEN");
        assertThat(OnboardingRules.effective("OPEN", null, ok)).isEqualTo("OPEN");
    }

    @Test
    void whatAPersonRecordedIsNotOverriddenByTheRecord() {
        assertThat(OnboardingRules.effective("WAIVED", "ID_NUMBER", none())).isEqualTo("WAIVED");
        assertThat(OnboardingRules.effective("DONE", "ID_NUMBER", none())).isEqualTo("DONE");
    }

    @Test
    void aCheckedTaskCannotBeTickedByHand() {
        assertThat(OnboardingRules.completeProblem("BANK_DETAILS", "OPEN", none())).contains("checked against");
        assertThat(OnboardingRules.completeProblem(null, "OPEN", none())).isNull();
        assertThat(OnboardingRules.completeProblem(null, "DONE", none())).contains("already done");
    }

    @Test
    void waivingNeedsAReasonAndAnOpenTask() {
        assertThat(OnboardingRules.waiveProblem("OPEN", " ")).contains("reason");
        assertThat(OnboardingRules.waiveProblem("OPEN", "Fixed-term, no fund")).isNull();
        assertThat(OnboardingRules.waiveProblem("DONE", "x")).contains("already");
        assertThat(OnboardingRules.waiveProblem("OPEN", "x".repeat(501))).contains("500");
    }

    @Test
    void onboardingCannotFinishWhileRequiredTasksAreOpen() {
        var tasks = List.of(
                new OnboardingRules.TaskLite("Contract", true, "OPEN"),
                new OnboardingRules.TaskLite("Equipment", false, "OPEN"),
                new OnboardingRules.TaskLite("UIF", true, "DONE"),
                new OnboardingRules.TaskLite("Fund", true, "WAIVED"));
        var problems = OnboardingRules.finishProblems(tasks);
        assertThat(problems).hasSize(1);
        assertThat(problems.get(0)).contains("Contract");
        assertThat(OnboardingRules.finishProblems(List.of(new OnboardingRules.TaskLite("Equipment", false, "OPEN")))).isEmpty();
    }

    @Test
    void progressCountsFinishedOpenRequiredAndOverdue() {
        var items = List.of(
                new OnboardingRules.Item(true, "DONE", START),
                new OnboardingRules.Item(true, "OPEN", LocalDate.of(2026, 11, 5)),
                new OnboardingRules.Item(false, "OPEN", LocalDate.of(2026, 12, 1)),
                new OnboardingRules.Item(true, "WAIVED", START));
        var p = OnboardingRules.progress(items, TODAY);
        assertThat(p.total()).isEqualTo(4);
        assertThat(p.finished()).isEqualTo(2);
        assertThat(p.requiredOpen()).isEqualTo(1);
        assertThat(p.overdue()).isEqualTo(1);
        assertThat(p.percent()).isEqualTo(50);
        assertThat(OnboardingRules.progress(List.of(), TODAY).percent()).isEqualTo(100);
    }

    @Test
    void doneTasksAreNeverOverdue() {
        assertThat(OnboardingRules.overdue("DONE", START, TODAY)).isFalse();
        assertThat(OnboardingRules.overdue("OPEN", null, TODAY)).isFalse();
        assertThat(OnboardingRules.overdue("OPEN", TODAY, TODAY)).isFalse();
        assertThat(OnboardingRules.overdue("OPEN", TODAY.minusDays(1), TODAY)).isTrue();
    }

    @Test
    void templatesAreChecked() {
        assertThat(OnboardingRules.templateProblem(" ", "ACCESS", "START", null, 0)).contains("title");
        assertThat(OnboardingRules.templateProblem("x", "NOPE", "START", null, 0)).contains("category");
        assertThat(OnboardingRules.templateProblem("x", "ACCESS", "LATER", null, 0)).contains("due date");
        assertThat(OnboardingRules.templateProblem("x", "ACCESS", "START", "WHATEVER", 0)).contains("check");
        assertThat(OnboardingRules.templateProblem("x", "ACCESS", "START", null, 4000)).contains("typing mistake");
        assertThat(OnboardingRules.templateProblem("x", "ACCESS", "START", "MANAGER", -3)).isNull();
    }

    @Test
    void aPassportWithItsCountryAlsoSatisfiesTheIdentityCheck() {
        assertThat(OnboardingRules.facts(null, "A1234567", "ZW", null, null, null, null, null, null, null, null, null, TODAY).idNumber()).isTrue();
        assertThat(OnboardingRules.facts(null, "A1234567", null, null, null, null, null, null, null, null, null, null, TODAY).idNumber()).isFalse();
        assertThat(OnboardingRules.facts("1234", "A1234567", "ZW", null, null, null, null, null, null, null, null, null, TODAY).idNumber()).isTrue();
    }
}
