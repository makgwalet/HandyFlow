package za.co.handyflow.platform.earthmoving;

import org.junit.jupiter.api.Test;
import za.co.handyflow.platform.earthmoving.domain.model.*;
import za.co.handyflow.platform.shared.TenantId;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;

class HireRulesTest {
    static BigDecimal n(String s) { return new BigDecimal(s); }
    static final LocalDate TODAY = LocalDate.of(2026, 10, 15);
    static final LocalDate START = LocalDate.of(2026, 10, 1);

    @Test void basisIsDailyOrHourly() {
        assertEquals("DAILY", HireRules.basis(" daily "));
        assertEquals("HOURLY", HireRules.basis("HOURLY"));
        assertEquals("KILOMETRE", HireRules.basis("kilometre"));
        assertEquals("WEEKLY", HireRules.basis("weekly"));
        assertEquals("MONTHLY", HireRules.basis(" Monthly"));
        assertThrows(IllegalArgumentException.class, () -> HireRules.basis("FORTNIGHTLY"));
        assertThrows(IllegalArgumentException.class, () -> HireRules.basis(null));
    }
    @Test void daysCountBothEnds() {
        assertEquals(1, HireRules.days(START, START));
        assertEquals(7, HireRules.days(START, START.plusDays(6)));
        assertEquals(31, HireRules.days(LocalDate.of(2026, 10, 1), LocalDate.of(2026, 10, 31)));
    }
    @Test void aPeriodStaysInsideTheDeployment() {
        assertDoesNotThrow(() -> HireRules.checkPeriod(START, TODAY, START, null, TODAY));
        assertThrows(IllegalArgumentException.class, () -> HireRules.checkPeriod(START.minusDays(1), TODAY, START, null, TODAY));
        assertThrows(IllegalArgumentException.class, () -> HireRules.checkPeriod(START, TODAY.plusDays(1), START, null, TODAY));
        assertThrows(IllegalArgumentException.class, () -> HireRules.checkPeriod(TODAY, START, START, null, TODAY));
        assertThrows(IllegalArgumentException.class, () -> HireRules.checkPeriod(null, TODAY, START, null, TODAY));
    }
    @Test void aReturnedMachineCannotBeChargedPastItsReturnDay() {
        LocalDate back = LocalDate.of(2026, 10, 10);
        assertDoesNotThrow(() -> HireRules.checkPeriod(START, back, START, back, TODAY));
        assertThrows(IllegalArgumentException.class, () -> HireRules.checkPeriod(START, back.plusDays(1), START, back, TODAY));
    }
    @Test void aChargeCoversAtMostAYear() {
        LocalDate longAgo = LocalDate.of(2024, 1, 1);
        assertThrows(IllegalArgumentException.class, () -> HireRules.checkPeriod(longAgo, longAgo.plusDays(366), longAgo, null, TODAY));
        assertDoesNotThrow(() -> HireRules.checkPeriod(longAgo, longAgo.plusDays(365), longAgo, null, TODAY));
    }
    @Test void theRateMustBeSetForTheBasis() {
        assertEquals(n("1200"), HireRules.rateFor("DAILY", n("1200"), null));
        assertEquals(n("150"), HireRules.rateFor("HOURLY", null, n("150")));
        assertThrows(IllegalArgumentException.class, () -> HireRules.rateFor("DAILY", null, n("150")));
        assertThrows(IllegalArgumentException.class, () -> HireRules.rateFor("HOURLY", n("1200"), n("0")));
    }
    @Test void hoursNeedSomeWork() {
        assertThrows(IllegalArgumentException.class, () -> HireRules.hours(null));
        assertThrows(IllegalArgumentException.class, () -> HireRules.hours(BigDecimal.ZERO));
        assertEquals(n("12.50"), HireRules.hours(n("12.5")));
    }
    @Test void moneyIsToTheCent() {
        assertEquals(n("3000.00"), HireRules.amount(n("3"), n("1000")));
        assertEquals(n("0.99"), HireRules.amount(n("0.33"), n("3")));
        assertEquals(n("0.83"), HireRules.amount(n("2.5"), n("0.33")));        // 0.825 rounds half up
        assertEquals(n("450.00"), HireRules.vatAmount(n("3000.00"), n("15.00")));
        assertEquals(n("0.15"), HireRules.vatAmount(n("1.00"), n("15.00")));
        assertEquals(n("0.00"), HireRules.vatAmount(n("100.00"), n("0.00")));
    }
    @Test void vatDefaultsToFifteenAndIsChecked() {
        assertEquals(n("15.00"), HireRules.vatRate(null));
        assertEquals(n("0.00"), HireRules.vatRate(BigDecimal.ZERO));
        assertThrows(IllegalArgumentException.class, () -> HireRules.vatRate(n("-1")));
        assertThrows(IllegalArgumentException.class, () -> HireRules.vatRate(n("101")));
    }
    @Test void overlapsIncludeSharedDaysButNotNeighbours() {
        assertTrue(HireRules.overlaps(START, START.plusDays(4), START.plusDays(4), START.plusDays(8)));
        assertFalse(HireRules.overlaps(START, START.plusDays(4), START.plusDays(5), START.plusDays(8)));
        assertTrue(HireRules.overlaps(START, START.plusDays(10), START.plusDays(2), START.plusDays(3)));
    }
    @Test void theInvoiceLineSaysWhatWasCharged() {
        assertEquals("Hire: D9-001 CAT D9 at Mine A, 1 Oct 2026 to 7 Oct 2026 (7 days @ R1000.00)",
                HireRules.describe("D9-001 CAT D9", "Mine A", "DAILY", START, START.plusDays(6), n("7.00"), n("1000")));
        assertEquals("Hire: D9-001 CAT D9, 1 Oct 2026 (1 day @ R1000.00)",
                HireRules.describe("D9-001 CAT D9", null, "DAILY", START, START, n("1.00"), n("1000")));
        assertEquals("Hire: T-01 Tipper at Quarry, 1 Oct 2026 to 3 Oct 2026 (12.5 hrs @ R150.00)",
                HireRules.describe("T-01 Tipper", "Quarry", "HOURLY", START, START.plusDays(2), n("12.50"), n("150")));
    }

