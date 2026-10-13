package za.co.handyflow.platform.expenses.application.internal;

import static org.junit.jupiter.api.Assertions.*;
import org.junit.jupiter.api.Test;
import java.util.UUID;
/** Finance patch F1: who may approve, see and attach to an expense claim. */
class ExpenseRulesTest {
  UUID a = UUID.randomUUID(), b = UUID.randomUUID();
  @Test void ownClaimRefused(){ assertNotNull(ExpenseRules.selfApprovalProblem(a,a,null,null)); }
  @Test void unknownApproverRefused(){ assertNotNull(ExpenseRules.selfApprovalProblem(a,null,null,null)); }
  @Test void paysApproverRefusedIgnoringCase(){ assertNotNull(ExpenseRules.selfApprovalProblem(a,b," Tumi@X.co ","tumi@x.CO")); }
  @Test void otherApproverAllowed(){ assertNull(ExpenseRules.selfApprovalProblem(a,b,"emp@x.co","boss@x.co")); }
  @Test void blankEmailsDoNotMatch(){ assertNull(ExpenseRules.selfApprovalProblem(a,b,"","  ")); }
  @Test void visibility(){ assertTrue(ExpenseRules.maySee(true,a,b)); assertTrue(ExpenseRules.maySee(false,a,a)); assertFalse(ExpenseRules.maySee(false,a,b)); assertFalse(ExpenseRules.maySee(false,a,null)); }
  @Test void attach(){ assertTrue(ExpenseRules.mayAttach(true,a,b)); assertTrue(ExpenseRules.mayAttach(false,a,a)); assertFalse(ExpenseRules.mayAttach(false,a,b)); }
}
