package za.co.handyflow.platform.hr.domain.model;

import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class BenefitRulesTest {

    private static BigDecimal d(String s) { return new BigDecimal(s); }
    private static LocalDate day(String s) { return LocalDate.parse(s); }
    private static BenefitRules.Line line(String kind, String amount, String from, String to) {
        return new BenefitRules.Line(kind, d(amount), day(from), to == null ? null : day(to));
    }
    private static String add(String kind, String amount, String from, String to, String description, String reason, List<BenefitRules.Line> existing) {
        return BenefitRules.addProblem(kind, amount == null ? null : d(amount), from == null ? null : day(from), to == null ? null : day(to), description, reason,
                existing, day("2024-01-01"));
    }

    @Test
    void aLineAppliesFromItsFirstDayToItsLast() {
        List<BenefitRules.Line> lines = List.of(line("EMPLOYER_MEDICAL", "2500", "2026-03-01", "2026-08-31"));

        assertThat(BenefitRules.on(lines, day("2026-02-28")).medical().signum()).isZero();
        assertThat(BenefitRules.on(lines, day("2026-03-01")).medical().compareTo(d("2500"))).isZero();
        assertThat(BenefitRules.on(lines, day("2026-08-31")).medical().compareTo(d("2500"))).isZero();
        assertThat(BenefitRules.on(lines, day("2026-09-01")).medical().signum()).isZero();
    }

    @Test
    void kindsAreKeptApartAndAnOpenLineRunsOn() {
        List<BenefitRules.Line> lines = List.of(line("EMPLOYER_MEDICAL", "2500", "2026-01-01", null), line("EMPLOYER_RETIREMENT", "1800", "2026-01-01", null),
                line("OTHER_FRINGE", "700", "2026-01-01", null), line("OTHER_FRINGE", "300", "2026-01-01", null));
        BenefitRules.Monthly m = BenefitRules.on(lines, day("2027-05-31"));

        assertThat(m.medical().compareTo(d("2500"))).isZero();
        assertThat(m.retirement().compareTo(d("1800"))).isZero();
        assertThat(m.other().compareTo(d("1000"))).isZero();
        assertThat(m.isEmpty()).isFalse();
        assertThat(BenefitRules.on(List.of(), day("2026-05-31")).isEmpty()).isTrue();
    }

    @Test
    void aLineMustBeComplete() {
        assertThat(add("EMPLOYER_MEDICAL", "2500", "2026-03-01", null, null, "Joined the scheme", List.of())).isNull();
        assertThat(add("CAR", "2500", "2026-03-01", null, null, "x", List.of())).contains("kind");
        assertThat(add("EMPLOYER_MEDICAL", null, "2026-03-01", null, null, "x", List.of())).contains("monthly amount");
        assertThat(add("EMPLOYER_MEDICAL", "0", "2026-03-01", null, null, "x", List.of())).contains("monthly amount");
        assertThat(add("EMPLOYER_MEDICAL", "10.123", "2026-03-01", null, null, "x", List.of())).contains("two decimals");
        assertThat(add("EMPLOYER_MEDICAL", "2500", null, null, null, "x", List.of())).contains("which date");
        assertThat(add("EMPLOYER_MEDICAL", "2500", "2026-03-01", "2026-02-01", null, "x", List.of())).contains("end date");
        assertThat(add("EMPLOYER_MEDICAL", "2500", "2026-03-01", null, null, " ", List.of())).contains("reason");
        assertThat(add("EMPLOYER_MEDICAL", "2500", "2023-12-31", null, null, "x", List.of())).contains("before the employee");
    }

    @Test
    void otherBenefitsNeedADescription() {
        assertThat(add("OTHER_FRINGE", "700", "2026-03-01", null, " ", "Car", List.of())).contains("Describe");
        assertThat(add("OTHER_FRINGE", "700", "2026-03-01", null, "Company car", "Car", List.of())).isNull();
    }

    @Test
    void medicalAndRetirementCannotOverlapButOthersMay() {
        List<BenefitRules.Line> existing = List.of(line("EMPLOYER_MEDICAL", "2500", "2026-01-01", null), line("OTHER_FRINGE", "700", "2026-01-01", null));

        assertThat(add("EMPLOYER_MEDICAL", "2800", "2026-06-01", null, null, "Raise", existing)).contains("End it first");
        assertThat(add("EMPLOYER_RETIREMENT", "1800", "2026-06-01", null, null, "New fund", existing)).isNull();
        assertThat(add("OTHER_FRINGE", "300", "2026-06-01", null, "Phone", "Phone", existing)).isNull();
        List<BenefitRules.Line> ended = List.of(line("EMPLOYER_MEDICAL", "2500", "2026-01-01", "2026-05-31"));
        assertThat(add("EMPLOYER_MEDICAL", "2800", "2026-06-01", null, null, "Raise", ended)).isNull();
        assertThat(add("EMPLOYER_MEDICAL", "2800", "2026-05-31", null, null, "Raise", ended)).contains("End it first");
    }

    @Test
    void aLineCanBeEndedOnceAndNotBeforeItStarted() {
        assertThat(BenefitRules.endProblem(day("2026-03-01"), null, day("2026-06-30"))).isNull();
        assertThat(BenefitRules.endProblem(day("2026-03-01"), null, null)).contains("last day");
        assertThat(BenefitRules.endProblem(day("2026-03-01"), null, day("2026-02-28"))).contains("before the benefit started");
        assertThat(BenefitRules.endProblem(day("2026-03-01"), day("2026-05-31"), day("2026-06-30"))).contains("already been ended");
    }
}
