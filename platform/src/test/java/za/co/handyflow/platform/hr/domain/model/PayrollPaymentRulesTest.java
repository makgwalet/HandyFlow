package za.co.handyflow.platform.hr.domain.model;

import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

/** Bank-file rules: bank details, references, the CSV, and who may move a payment on. */
class PayrollPaymentRulesTest {

    @Test
    void acceptsGoodBankDetailsWithSpacesAndDashes() {
        assertNull(PayrollPaymentRules.bankProblem("Thandi Nkosi", "1234 567 890", "250-655"));
        assertEquals("250655", PayrollPaymentRules.digits("250-655"));
    }

    @Test
    void rejectsMissingOrMalformedBankDetails() {
        assertNotNull(PayrollPaymentRules.bankProblem(" ", "1234567890", "250655"));
        assertEquals("no bank account number", PayrollPaymentRules.bankProblem("A B", null, "250655"));
        assertEquals("the account number must be 6 to 16 digits", PayrollPaymentRules.bankProblem("A B", "12345", "250655"));
        assertEquals("the account number must be 6 to 16 digits", PayrollPaymentRules.bankProblem("A B", "12345678901234567", "250655"));
        assertEquals("the account number must be 6 to 16 digits", PayrollPaymentRules.bankProblem("A B", "12AB567890", "250655"));
        assertEquals("no branch code", PayrollPaymentRules.bankProblem("A B", "1234567890", ""));
        assertEquals("the branch code must be 6 digits", PayrollPaymentRules.bankProblem("A B", "1234567890", "2506"));
    }

    @Test
    void referenceIsShortAndPlain() {
        assertEquals("SAL PR00005 E001", PayrollPaymentRules.reference("PR00005", "E001"));
        assertEquals("SAL PR00005 E1", PayrollPaymentRules.reference("PR-00005", "E-1"));
        assertTrue(PayrollPaymentRules.reference("PR000000000005", "EMPLOYEE-0000000001").length() <= PayrollPaymentRules.MAX_REFERENCE);
    }

    @Test
    void paymentsCanOnlyBeMadeUpForAnIssuedRunWithNothingPaidYet() {
        assertNull(PayrollPaymentRules.generateProblem("FINALISED", 0));
        assertNull(PayrollPaymentRules.generateProblem("COMPLETED", 0));
        assertNotNull(PayrollPaymentRules.generateProblem("APPROVED", 0));
        assertNotNull(PayrollPaymentRules.generateProblem("DRAFT", 0));
        assertNotNull(PayrollPaymentRules.generateProblem("FINALISED", 1));
    }

    @Test
    void confirmingPaidNeedsPendingPaymentsAndADownloadedFile() {
        assertNull(PayrollPaymentRules.markPaidProblem(3, 0));
        assertNotNull(PayrollPaymentRules.markPaidProblem(0, 0));
        assertEquals("Download the payment file before confirming that it was paid.", PayrollPaymentRules.markPaidProblem(3, 1));
    }

    @Test
    void aReturnNeedsAReasonAndAPaymentThatIsNotAlreadyReturned() {
        assertNull(PayrollPaymentRules.returnProblem("PAID", "Account closed"));
        assertNull(PayrollPaymentRules.returnProblem("PENDING", "Rejected by bank"));
        assertNotNull(PayrollPaymentRules.returnProblem("RETURNED", "x"));
        assertNotNull(PayrollPaymentRules.returnProblem("PAID", " "));
        assertNotNull(PayrollPaymentRules.returnProblem("PAID", null));
        assertNotNull(PayrollPaymentRules.returnProblem("PAID", "x".repeat(301)));
    }

    @Test
    void csvHasAHeaderOneLinePerPaymentAndTwoDecimals() {
        String csv = PayrollPaymentRules.csv(List.of(
                new PayrollPaymentRules.Line("Thandi Nkosi", "FNB", "250 655", "1234 567 890", new BigDecimal("21234.5"), "SAL PR00005 E001"),
                new PayrollPaymentRules.Line("Sipho, Dlamini", null, "632005", "9876543210", new BigDecimal("100"), "SAL PR00005 E002")));
        String[] l = csv.substring(1).split("\r\n");
        assertEquals("Beneficiary name,Bank,Branch code,Account number,Amount,Reference", l[0]);
        assertEquals("Thandi Nkosi,FNB,250655,1234567890,21234.50,SAL PR00005 E001", l[1]);
        assertEquals("\"Sipho, Dlamini\",,632005,9876543210,100.00,SAL PR00005 E002", l[2]);
        assertEquals(3, l.length);
    }

    @Test
    void namesThatLookLikeFormulasAreNeutralised() {
        String csv = PayrollPaymentRules.csv(List.of(
                new PayrollPaymentRules.Line("=HYPERLINK(\"http://x\")", "@bank", "250655", "1234567890", BigDecimal.TEN, "SAL")));
        assertTrue(csv.contains("\"'=HYPERLINK(\"\"http://x\"\")\""));
        assertTrue(csv.contains(",'@bank,"));
    }

    @Test
    void filenameIsSafe() {
        assertEquals("salary-payments-PR00005.csv", PayrollPaymentRules.filename("PR00005"));
        assertEquals("salary-payments-PR00005.csv", PayrollPaymentRules.filename("PR/00005\\.."));
    }

    @Test
    void accountTypesAreKnownAndBlankIsCurrent() {
        assertNull(PayrollPaymentRules.accountTypeProblem(null));
        assertNull(PayrollPaymentRules.accountTypeProblem(" "));
        assertNull(PayrollPaymentRules.accountTypeProblem("savings"));
        assertTrue(PayrollPaymentRules.accountTypeProblem("CREDIT").contains("current, savings"));
        assertEquals("SAVINGS", PayrollPaymentRules.accountType(" savings "));
        assertEquals("CURRENT", PayrollPaymentRules.accountType(null));
        assertEquals("Bond", PayrollPaymentRules.accountTypeLabel("BOND"));
        assertEquals("Current or cheque", PayrollPaymentRules.accountTypeLabel(null));
    }

    @Test
    void aLineWithoutATypeIsCurrent() {
        assertEquals("CURRENT", new PayrollPaymentRules.Line("A", "FNB", "632005", "1234567890", BigDecimal.TEN, "R").accountType());
    }
}
