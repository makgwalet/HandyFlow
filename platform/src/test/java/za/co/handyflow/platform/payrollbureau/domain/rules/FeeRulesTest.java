package za.co.handyflow.platform.payrollbureau.domain.rules;

import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.time.LocalDate;

import static org.junit.jupiter.api.Assertions.*;

class FeeRulesTest {

    private static BigDecimal d(String s) { return new BigDecimal(s); }

    @Test
    void chargesPerEmployeeWhenAboveTheMinimum() {
        var f = FeeRules.runFee(d("50.00"), 10, d("300"));
        assertFalse(f.minimumApplied());
        assertEquals(d("500.00"), f.amount());
        assertEquals(d("10"), f.quantity());
    }

    @Test
    void chargesTheMinimumWhenThePerEmployeeTotalIsLess() {
        var f = FeeRules.runFee(d("50.00"), 3, d("300"));
        assertTrue(f.minimumApplied());
        assertEquals(d("300.00"), f.amount());
        assertEquals(d("1"), f.quantity());
        assertEquals(d("300.00"), f.unitPrice());
    }

    @Test
    void aMinimumEqualToTheTotalIsNotCalledAMinimum() {
        assertFalse(FeeRules.runFee(d("50"), 6, d("300")).minimumApplied());
        assertFalse(FeeRules.runFee(d("50"), 6, null).minimumApplied());
    }

    @Test
    void monthlyAndAnnualChargesAreBilledOncePerPeriod() {
        assertTrue(FeeRules.chargeDue("PER_RUN", true));
        assertTrue(FeeRules.chargeDue("MONTHLY", false));
        assertFalse(FeeRules.chargeDue("MONTHLY", true));
        assertFalse(FeeRules.chargeDue("ANNUAL", true));
        assertTrue(FeeRules.chargeDue("ANNUAL", false));
    }

    @Test
    void periodsAreTheCalendarMonthAndYear() {
        LocalDate d = LocalDate.of(2028, 2, 10);
        assertEquals(LocalDate.of(2028, 2, 1), FeeRules.periodStart("MONTHLY", d));
        assertEquals(LocalDate.of(2028, 2, 29), FeeRules.periodEnd("MONTHLY", d));
        assertEquals(LocalDate.of(2028, 1, 1), FeeRules.periodStart("ANNUAL", d));
        assertEquals(LocalDate.of(2028, 12, 31), FeeRules.periodEnd("ANNUAL", d));
    }

    @Test
    void validatesChargesAndPricing() {
        assertTrue(FeeRules.chargeProblem("EMP201 filing", d("250"), "MONTHLY") == null);
        assertTrue(FeeRules.chargeProblem("", d("250"), "MONTHLY") != null);
        assertTrue(FeeRules.chargeProblem("x", d("0"), "MONTHLY") != null);
        assertTrue(FeeRules.chargeProblem("x", d("5"), "WEEKLY") != null);
        assertTrue(FeeRules.pricingProblem(d("-1"), d("0")) != null);
        assertTrue(FeeRules.pricingProblem(d("50"), d("300")) == null);
    }

    @Test
    void aFeeNoteWithPaymentsOrNoReasonCannotBeCancelled() {
        assertTrue(FeeRules.cancelProblem("SENT", d("10"), "wrong run") != null);
        assertTrue(FeeRules.cancelProblem("SENT", d("0"), " ") != null);
        assertTrue(FeeRules.cancelProblem("CANCELLED", d("0"), "x") != null);
        assertTrue(FeeRules.cancelProblem("DRAFT", d("0"), "billed the wrong run") == null);
    }

    @Test
    void marginIsAPercentageOfRevenue() {
        assertEquals(d("60.0"), FeeRules.marginPercent(d("1000"), d("400")));
        assertEquals(d("-20.0"), FeeRules.marginPercent(d("500"), d("600")));
        assertTrue(FeeRules.marginPercent(d("0"), d("100")) == null);
    }
}