    // ── the charge itself ────────────────────────────────────────────────────

    private EarthHireCharge charge() {
        return EarthHireCharge.create(TenantId.generate(), UUID.randomUUID(), UUID.randomUUID(), UUID.randomUUID(), START, START.plusDays(2),
                "DAILY", n("3.00"), n("1000"), null, "Hire: x", UUID.randomUUID());
    }
    @Test void aChargeWorksOutItsMoney() {
        EarthHireCharge c = charge();
        assertEquals(n("3000.00"), c.getAmount());
        assertEquals(n("450.00"), c.getVatAmount());
        assertEquals(n("3450.00"), c.getTotal());
        assertEquals("PENDING", c.getStatus());
    }
    @Test void aChargeNeedsAQuantityAndARate() {
        assertThrows(IllegalArgumentException.class, () -> EarthHireCharge.create(TenantId.generate(), UUID.randomUUID(), UUID.randomUUID(),
                UUID.randomUUID(), START, START, "DAILY", BigDecimal.ZERO, n("1000"), null, "x", null));
        assertThrows(IllegalArgumentException.class, () -> EarthHireCharge.create(TenantId.generate(), UUID.randomUUID(), UUID.randomUUID(),
                UUID.randomUUID(), START, START, "DAILY", n("1"), null, null, "x", null));
    }
    @Test void aChargeGoesOntoOneInvoiceOnlyOnce() {
        EarthHireCharge c = charge();
        UUID inv = UUID.randomUUID();
        c.markInvoiced(inv, "INV-1");
        assertEquals("INVOICED", c.getStatus());
        assertEquals(inv, c.getInvoiceId());
        assertThrows(IllegalStateException.class, () -> c.markInvoiced(UUID.randomUUID(), "INV-2"));
        assertThrows(IllegalStateException.class, () -> c.voidCharge("billed in error"));
    }
    @Test void voidingNeedsAReasonAndOnlyWorksBeforeInvoicing() {
        EarthHireCharge c = charge();
        assertThrows(IllegalArgumentException.class, () -> c.voidCharge(" "));
        assertEquals("PENDING", c.getStatus());
        c.voidCharge("billed in error");
        assertEquals("VOIDED", c.getStatus());
        assertEquals("billed in error", c.getNote());
        assertThrows(IllegalStateException.class, () -> c.voidCharge("again please"));
        assertThrows(IllegalStateException.class, () -> c.markInvoiced(UUID.randomUUID(), "INV-3"));
    }
    @Test void reopeningNeedsAReasonAndClearsTheInvoice() {
        EarthHireCharge c = charge();
        assertThrows(IllegalStateException.class, () -> c.reopen("draft was deleted"));
        c.markInvoiced(UUID.randomUUID(), "INV-1");
        assertThrows(IllegalArgumentException.class, () -> c.reopen("x"));
        c.reopen("draft was deleted");
        assertEquals("PENDING", c.getStatus());
        assertNull(c.getInvoiceId());
        assertNull(c.getInvoiceNumber());
    }
    @Test void anAgreedRateNeedsAReasonAndBeatsTheMachineRate() {
        assertEquals(n("900"), HireRules.rate("DAILY", n("1000"), null, n("900"), "Agreed with the client"));
        assertEquals(n("1000"), HireRules.rate("DAILY", n("1000"), null, null, null));
        assertThrows(IllegalArgumentException.class, () -> HireRules.rate("DAILY", n("1000"), null, n("900"), " "));
        assertThrows(IllegalArgumentException.class, () -> HireRules.rate("DAILY", n("1000"), null, n("-5"), "Agreed with the client"));
    }
    @Test void aMachineWithNoRateCanStillBeChargedAtAnAgreedRate() {
        assertEquals(n("750"), HireRules.rate("HOURLY", null, null, n("750"), "Rate set by the contract"));
        assertThrows(IllegalArgumentException.class, () -> HireRules.rate("HOURLY", null, null, null, null));
    }

