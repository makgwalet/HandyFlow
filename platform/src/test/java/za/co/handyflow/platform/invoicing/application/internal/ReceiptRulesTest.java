package za.co.handyflow.platform.invoicing.application.internal;

import static org.junit.jupiter.api.Assertions.*;
import org.junit.jupiter.api.Test;
import java.math.BigDecimal;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import za.co.handyflow.platform.invoicing.application.internal.ReceiptRules.Allocation;
import za.co.handyflow.platform.invoicing.application.internal.ReceiptRules.Open;

/** Finance patch F8: receipts, allocations and credit notes settling invoices. */
class ReceiptRulesTest {
    static BigDecimal r(String s) { return new BigDecimal(s); }
    UUID a = UUID.randomUUID(), b = UUID.randomUUID(), c = UUID.randomUUID();

    @Test void balanceCountsPaymentsAndCredits() {
        assertEquals(r("15.00"), ReceiptRules.balance(r("115"), r("50"), r("50")));
        assertEquals(r("0.00"), ReceiptRules.balance(r("115"), r("100"), r("50")));
    }
    @Test void statusFollowsSettlement() {
        assertNull(ReceiptRules.settledStatus(r("115"), BigDecimal.ZERO, BigDecimal.ZERO));
        assertEquals("PARTIALLY_PAID", ReceiptRules.settledStatus(r("115"), r("15"), null));
        assertEquals("PAID", ReceiptRules.settledStatus(r("115"), r("15"), r("100")));
        assertEquals("PAID", ReceiptRules.settledStatus(r("115"), null, r("115")));
    }
    @Test void allocationsMustFitInvoicesAndMoney() {
        Map<UUID, BigDecimal> open = Map.of(a, r("100"), b, r("50"));
        assertNull(ReceiptRules.allocationProblem(List.of(new Allocation(a, r("100")), new Allocation(b, r("20"))), r("120"), open));
        assertNotNull(ReceiptRules.allocationProblem(List.of(new Allocation(a, r("100.02"))), r("500"), open));
        assertNotNull(ReceiptRules.allocationProblem(List.of(new Allocation(a, r("60")), new Allocation(a, r("60"))), r("500"), open));
        assertNotNull(ReceiptRules.allocationProblem(List.of(new Allocation(a, r("100")), new Allocation(b, r("50"))), r("120"), open));
        assertNotNull(ReceiptRules.allocationProblem(List.of(new Allocation(c, r("10"))), r("500"), open));
        assertNotNull(ReceiptRules.allocationProblem(List.of(new Allocation(a, r("0"))), r("500"), open));
        assertNull(ReceiptRules.allocationProblem(List.of(), r("0"), open));
    }
    @Test void oldestFirstUsesTheMoneyInOrder() {
        List<Allocation> out = ReceiptRules.oldestFirst(r("130"), List.of(new Open(a, r("100")), new Open(b, r("50")), new Open(c, r("10"))));
        assertEquals(2, out.size());
        assertEquals(r("100.00"), out.get(0).amount());
        assertEquals(r("30.00"), out.get(1).amount());
        assertTrue(ReceiptRules.oldestFirst(r("0"), List.of(new Open(a, r("1")))).isEmpty());
    }
    @Test void creditNotesCannotExceedTheInvoice() {
        assertNull(ReceiptRules.creditNoteProblem(r("115"), r("15"), r("100")));
        String p = ReceiptRules.creditNoteProblem(r("115"), r("15"), r("100.10"));
        assertTrue(p.contains("R 100.00"));
    }
    @Test void creditSplitsBetweenInvoiceAndAccount() {
        BigDecimal[] s = ReceiptRules.splitCredit(r("115"), r("15"));
        assertEquals(r("15.00"), s[0]);
        assertEquals(r("100.00"), s[1]);
        s = ReceiptRules.splitCredit(r("10"), r("115"));
        assertEquals(r("10.00"), s[0]);
        assertEquals(r("0.00"), s[1]);
    }
}
