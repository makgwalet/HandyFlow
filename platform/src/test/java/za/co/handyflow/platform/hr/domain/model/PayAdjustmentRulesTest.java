package za.co.handyflow.platform.hr.domain.model;

import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.time.LocalDate;

import static org.junit.jupiter.api.Assertions.*;

/** Bonuses and back pay: what may be entered, and when one may be cancelled. */
class PayAdjustmentRulesTest {

    private static final LocalDate TODAY = LocalDate.of(2026, 10, 28);
    private static final LocalDate STARTED = LocalDate.of(2024, 1, 15);

    private static BigDecimal d(String s) { return new BigDecimal(s); }

    private static String bonus(String amount, LocalDate payFrom, String description, String reason, boolean self) {
        return PayAdjustmentRules.addProblem("BONUS", amount == null ? null : d(amount), payFrom, description, reason, null, null, TODAY, STARTED, self);
    }

    private static String back(String amount, LocalDate from, LocalDate to) {
        return PayAdjustmentRules.addProblem("BACK_PAY", d(amount), TODAY, "Raise agreed late", "Approved", from, to, TODAY, STARTED, false);
    }

    @Test
    void aBonusWithEverythingFilledInIsAccepted() {
        assertNull(bonus("20000.00", TODAY, "Annual performance bonus 2026", "Board decision", false));
    }

    @Test
    void anAmountIsRequiredPositiveWithTwoDecimalsAndNotAbsurd() {
        assertNotNull(bonus(null, TODAY, "x", "y", false));
        assertNotNull(bonus("0", TODAY, "x", "y", false));
        assertNotNull(bonus("-5", TODAY, "x", "y", false));
        assertNotNull(bonus("10.005", TODAY, "x", "y", false));
        assertNotNull(bonus("10000000.01", TODAY, "x", "y", false));
        assertNull(bonus("10000000.00", TODAY, "x", "y", false));
    }

    @Test
    void aDescriptionAndAReasonAreRequired() {
        assertNotNull(bonus("100", TODAY, " ", "y", false));
        assertNotNull(bonus("100", TODAY, "x", null, false));
        assertNotNull(bonus("100", TODAY, "x".repeat(201), "y", false));
        assertNotNull(bonus("100", TODAY, "x", "y".repeat(301), false));
    }

    @Test
    void nobodyMayEnterAPaymentForThemselves() {
        String problem = bonus("100", TODAY, "x", "y", true);
        assertNotNull(problem);
        assertTrue(problem.startsWith("You cannot"));
    }

    @Test
    void thePayMonthIsRequiredAndCannotBeMoreThanAYearAway() {
        assertNotNull(bonus("100", null, "x", "y", false));
        assertNull(bonus("100", TODAY.plusDays(366), "x", "y", false));
        assertNotNull(bonus("100", TODAY.plusDays(367), "x", "y", false));
        assertNull(bonus("100", TODAY.minusMonths(2), "x", "y", false));   // missed an earlier run: paid in the next one
    }

    @Test
    void aBonusDoesNotCoverAPeriod() {
        assertNotNull(PayAdjustmentRules.addProblem("BONUS", d("100"), TODAY, "x", "y", LocalDate.of(2026, 1, 1), null, TODAY, STARTED, false));
    }

    @Test
    void backPayMustSayWhichPeriodItCoversAndThatPeriodMustHavePassed() {
        assertNull(back("5000", LocalDate.of(2026, 7, 1), LocalDate.of(2026, 9, 30)));
        assertNotNull(back("5000", null, LocalDate.of(2026, 9, 30)));
        assertNotNull(back("5000", LocalDate.of(2026, 7, 1), null));
        assertNotNull(back("5000", LocalDate.of(2026, 9, 30), LocalDate.of(2026, 7, 1)));
        assertNotNull(back("5000", LocalDate.of(2026, 7, 1), TODAY.plusDays(1)));
    }

    @Test
    void backPayCannotReachBackBeforeTheEmployeeStarted() {
        assertNotNull(back("5000", STARTED.minusDays(1), LocalDate.of(2026, 9, 30)));
        assertNull(back("5000", STARTED, LocalDate.of(2026, 9, 30)));
    }

    @Test
    void theKindMustBeKnown() {
        assertNotNull(PayAdjustmentRules.addProblem("COMMISSION", d("100"), TODAY, "x", "y", null, null, TODAY, STARTED, false));
        assertNotNull(PayAdjustmentRules.addProblem(null, d("100"), TODAY, "x", "y", null, null, TODAY, STARTED, false));
    }

    @Test
    void anOpenPaymentNoRunHasTakenCanBeCancelledWithAReason() {
        assertNull(PayAdjustmentRules.cancelProblem("OPEN", false, "Entered twice"));
        assertNotNull(PayAdjustmentRules.cancelProblem("OPEN", false, " "));
        assertNotNull(PayAdjustmentRules.cancelProblem("OPEN", false, null));
        assertNotNull(PayAdjustmentRules.cancelProblem("OPEN", false, "r".repeat(301)));
    }

    @Test
    void aPaymentARunHasTakenOrThatIsAlreadyCancelledCannotBeCancelled() {
        assertTrue(PayAdjustmentRules.cancelProblem("OPEN", true, "Entered twice").contains("pay run"));
        assertNotNull(PayAdjustmentRules.cancelProblem("CANCELLED", false, "Entered twice"));
    }

    @Test
    void aRecoveryIsEnteredLikeABonusWithNoPeriod() {
        assertNull(PayAdjustmentRules.addProblem("RECOVERY", d("1500.00"), TODAY, "Overpaid in September", "Paid twice by mistake", null, null, TODAY, STARTED, false));
        assertNotNull(PayAdjustmentRules.addProblem("RECOVERY", d("1500.00"), TODAY, "Overpaid in September", "Paid twice by mistake", TODAY, TODAY, TODAY, STARTED, false));
        assertEquals("Recovery of overpayment", PayAdjustmentRules.label("RECOVERY"));
    }

    @Test
    void recoveriesAreTakenWholeInOrderAndNeverTakeNetPayBelowZero() {
        var r = PayAdjustmentRules.affordable(java.util.List.of(d("4000"), d("3000"), d("500")), d("5000"));
        assertEquals(java.util.List.of(true, false, true), r);
        assertEquals(java.util.List.of(false), PayAdjustmentRules.affordable(java.util.List.of(d("100")), d("99.99")));
        assertEquals(java.util.List.of(true), PayAdjustmentRules.affordable(java.util.List.of(d("100")), d("100.00")));
        assertEquals(java.util.List.of(false), PayAdjustmentRules.affordable(java.util.List.of(d("100")), null));
    }
}
