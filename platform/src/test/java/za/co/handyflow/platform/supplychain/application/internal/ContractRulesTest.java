package za.co.handyflow.platform.supplychain.application.internal;

import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.time.LocalDate;

import static org.assertj.core.api.Assertions.assertThat;

class ContractRulesTest {
    private static LocalDate d(String s) { return LocalDate.parse(s); }

    @Test
    void lifecycle() {
        assertThat(ContractRules.canMove("DRAFT", "ACTIVE")).isTrue();
        assertThat(ContractRules.canMove("DRAFT", "EXPIRED")).isFalse();
        assertThat(ContractRules.canMove("ACTIVE", "EXPIRED")).isTrue();
        assertThat(ContractRules.canMove("ACTIVE", "TERMINATED")).isTrue();
        assertThat(ContractRules.canMove("ACTIVE", "DRAFT")).isFalse();
        assertThat(ContractRules.canMove("EXPIRED", "ACTIVE")).as("an expired contract is renewed by a new one").isFalse();
        assertThat(ContractRules.canMove("TERMINATED", "ACTIVE")).isFalse();
    }

    @Test
    void validation() {
        assertThat(ContractRules.problem(d("2026-01-01"), d("2026-12-31"), 60, null)).isNull();
        assertThat(ContractRules.problem(d("2026-01-01"), d("2025-12-31"), 60, null)).contains("before");
        assertThat(ContractRules.problem(null, d("2026-12-31"), 60, null)).contains("required");
        assertThat(ContractRules.problem(d("2026-01-01"), d("2026-12-31"), 400, null)).contains("0 and 365");
        assertThat(ContractRules.problem(d("2026-01-01"), d("2026-12-31"), 60, BigDecimal.ZERO)).contains("above zero");
    }

    @Test
    void renewalNoticeWindow_opensNoticeDaysBeforeTheEnd_andClosesAtTheEnd() {
        LocalDate end = d("2026-12-31");
        assertThat(ContractRules.renewalNoticeDue(end, d("2026-11-01"), 60)).as("60 days before the end is the first day").isTrue();
        assertThat(ContractRules.renewalNoticeDue(end, d("2026-10-31"), 60)).isFalse();
        assertThat(ContractRules.renewalNoticeDue(end, d("2026-12-31"), 60)).isTrue();
        assertThat(ContractRules.renewalNoticeDue(end, d("2027-01-01"), 60)).isFalse();
    }

    @Test
    void endedMeansTheDayAfterTheEndDate() {
        assertThat(ContractRules.hasEnded(d("2026-12-31"), d("2026-12-31"))).isFalse();
        assertThat(ContractRules.hasEnded(d("2026-12-31"), d("2027-01-01"))).isTrue();
    }

    @Test
    void autoRenewalRunsForTheSameNumberOfDays_startingTheNextDay() {
        var next = ContractRules.nextTerm(d("2026-01-01"), d("2026-12-31"));
        assertThat(next.start()).isEqualTo(d("2027-01-01"));
        assertThat(next.end()).isEqualTo(d("2027-12-31"));
        var oneDay = ContractRules.nextTerm(d("2026-05-05"), d("2026-05-05"));
        assertThat(oneDay.start()).isEqualTo(d("2026-05-06"));
        assertThat(oneDay.end()).isEqualTo(d("2026-05-06"));
    }

    @Test
    void priceVariance() {
        assertThat(ContractRules.overAgreedPct(new BigDecimal("110"), new BigDecimal("100"))).isEqualByComparingTo("10.0");
        assertThat(ContractRules.overAgreedPct(new BigDecimal("100"), new BigDecimal("100"))).isNull();
        assertThat(ContractRules.overAgreedPct(new BigDecimal("90"), new BigDecimal("100"))).isNull();
        assertThat(ContractRules.overAgreedPct(new BigDecimal("5"), BigDecimal.ZERO)).isNull();
    }
}
