package za.co.handyflow.platform.payrollbureau.domain.rules;

import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;

class EmployeeChangeRulesTest {

    private final UUID maker = UUID.randomUUID();
    private final UUID checker = UUID.randomUUID();

    @Test
    void sameAmountWithDifferentScaleIsNotAChange() {
        assertFalse(EmployeeChangeRules.differs(new BigDecimal("100.0"), new BigDecimal("100.00")));
    }

    @Test
    void nullCountsAsZero() {
        assertFalse(EmployeeChangeRules.differs((BigDecimal) null, BigDecimal.ZERO));
        assertTrue(EmployeeChangeRules.differs((BigDecimal) null, BigDecimal.ONE));
    }

    @Test
    void textIgnoresBlankAndSurroundingSpace() {
        assertFalse(EmployeeChangeRules.differs(" FNB ", "FNB"));
        assertFalse(EmployeeChangeRules.differs((String) null, ""));
        assertTrue(EmployeeChangeRules.differs("FNB", "Absa"));
    }

    @Test
    void theMakerCannotApproveTheirOwnChange() {
        assertTrue(EmployeeChangeRules.decisionProblem("PENDING", maker, maker).contains("different user"));
    }

    @Test
    void aDifferentUserCanDecideAPendingChange() {
        assertNull(EmployeeChangeRules.decisionProblem("PENDING", maker, checker));
    }

    @Test
    void aDecidedChangeCannotBeDecidedAgain() {
        assertTrue(EmployeeChangeRules.decisionProblem("APPROVED", maker, checker).contains("already been approved"));
    }

    @Test
    void anUnknownApproverIsRefused() {
        assertNotNull(EmployeeChangeRules.decisionProblem("PENDING", maker, null));
    }

    @Test
    void onlyTheMakerCanCancelAndOnlyWhilePending() {
        assertNull(EmployeeChangeRules.cancelProblem("PENDING", maker, maker));
        assertTrue(EmployeeChangeRules.cancelProblem("PENDING", maker, checker).contains("Only the person"));
        assertTrue(EmployeeChangeRules.cancelProblem("REJECTED", maker, maker).contains("already been rejected"));
    }
}
