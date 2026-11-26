package za.co.handyflow.platform.payrollbureau.domain.rules;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class DeliveryRulesTest {

    @Test
    void aPayslipNeverTriedIsSent() {
        assertTrue(DeliveryRules.shouldSend(null, 0, false, false));
        assertTrue(DeliveryRules.shouldSend(null, 0, false, true));
    }

    @Test
    void aPayslipAlreadySentIsNotSentAgainUnlessAPersonAsks() {
        assertFalse(DeliveryRules.shouldSend("SENT", 1, false, false));
        assertFalse(DeliveryRules.shouldSend("SENT", 1, false, true));
        assertTrue(DeliveryRules.shouldSend("SENT", 1, true, false));
    }

    @Test
    void aFailedPayslipIsRetriedByHandAlwaysAndAutomaticallyUpToTheLimit() {
        assertTrue(DeliveryRules.shouldSend("FAILED", 5, false, false));
        assertTrue(DeliveryRules.shouldSend("FAILED", DeliveryRules.MAX_AUTO_ATTEMPTS - 1, false, true));
        assertFalse(DeliveryRules.shouldSend("FAILED", DeliveryRules.MAX_AUTO_ATTEMPTS, false, true));
    }

    @Test
    void anEmployeeWithNoEmailIsLookedAtAgain() {
        assertTrue(DeliveryRules.shouldSend("NO_EMAIL", 0, false, true));
    }

    @Test
    void aRunIsSettledOnlyWhenNothingRetryableIsLeft() {
        assertTrue(DeliveryRules.runIsSettled(0));
        assertFalse(DeliveryRules.runIsSettled(2));
    }
}