    // ── kilometres ───────────────────────────────────────────────────────────

    @Test void kilometresAreTheOdometerDifference() {
        assertEquals(n("850.00"), HireRules.kilometres(n("10000"), n("10850"), null));
        assertEquals(n("0.50"), HireRules.kilometres(n("10000.25"), n("10000.75"), null));
    }
    @Test void anAllowanceIsTakenOffAndCannotGoBelowNothing() {
        assertEquals(n("350.00"), HireRules.kilometres(n("10000"), n("10850"), n("500")));
        assertThrows(IllegalArgumentException.class, () -> HireRules.kilometres(n("10000"), n("10400"), n("500")));
        assertThrows(IllegalArgumentException.class, () -> HireRules.kilometres(n("10000"), n("10500"), n("500")));
        assertThrows(IllegalArgumentException.class, () -> HireRules.kilometres(n("10000"), n("10850"), n("-1")));
    }
    @Test void missingOrBackwardsReadingsAreRefusedNotGuessed() {
        assertThrows(IllegalArgumentException.class, () -> HireRules.kilometres(null, n("10850"), null));
        assertThrows(IllegalArgumentException.class, () -> HireRules.kilometres(n("10000"), null, null));
        assertThrows(IllegalArgumentException.class, () -> HireRules.kilometres(n("10850"), n("10000"), null));
        assertThrows(IllegalArgumentException.class, () -> HireRules.kilometres(n("10000"), n("10000"), null));
    }
    @Test void aKilometreRateIsNeverTakenFromTheMachine() {
        assertThrows(IllegalArgumentException.class, () -> HireRules.rateFor("KILOMETRE", n("1200"), n("150")));
        assertEquals(n("12.50"), HireRules.rate("KILOMETRE", null, null, n("12.50"), "Per-km rate in the contract"));
        assertThrows(IllegalArgumentException.class, () -> HireRules.rate("KILOMETRE", null, null, n("12.50"), null));
    }
    @Test void theInvoiceUnitFollowsTheBasis() {
        assertEquals("day", HireRules.unit("DAILY"));
        assertEquals("hour", HireRules.unit("HOURLY"));
        assertEquals("km", HireRules.unit("KILOMETRE"));
    }
    @Test void theLineShowsTheReadingsAndAllowance() {
        String s = HireRules.describeKm("D9", "Mine A", START, START.plusDays(9), n("10000"), n("10850"), n("500"), n("350.00"), n("12.5"));
        assertTrue(s.contains("odometer 10000 to 10850"));
        assertTrue(s.contains("500 km included"));
        assertTrue(s.contains("350 km @ R12.50"));
    }

    // ── weeks and months ─────────────────────────────────────────────────────

