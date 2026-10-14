package za.co.handyflow.platform.ap.domain;

import static org.junit.jupiter.api.Assertions.*;
import org.junit.jupiter.api.Test;
import java.math.BigDecimal;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import za.co.handyflow.platform.ap.domain.ApPaymentRules.Allocation;
import za.co.handyflow.platform.ap.domain.ApPaymentRules.Open;

/** Finance patch F10: what a supplier bill owes, part payments and supplier credit notes. */
class ApPaymentRulesTest {
    static BigDecimal d(String v) { return new BigDecimal(v); }
    UUID b1 = UUID.randomUUID(), b2 = UUID.randomUUID(), b3 = UUID.randomUUID();

    @Test void balanceIsTotalLessPaidAndCredited() {
        assertEquals(d("600.00"), ApPaymentRules.balance(d("1000"), d("300"), d("100")));
        assertEquals(d("0.00"), ApPaymentRules.balance(d("1000"), d("900"), d("200")));
        assertEquals(d("100.00"), ApPaymentRules.balance(d("100"), null, null));
    }
    @Test void statusFollowsWhatIsSettled() {
        assertNull(ApPaymentRules.settledStatus(d("1000"), d("0"), d("0")));
        assertEquals("PARTIALLY_PAID", ApPaymentRules.settledStatus(d("1000"), d("300"), d("0")));
        assertEquals("PARTIALLY_PAID", ApPaymentRules.settledStatus(d("1000"), d("0"), d("999.99")));
        assertEquals("PAID", ApPaymentRules.settledStatus(d("1000"), d("700"), d("300")));
        assertEquals("PAID", ApPaymentRules.settledStatus(d("1000"), d("1000"), d("0")));
    }
    @Test void payableStatuses() {
        assertTrue(ApPaymentRules.payable("APPROVED"));
        assertTrue(ApPaymentRules.payable("OVERDUE"));
        assertTrue(ApPaymentRules.payable("PARTIALLY_PAID"));
        assertFalse(ApPaymentRules.payable("DRAFT"));
        assertFalse(ApPaymentRules.payable("PAID"));
        assertFalse(ApPaymentRules.payable("REJECTED"));
        assertFalse(ApPaymentRules.payable(null));
    }
    @Test void paymentDefaultsToTheBalance() {
        assertEquals(d("250.00"), ApPaymentRules.amountToPay(null, d("250")));
        assertEquals(d("100.00"), ApPaymentRules.amountToPay(d("100"), d("250")));
    }
    @Test void paymentMustFitTheBalanceAndNameABank() {
        assertNull(ApPaymentRules.paymentProblem(d("250"), d("250"), true));
        assertNull(ApPaymentRules.paymentProblem(d("0.01"), d("250"), true));
        assertEquals("Enter the amount to pay", ApPaymentRules.paymentProblem(d("0"), d("250"), true));
        assertEquals("Enter the amount to pay", ApPaymentRules.paymentProblem(null, d("250"), true));
        assertTrue(ApPaymentRules.paymentProblem(d("250.01"), d("250"), true).contains("more than the R 250.00"));
        assertEquals("This bill has nothing left to pay", ApPaymentRules.paymentProblem(d("10"), d("0"), true));
        assertTrue(ApPaymentRules.paymentProblem(d("10"), d("250"), false).contains("bank account"));
    }
    @Test void creditAmountsNeedAnAmountAndSensibleVat() {
        assertNull(ApPaymentRules.creditAmountsProblem(d("100"), d("15")));
        assertNull(ApPaymentRules.creditAmountsProblem(d("100"), null));
        assertNotNull(ApPaymentRules.creditAmountsProblem(null, null));
        assertNotNull(ApPaymentRules.creditAmountsProblem(d("0"), null));
        assertNotNull(ApPaymentRules.creditAmountsProblem(d("100"), d("-1")));
        assertNotNull(ApPaymentRules.creditAmountsProblem(d("100"), d("101")));
    }
    @Test void creditNotesOnABillNeverExceedIt() {
        assertNull(ApPaymentRules.creditNoteProblem(d("1000"), d("0"), d("1000")));
        assertNull(ApPaymentRules.creditNoteProblem(d("1000"), d("400"), d("600")));
        String p = ApPaymentRules.creditNoteProblem(d("1000"), d("400"), d("600.01"));
        assertTrue(p.contains("At most R 600.00"));
        assertTrue(ApPaymentRules.creditNoteProblem(d("1000"), d("1000"), d("1")).contains("At most R 0.00"));
    }
    @Test void creditSplitsBetweenTheBillAndTheSupplierAccount() {
        BigDecimal[] a = ApPaymentRules.splitCredit(d("300"), d("1000"));
        assertEquals(d("300.00"), a[0]); assertEquals(d("0.00"), a[1]);
        BigDecimal[] b = ApPaymentRules.splitCredit(d("300"), d("120"));
        assertEquals(d("120.00"), b[0]); assertEquals(d("180.00"), b[1]);
        BigDecimal[] c = ApPaymentRules.splitCredit(d("300"), d("0"));
        assertEquals(d("0.00"), c[0]); assertEquals(d("300.00"), c[1]);
    }
    @Test void allocationsMustFit() {
        Map<UUID, BigDecimal> open = new LinkedHashMap<>();
        open.put(b1, d("100")); open.put(b2, d("50"));
        assertNull(ApPaymentRules.allocationProblem(List.of(new Allocation(b1, d("100")), new Allocation(b2, d("20"))), d("120"), open));
        assertNotNull(ApPaymentRules.allocationProblem(List.of(), d("120"), open));
        assertNotNull(ApPaymentRules.allocationProblem(null, d("120"), open));
        assertTrue(ApPaymentRules.allocationProblem(List.of(new Allocation(b3, d("10"))), d("120"), open).contains("not open"));
        assertTrue(ApPaymentRules.allocationProblem(List.of(new Allocation(b2, d("50.01"))), d("120"), open).contains("still owes"));
        assertTrue(ApPaymentRules.allocationProblem(List.of(new Allocation(b1, d("60")), new Allocation(b1, d("60"))), d("500"), open).contains("still owes"));
        assertTrue(ApPaymentRules.allocationProblem(List.of(new Allocation(b1, d("100")), new Allocation(b2, d("50"))), d("120"), open).contains("more than the R 120.00"));
        assertNotNull(ApPaymentRules.allocationProblem(List.of(new Allocation(b1, d("0"))), d("120"), open));
    }
    @Test void oldestFirstFillsBillsInOrderUntilTheCreditRunsOut() {
        List<Allocation> a = ApPaymentRules.oldestFirst(d("130"), List.of(new Open(b1, d("100")), new Open(b2, d("50")), new Open(b3, d("70"))));
        assertEquals(2, a.size());
        assertEquals(b1, a.get(0).billId()); assertEquals(d("100.00"), a.get(0).amount());
        assertEquals(b2, a.get(1).billId()); assertEquals(d("30.00"), a.get(1).amount());
        assertTrue(ApPaymentRules.oldestFirst(d("0"), List.of(new Open(b1, d("100")))).isEmpty());
        assertEquals(1, ApPaymentRules.oldestFirst(d("500"), List.of(new Open(b1, d("100")), new Open(b2, d("0")))).size());
    }
    @Test void aBatchRefusesBillsThatNowOweLess() {
        Map<String, BigDecimal[]> ok = new LinkedHashMap<>();
        ok.put("INV-1", new BigDecimal[] { d("100"), d("100") });
        assertNull(ApPaymentRules.batchProblem(ok));
        Map<String, BigDecimal[]> changed = new LinkedHashMap<>();
        changed.put("INV-1", new BigDecimal[] { d("100"), d("100") });
        changed.put("INV-2", new BigDecimal[] { d("100"), d("60") });
        String p = ApPaymentRules.batchProblem(changed);
        assertTrue(p.contains("INV-2")); assertFalse(p.contains("INV-1"));
    }
}
