package za.co.handyflow.platform.accounting.application.internal;

import static org.junit.jupiter.api.Assertions.*;
import org.junit.jupiter.api.Test;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import za.co.handyflow.platform.accounting.application.internal.MonthEndRules.*;

/** Finance patch F13: month-end close checklist. */
class MonthEndRulesTest {
    static LocalDate day(String s) { return LocalDate.parse(s); }
    LocalDate today = day("2026-10-14");

    @Test void monthEnds() {
        assertTrue(MonthEndRules.isMonthEnd(day("2026-09-30")));
        assertTrue(MonthEndRules.isMonthEnd(day("2028-02-29")));
        assertFalse(MonthEndRules.isMonthEnd(day("2026-09-29")));
        assertEquals(day("2026-09-30"), MonthEndRules.defaultPeriodEnd(today));
        assertEquals(day("2025-12-31"), MonthEndRules.defaultPeriodEnd(day("2026-01-05")));
    }

    @Test void countItemsBlockOrWarn() {
        assertEquals(MonthEndRules.DONE, MonthEndRules.countItem("D", "Drafts", 0, "{n} drafts", true).status());
        Item blocked = MonthEndRules.countItem("D", "Drafts", 3, "{n} drafts", true);
        assertEquals(MonthEndRules.BLOCKED, blocked.status());
        assertEquals("3 drafts", blocked.detail());
        assertEquals(MonthEndRules.WARNING, MonthEndRules.countItem("V", "VAT", 1, "{n} VAT period", false).status());
    }

    @Test void bankNeedsASignOffCoveringThePeriod() {
        LocalDate end = day("2026-09-30");
        Item item = MonthEndRules.bankItem(end, List.of(
                new BankState("FNB", day("2026-09-30"), true),
                new BankState("Savings", day("2026-08-31"), true),
                new BankState("Old", null, true),
                new BankState("Unused", null, false)));
        assertEquals(MonthEndRules.BLOCKED, item.status());
        assertTrue(item.detail().contains("Savings, Old"));
        assertFalse(item.detail().contains("FNB"));
        assertFalse(item.detail().contains("Unused"));
        assertEquals(MonthEndRules.DONE, MonthEndRules.bankItem(end, List.of(new BankState("FNB", end, true), new BankState("Unused", null, false))).status());
    }

    @Test void trialBalanceMustBalance() {
        assertEquals(MonthEndRules.DONE, MonthEndRules.trialBalanceItem(new BigDecimal("100.00"), new BigDecimal("100.004")).status());
        Item bad = MonthEndRules.trialBalanceItem(new BigDecimal("100.00"), new BigDecimal("90.00"));
        assertEquals(MonthEndRules.BLOCKED, bad.status());
        assertTrue(bad.detail().contains("10.00"));
    }

    private List<Item> allDone() {
        List<Item> items = new ArrayList<>();
        items.add(MonthEndRules.countItem("D", "Drafts", 0, "x", true));
        for (ManualStep s : MonthEndRules.MANUAL_STEPS) items.add(MonthEndRules.manualItem(s, true, "ok"));
        items.add(MonthEndRules.lockItem(day("2026-09-30"), null)); // not blocking
        return items;
    }

    @Test void closeIsAllowedWhenEverythingBlockingIsDone() {
        assertNull(MonthEndRules.closeProblem(day("2026-09-30"), today, null, allDone()));
    }

    @Test void closeRefusals() {
        List<Item> items = allDone();
        assertNotNull(MonthEndRules.closeProblem(null, today, null, items));
        assertEquals("Close to the last day of a month", MonthEndRules.closeProblem(day("2026-09-29"), today, null, items));
        assertEquals("A month cannot be closed before it has ended", MonthEndRules.closeProblem(day("2026-10-31"), today, null, items));
        assertTrue(MonthEndRules.closeProblem(day("2026-08-31"), today, day("2026-09-30"), items).contains("already closed"));
        items.add(MonthEndRules.countItem("P", "Posting problems", 2, "{n} problems", true));
        items.set(1, MonthEndRules.manualItem(MonthEndRules.MANUAL_STEPS.get(0), false, ""));
        String p = MonthEndRules.closeProblem(day("2026-09-30"), today, null, items);
        assertTrue(p.startsWith("2 items are not done"));
        assertTrue(p.contains("Posting problems"));
        assertTrue(p.contains("Accruals"));
    }

    @Test void warningsAndTheLockItemDoNotBlock() {
        List<Item> items = allDone();
        items.add(MonthEndRules.countItem("V", "VAT", 1, "{n} open", false));
        assertNull(MonthEndRules.closeProblem(day("2026-09-30"), today, null, items));
    }

    @Test void confirmingRules() {
        assertNull(MonthEndRules.confirmProblem("PAYROLL", day("2026-09-30"), null));
        assertEquals("Unknown step", MonthEndRules.confirmProblem("NOPE", day("2026-09-30"), null));
        assertNotNull(MonthEndRules.confirmProblem("PAYROLL", day("2026-09-15"), null));
        assertTrue(MonthEndRules.confirmProblem("PAYROLL", day("2026-08-31"), day("2026-09-30")).contains("closed"));
    }

    @Test void lockItem() {
        assertEquals(MonthEndRules.DONE, MonthEndRules.lockItem(day("2026-09-30"), day("2026-09-30")).status());
        assertEquals(MonthEndRules.WAITING, MonthEndRules.lockItem(day("2026-09-30"), day("2026-08-31")).status());
    }
}
