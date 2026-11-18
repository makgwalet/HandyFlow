package za.co.handyflow.platform.supplychain;

import org.junit.jupiter.api.Test;
import za.co.handyflow.platform.supplychain.application.internal.ApprovalLimitRules;
import za.co.handyflow.platform.supplychain.application.internal.PriceOverrideRules;
import za.co.handyflow.platform.supplychain.application.internal.ScmPolicyRules;

import static org.junit.jupiter.api.Assertions.*;
import static za.co.handyflow.platform.supplychain.application.internal.ScmPolicyRules.*;

import java.math.BigDecimal;

class ScmPolicyRulesTest {

    private static BigDecimal d(String s) { return new BigDecimal(s); }

    @Test void categoryToleranceBeatsCompanyAndFallsBack() {
        assertEquals(d("2"), tolerance(d("5"), d("2")));
        assertEquals(d("0"), tolerance(d("5"), d("0")));
        assertEquals(d("5"), tolerance(d("5"), null));
    }

    @Test void categoryToleranceDecidesWhetherAnIncreaseNeedsApproval() {
        // 8 % increase: fine under a 10 % category tolerance, needs approval under a 5 % company default.
        assertFalse(PriceOverrideRules.assess(d("100"), d("108"), d("1"), tolerance(d("5"), d("10"))).needsApproval());
        assertTrue(PriceOverrideRules.assess(d("100"), d("108"), d("1"), tolerance(d("5"), null)).needsApproval());
    }

    @Test void supplierVatBeatsCompany() {
        assertEquals(d("0"), vatRecoverablePct(d("100"), d("0")));
        assertEquals(d("100"), vatRecoverablePct(d("100"), null));
    }

    @Test void vatFactorPerBasis() {
        assertEquals(0, vatFactor("INC_VAT", d("100"), false).compareTo(BigDecimal.ONE));
        assertEquals(0, vatFactor("NET_OF_RECOVERABLE", d("100"), true).compareTo(BigDecimal.ZERO));
        assertEquals(0, vatFactor("NET_OF_RECOVERABLE", d("0"), true).compareTo(BigDecimal.ONE));
        assertEquals(0, vatFactor("NET_OF_RECOVERABLE", d("60"), true).compareTo(d("0.4")));
        assertEquals(0, vatFactor("EX_VAT", d("60"), false).compareTo(BigDecimal.ZERO));
        assertEquals(0, vatFactor("EX_VAT", d("60"), true).compareTo(d("0.4")));
    }

    @Test void supplierWithNoRecoveryCountsAllItsVat() {
        BigDecimal net = d("1000"), vat = d("150");
        BigDecimal recovering = net.add(vat.multiply(vatFactor("NET_OF_RECOVERABLE", vatRecoverablePct(d("100"), null), true)));
        BigDecimal notRecovering = net.add(vat.multiply(vatFactor("NET_OF_RECOVERABLE", vatRecoverablePct(d("100"), d("0")), true)));
        assertEquals(0, recovering.compareTo(d("1000")));
        assertEquals(0, notRecovering.compareTo(d("1150")));
    }

    @Test void convertsToBaseAtTheRate() {
        assertEquals(d("18550.00"), toBase(d("1000"), d("18.55")));
        assertEquals(d("0.19"), toBase(d("0.01"), d("18.55")));
        assertThrows(IllegalArgumentException.class, () -> toBase(d("1"), null));
    }

    @Test void baseCurrencyDetection() {
        assertTrue(isBase("zar", "ZAR"));
        assertTrue(isBase(null, "ZAR"));
        assertFalse(isBase("USD", "ZAR"));
    }

    @Test void rateValidation() {
        assertNull(rateProblem("USD", "ZAR", d("18.5")));
        assertNotNull(rateProblem("ZAR", "ZAR", d("1")));
        assertNotNull(rateProblem("USD", "ZAR", d("0")));
        assertNotNull(rateProblem("USD", "ZAR", null));
        assertNotNull(rateProblem("US", "ZAR", d("1")));
        assertNotNull(rateProblem("USD", "ZAR", d("1.123456789")));
    }

    @Test void priceIncreaseAmountForTheApproverCap() {
        assertEquals(d("30.00"), increaseAmount(d("100"), d("110"), d("3")));
        assertEquals(d("0"), increaseAmount(d("100"), d("90"), d("3")));
        assertEquals(d("50.00"), increaseAmount(null, d("10"), d("5")));
    }

    @Test void priceOverrideIsAnApprovalLimitDocumentType() {
        assertTrue(ApprovalLimitRules.validDocumentType(ApprovalLimitRules.PRICE_OVERRIDE));
        assertEquals("price increase", ApprovalLimitRules.singular(ApprovalLimitRules.PRICE_OVERRIDE));
    }

    @Test void contractSavingsAgainstListPrice() {
        Savings s = savings(d("100"), d("90"), d("90"), d("10"));
        assertEquals(d("100.00"), s.saving());
        assertEquals(d("0"), s.leakage());
    }

    @Test void paidAboveContractIsLeakageNotSaving() {
        Savings s = savings(d("100"), d("90"), d("95"), d("10"));
        assertEquals(d("50.00"), s.saving());
        assertEquals(d("50.00"), s.leakage());
    }

    @Test void paidAboveListHasNoSaving() {
        assertEquals(d("0"), savings(d("100"), null, d("120"), d("2")).saving());
        assertEquals(d("0"), savings(null, null, d("120"), d("2")).saving());
    }
}
