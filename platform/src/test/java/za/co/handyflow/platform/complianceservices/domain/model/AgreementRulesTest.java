package za.co.handyflow.platform.complianceservices.domain.model;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class AgreementRulesTest {

    private static final LocalDate TODAY = LocalDate.of(2027, 3, 1);

    @Test
    @DisplayName("A one-off agreement is billed once, on the first billing date")
    void once() {
        assertThat(AgreementRules.schedule(LocalDate.of(2027, 4, 1), null, "ONCE")).containsExactly(LocalDate.of(2027, 4, 1));
    }

    @Test
    @DisplayName("Monthly billing stays on the same day, and a 31st lands on the last day of a short month then returns")
    void monthlyAnchors() {
        List<LocalDate> d = AgreementRules.schedule(LocalDate.of(2027, 1, 31), LocalDate.of(2027, 4, 30), "MONTHLY");
        assertThat(d).containsExactly(LocalDate.of(2027, 1, 31), LocalDate.of(2027, 2, 28), LocalDate.of(2027, 3, 31), LocalDate.of(2027, 4, 30));
    }

    @Test
    @DisplayName("Quarterly and annual steps")
    void steps() {
        assertThat(AgreementRules.schedule(LocalDate.of(2027, 1, 1), LocalDate.of(2027, 12, 31), "QUARTERLY")).hasSize(4);
        assertThat(AgreementRules.schedule(LocalDate.of(2027, 1, 1), LocalDate.of(2029, 12, 31), "ANNUALLY")).hasSize(3);
    }

    @Test
    @DisplayName("An open-ended agreement shows a capped preview")
    void openCapped() {
        assertThat(AgreementRules.schedule(TODAY, null, "MONTHLY")).hasSize(AgreementRules.MAX_OPEN_DATES);
    }

    @Test
    @DisplayName("A first billing date after the end gives no dates")
    void firstAfterEnd() {
        assertThat(AgreementRules.schedule(LocalDate.of(2028, 1, 1), LocalDate.of(2027, 12, 31), "MONTHLY")).isEmpty();
    }

    @Test
    @DisplayName("Contract value is the amount times the dates, and only when the term has an end")
    void contractValue() {
        assertThat(AgreementRules.contractValue(LocalDate.of(2027, 1, 1), LocalDate.of(2027, 12, 31), "MONTHLY", new BigDecimal("1500")))
                .isEqualByComparingTo("18000.00");
        assertThat(AgreementRules.contractValue(LocalDate.of(2027, 1, 1), null, "MONTHLY", new BigDecimal("1500"))).isNull();
        assertThat(AgreementRules.contractValue(TODAY, null, "ONCE", new BigDecimal("900"))).isNull();
    }

    @Test
    @DisplayName("Next billing is the first date on or after today")
    void next() {
        assertThat(AgreementRules.nextBilling(LocalDate.of(2027, 1, 1), LocalDate.of(2027, 12, 31), "MONTHLY", TODAY)).isEqualTo(TODAY);
        assertThat(AgreementRules.nextBilling(LocalDate.of(2027, 1, 1), LocalDate.of(2027, 2, 1), "MONTHLY", TODAY)).isNull();
    }

    @Test
    @DisplayName("Term checks: order of dates and a ten-year limit")
    void term() {
        assertThat(AgreementRules.termProblem(null, null)).contains("start date");
        assertThat(AgreementRules.termProblem(TODAY, TODAY.minusDays(1))).contains("before the start");
        assertThat(AgreementRules.termProblem(TODAY, TODAY.plusYears(10))).isNull();
        assertThat(AgreementRules.termProblem(TODAY, TODAY.plusYears(10).plusDays(1))).contains("10 years");
        assertThat(AgreementRules.termProblem(TODAY, null)).isNull();
    }

    @Test
    @DisplayName("Billing checks: frequency, amount and dates")
    void billing() {
        assertThat(AgreementRules.billingProblem("WEEKLY", BigDecimal.TEN, TODAY, TODAY, null)).contains("frequency");
        assertThat(AgreementRules.billingProblem("MONTHLY", BigDecimal.ZERO, TODAY, TODAY, null)).contains("more than zero");
        assertThat(AgreementRules.billingProblem("MONTHLY", new BigDecimal("10.005"), TODAY, TODAY, null)).contains("two decimal");
        assertThat(AgreementRules.billingProblem("MONTHLY", BigDecimal.TEN, null, TODAY, null)).contains("first billing");
        assertThat(AgreementRules.billingProblem("MONTHLY", BigDecimal.TEN, TODAY.minusDays(1), TODAY, null)).contains("before the start");
        assertThat(AgreementRules.billingProblem("MONTHLY", BigDecimal.TEN, TODAY.plusDays(40), TODAY, TODAY.plusDays(30))).contains("after the end");
        assertThat(AgreementRules.billingProblem("MONTHLY", new BigDecimal("1500.50"), TODAY, TODAY, TODAY.plusMonths(6))).isNull();
    }

    @Test
    @DisplayName("Notice is 0 to 365 days")
    void notice() {
        assertThat(AgreementRules.noticeProblem(-1)).isNotNull();
        assertThat(AgreementRules.noticeProblem(366)).isNotNull();
        assertThat(AgreementRules.noticeProblem(0)).isNull();
        assertThat(AgreementRules.noticeProblem(365)).isNull();
    }

    @Test
    @DisplayName("A contract that differs from the quote says so; one that matches says nothing")
    void comparison() {
        assertThat(AgreementRules.comparisonNote(new BigDecimal("18000"), new BigDecimal("18000.00"))).isNull();
        assertThat(AgreementRules.comparisonNote(new BigDecimal("18500"), new BigDecimal("18000"))).contains("500.00 more");
        assertThat(AgreementRules.comparisonNote(new BigDecimal("17000"), new BigDecimal("18000"))).contains("1000.00 less");
        assertThat(AgreementRules.comparisonNote(null, new BigDecimal("18000"))).isNull();
    }

    @Test
    @DisplayName("Attention: ended term, notice window, and nothing for quiet agreements")
    void attention() {
        LocalDate end = TODAY.plusDays(60);
        assertThat(AgreementRules.attention("ACTIVE", end, false, 30, TODAY)).isNull();
        assertThat(AgreementRules.attention("ACTIVE", end, true, 30, end.minusDays(30))).contains("Notice to stop the renewal");
        assertThat(AgreementRules.attention("ACTIVE", end, false, 30, end.minusDays(10))).contains("term ends");
        assertThat(AgreementRules.attention("ACTIVE", TODAY.minusDays(1), false, 30, TODAY)).contains("term ended");
        assertThat(AgreementRules.attention("ACTIVE", TODAY.minusDays(1), true, 30, TODAY)).contains("not automatic yet");
        assertThat(AgreementRules.attention("ENDED", TODAY.minusDays(1), false, 30, TODAY)).isNull();
        assertThat(AgreementRules.attention("ACTIVE", null, false, 30, TODAY)).isNull();
    }

    @Test
    @DisplayName("The actions on offer follow the status")
    void actions() {
        assertThat(AgreementRules.actionsFor("DRAFT")).containsExactly("activate", "cancel");
        assertThat(AgreementRules.actionsFor("ACTIVE")).containsExactly("suspend", "end");
        assertThat(AgreementRules.actionsFor("SUSPENDED")).containsExactly("resume", "end");
        assertThat(AgreementRules.actionsFor("ENDED")).isEmpty();
        assertThat(AgreementRules.actionsFor("CANCELLED")).isEmpty();
        assertThat(AgreementRules.canEdit("DRAFT")).isTrue();
        assertThat(AgreementRules.canEdit("ACTIVE")).isFalse();
    }
}
