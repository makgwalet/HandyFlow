package za.co.handyflow.platform.ap.application.internal;

import static org.junit.jupiter.api.Assertions.*;
import org.junit.jupiter.api.Test;
import java.util.List;
import java.util.UUID;
import za.co.handyflow.platform.ap.application.internal.ApDutyRules.Duty;

/** Finance patch F6: separation of duties in Accounts Payable. */
class ApDutyRulesTest {
    UUID clerk = UUID.randomUUID(), manager = UUID.randomUUID();

    @Test void differentPersonMayAct() { assertNull(ApDutyRules.problem(Duty.APPROVE_OWN_BILL, clerk, manager, 3)); }
    @Test void samePersonRefusedWhenSomeoneElseCan() {
        String p = ApDutyRules.problem(Duty.APPROVE_OWN_BILL, clerk, clerk, 1);
        assertTrue(p.startsWith("You captured this bill"));
        assertTrue(p.contains("one other person can"));
        assertTrue(ApDutyRules.problem(Duty.RELEASE_OWN_BATCH, clerk, clerk, 4).contains("4 other people can"));
    }
    @Test void soleHolderAllowedAndFlagged() {
        assertNull(ApDutyRules.problem(Duty.PAY_OWN_BILL, clerk, clerk, 0));
        assertTrue(ApDutyRules.soleException(clerk, clerk, 0));
        assertFalse(ApDutyRules.soleException(clerk, manager, 0));
        assertFalse(ApDutyRules.soleException(clerk, clerk, 2));
    }
    @Test void unknownEarlierPersonDoesNotBlock() {
        assertNull(ApDutyRules.problem(Duty.SECOND_APPROVAL, null, clerk, 5));
        assertFalse(ApDutyRules.soleException(null, clerk, 0));
    }
    @Test void bankingChangeIgnoresSpacesAndCase() {
        assertFalse(ApDutyRules.bankingChanged("FNB", "Acme", "6200 1234", "250655", "fnb", "ACME", "62001234", "250655"));
        assertTrue(ApDutyRules.bankingChanged("FNB", "Acme", "62001234", "250655", "FNB", "Acme", "62001235", "250655"));
        assertTrue(ApDutyRules.bankingChanged("FNB", "Acme", "62001234", "250655", "FNB", "Acme", "62001234", "198765"));
        assertTrue(ApDutyRules.bankingChanged(null, "Acme", "1", "2", "Nedbank", "Acme", "1", "2"));
    }
    @Test void unverifiedSuppliersNamed() {
        assertNull(ApDutyRules.unverifiedProblem(List.of()));
        assertNull(ApDutyRules.unverifiedProblem(null));
        String p = ApDutyRules.unverifiedProblem(List.of("Sasol", "Acme", "Sasol"));
        assertTrue(p.contains("Acme, Sasol"));
    }
}