    @Test void everyWeekStartedIsAWholeWeek() {
        assertEquals(1, HireRules.weeks(START, START));
        assertEquals(1, HireRules.weeks(START, START.plusDays(6)));
        assertEquals(2, HireRules.weeks(START, START.plusDays(7)));
        assertEquals(2, HireRules.weeks(START, START.plusDays(13)));
        assertEquals(5, HireRules.weeks(LocalDate.of(2026, 10, 1), LocalDate.of(2026, 10, 31)));
    }
    @Test void everyMonthStartedIsAWholeMonth() {
        assertEquals(1, HireRules.months(LocalDate.of(2026, 10, 1), LocalDate.of(2026, 10, 31)));
        assertEquals(2, HireRules.months(LocalDate.of(2026, 10, 1), LocalDate.of(2026, 11, 1)));
        assertEquals(1, HireRules.months(LocalDate.of(2026, 10, 1), LocalDate.of(2026, 10, 1)));
        assertEquals(3, HireRules.months(LocalDate.of(2026, 10, 15), LocalDate.of(2027, 1, 14)));
        assertEquals(4, HireRules.months(LocalDate.of(2026, 10, 15), LocalDate.of(2027, 1, 15)));
    }
    @Test void aMonthStartingOnThe31stDoesNotDrift() {
        assertEquals(1, HireRules.months(LocalDate.of(2026, 1, 31), LocalDate.of(2026, 2, 27)));
        assertEquals(2, HireRules.months(LocalDate.of(2026, 1, 31), LocalDate.of(2026, 3, 1)));
        assertEquals(2, HireRules.months(LocalDate.of(2026, 1, 31), LocalDate.of(2026, 3, 30)));
    }
    @Test void weeklyAndMonthlyRatesComeFromTheContractOrAreAgreed() {
        assertThrows(IllegalArgumentException.class, () -> HireRules.rateFor("WEEKLY", n("1000"), n("100")));
        assertThrows(IllegalArgumentException.class, () -> HireRules.rateFor("MONTHLY", n("1000"), n("100")));
        assertThrows(IllegalArgumentException.class, () -> HireRules.choose("MONTHLY", null, null, null, n("1000"), n("100")));
        assertEquals(n("30000.00"), HireRules.choose("MONTHLY", null, null, n("30000.00"), n("1000"), n("100")).value());
    }
    @Test void theRateComesFromAgreedThenContractThenMachine() {
        var agreed = HireRules.choose("DAILY", n("900"), "Long-term discount", n("950"), n("1000"), n("100"));
        assertEquals(n("900"), agreed.value());
        assertEquals("AGREED", agreed.source());
        var contract = HireRules.choose("DAILY", null, null, n("950"), n("1000"), n("100"));
        assertEquals(n("950"), contract.value());
        assertEquals("CONTRACT", contract.source());
        var machine = HireRules.choose("DAILY", null, null, null, n("1000"), n("100"));
        assertEquals(n("1000"), machine.value());
        assertEquals("ASSET", machine.source());
        assertEquals(n("100"), HireRules.choose("HOURLY", null, null, null, n("1000"), n("100")).value());
        assertThrows(IllegalArgumentException.class, () -> HireRules.choose("DAILY", n("900"), null, n("950"), n("1000"), n("100")));
        assertThrows(IllegalArgumentException.class, () -> HireRules.choose("KILOMETRE", null, null, null, n("1000"), n("100")));
    }
    @Test void aContractRateMustBeMoreThanNothing() {
        assertEquals(n("12.50"), HireRules.contractRate("KILOMETRE", n("12.5")));
        assertThrows(IllegalArgumentException.class, () -> HireRules.contractRate("KILOMETRE", n("0")));
        assertThrows(IllegalArgumentException.class, () -> HireRules.contractRate("WEEKLY", null));
    }
    @Test void invoiceLinesNameWeeksAndMonths() {
        assertEquals("week", HireRules.unit("WEEKLY"));
        assertEquals("month", HireRules.unit("MONTHLY"));
        String w = HireRules.describe("D9", "Mine A", "WEEKLY", START, START.plusDays(13), n("2.00"), n("5000"));
        assertTrue(w.contains("2 weeks @ R5000.00"));
        String m = HireRules.describe("D9", null, "MONTHLY", START, START.plusDays(29), n("1.00"), n("30000"));
        assertTrue(m.contains("1 month @ R30000.00"));
    }
}
