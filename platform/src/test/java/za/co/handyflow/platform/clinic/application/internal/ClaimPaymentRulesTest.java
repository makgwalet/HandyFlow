package za.co.handyflow.platform.clinic.application.internal;

import org.junit.jupiter.api.Test;

import java.math.BigDecimal;

import static org.junit.jupiter.api.Assertions.*;

class ClaimPaymentRulesTest {

    private static final BigDecimal GROSS = new BigDecimal("520.00");

    private static BigDecimal bd(String s) { return new BigDecimal(s); }

    @Test
    void aFullPaymentWithoutAnAmountIsTheClaimTotal() {
        assertEquals(GROSS, ClaimPaymentRules.schemeAmount("PAID", GROSS, null));
    }

    @Test
    void aFullPaymentMayStateALowerAmountButNotZeroOrMore() {
        assertEquals(bd("500.00"), ClaimPaymentRules.schemeAmount("PAID", GROSS, bd("500")));
        assertEquals(GROSS, ClaimPaymentRules.schemeAmount("PAID", GROSS, bd("520.00")));
        assertThrows(IllegalArgumentException.class, () -> ClaimPaymentRules.schemeAmount("PAID", GROSS, bd("520.01")));
        assertThrows(IllegalArgumentException.class, () -> ClaimPaymentRules.schemeAmount("PAID", GROSS, BigDecimal.ZERO));
        assertThrows(IllegalArgumentException.class, () -> ClaimPaymentRules.schemeAmount("PAID", GROSS, bd("-1")));
    }

    @Test
    void aPartialPaymentNeedsAnAmountAndNeverDefaultsTo80Percent() {
        IllegalArgumentException e = assertThrows(IllegalArgumentException.class,
                () -> ClaimPaymentRules.schemeAmount("PARTIAL", GROSS, null));
        assertTrue(e.getMessage().contains("Enter the amount"));
    }

    @Test
    void aPartialPaymentIsAboveZeroAndBelowTheTotal() {
        assertEquals(bd("300.00"), ClaimPaymentRules.schemeAmount("PARTIAL", GROSS, bd("300")));
        assertEquals(bd("0.01"), ClaimPaymentRules.schemeAmount("PARTIAL", GROSS, bd("0.01")));
        assertThrows(IllegalArgumentException.class, () -> ClaimPaymentRules.schemeAmount("PARTIAL", GROSS, BigDecimal.ZERO));
        assertThrows(IllegalArgumentException.class, () -> ClaimPaymentRules.schemeAmount("PARTIAL", GROSS, GROSS));
        assertThrows(IllegalArgumentException.class, () -> ClaimPaymentRules.schemeAmount("PARTIAL", GROSS, bd("600")));
    }

    @Test
    void amountsAreRoundedToCents() {
        assertEquals(bd("300.13"), ClaimPaymentRules.schemeAmount("PARTIAL", GROSS, bd("300.125")));
    }

    @Test
    void otherActionsAreNotPayments() {
        assertThrows(IllegalArgumentException.class, () -> ClaimPaymentRules.schemeAmount("ACCEPT", GROSS, null));
    }
}
