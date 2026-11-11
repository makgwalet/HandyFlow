package za.co.handyflow.platform.admin.application.internal;

import org.junit.jupiter.api.Test;
import za.co.handyflow.platform.admin.application.internal.TierPricing.*;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.YearMonth;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

class TierPricingTest {

    private static Price p(String tier, Period per, String amt, String from, boolean approved) {
        return new Price(tier, per, amt == null ? null : new BigDecimal(amt), LocalDate.parse(from), approved);
    }

    @Test
    void unapprovedPriceIsNeverUsed() {
        var book = List.of(p("SME", Period.MONTHLY, "100.00", "2026-01-01", false));
        assertTrue(TierPricing.effective(book, "SME", Period.MONTHLY, LocalDate.parse("2026-06-01")).isEmpty());
    }

    @Test
    void latestApprovedPriceInEffectWins() {
        var book = List.of(
                p("SME", Period.MONTHLY, "100.00", "2026-01-01", true),
                p("SME", Period.MONTHLY, "120.00", "2026-07-01", true),
                p("SME", Period.MONTHLY, "999.00", "2026-03-01", false),
                p("SME", Period.ANNUAL, "1000.00", "2026-01-01", true));
        assertEquals(new BigDecimal("100.00"), TierPricing.effective(book, "SME", Period.MONTHLY, LocalDate.parse("2026-06-30")).get().amount());
        assertEquals(new BigDecimal("120.00"), TierPricing.effective(book, "SME", Period.MONTHLY, LocalDate.parse("2026-07-01")).get().amount());
        assertEquals(new BigDecimal("1000.00"), TierPricing.effective(book, "SME", Period.ANNUAL, LocalDate.parse("2026-07-01")).get().amount());
        assertTrue(TierPricing.effective(book, "SME", Period.MONTHLY, LocalDate.parse("2025-12-31")).isEmpty());
        assertTrue(TierPricing.effective(book, "STARTER", Period.MONTHLY, LocalDate.parse("2026-07-01")).isEmpty());
    }

    @Test
    void classifiesChanges() {
        assertEquals(ChangeKind.NEW, TierPricing.classify(null, 2));
        assertEquals(ChangeKind.UPGRADE, TierPricing.classify(2, 3));
        assertEquals(ChangeKind.DOWNGRADE, TierPricing.classify(3, 2));
        assertEquals(ChangeKind.SAME_TIER, TierPricing.classify(3, 3));
    }

    @Test
    void downgradeWaitsForEndOfPaidPeriodButUpgradeDoesNot() {
        LocalDate end = LocalDate.parse("2026-08-01");
        assertEquals(end, TierPricing.changeEffective(ChangeKind.DOWNGRADE, LocalDate.parse("2026-07-10"), end));
        assertEquals(LocalDate.parse("2026-09-01"), TierPricing.changeEffective(ChangeKind.DOWNGRADE, LocalDate.parse("2026-09-01"), end));
        assertEquals(LocalDate.parse("2026-07-10"), TierPricing.changeEffective(ChangeKind.UPGRADE, LocalDate.parse("2026-07-10"), end));
    }

    @Test
    void periodBoundaries() {
        LocalDate start = LocalDate.parse("2026-03-15");
        assertEquals(LocalDate.parse("2026-07-01"), TierPricing.periodStart(Period.MONTHLY, start, LocalDate.parse("2026-07-20")));
        assertEquals(LocalDate.parse("2026-08-01"), TierPricing.periodEnd(Period.MONTHLY, start, LocalDate.parse("2026-07-20")));
        assertEquals(LocalDate.parse("2027-03-15"), TierPricing.periodStart(Period.ANNUAL, start, LocalDate.parse("2027-05-01")));
        assertEquals(LocalDate.parse("2028-03-15"), TierPricing.periodEnd(Period.ANNUAL, start, LocalDate.parse("2027-05-01")));
    }

    @Test
    void upgradeProrationIsDifferenceForRemainingDays() {
        // July has 31 days; upgrading on 17 July leaves 15 days. (200 - 100) * 15 / 31 = 48.39
        BigDecimal r = TierPricing.prorateUpgrade(new BigDecimal("100"), new BigDecimal("200"),
                LocalDate.parse("2026-07-17"), LocalDate.parse("2026-07-01"), LocalDate.parse("2026-08-01"));
        assertEquals(new BigDecimal("48.39"), r);
    }

    @Test
    void prorationIsZeroWhenNotApplicable() {
        LocalDate s = LocalDate.parse("2026-07-01"), e = LocalDate.parse("2026-08-01");
        assertEquals(BigDecimal.ZERO, TierPricing.prorateUpgrade(null, new BigDecimal("200"), LocalDate.parse("2026-07-10"), s, e));
        assertEquals(BigDecimal.ZERO, TierPricing.prorateUpgrade(new BigDecimal("200"), new BigDecimal("100"), LocalDate.parse("2026-07-10"), s, e));
        assertEquals(BigDecimal.ZERO, TierPricing.prorateUpgrade(new BigDecimal("100"), new BigDecimal("200"), e, s, e));
    }

    @Test
    void monthlyBillsEveryMonth() {
        assertEquals(new BigDecimal("100"), TierPricing.chargeForMonth(Period.MONTHLY, new BigDecimal("100"),
                LocalDate.parse("2026-03-15"), YearMonth.of(2026, 9)));
    }

    @Test
    void annualBillsOnStartMonthAndAnniversariesOnly() {
        LocalDate start = LocalDate.parse("2026-03-15");
        BigDecimal a = new BigDecimal("1000");
        assertEquals(a, TierPricing.chargeForMonth(Period.ANNUAL, a, start, YearMonth.of(2026, 3)));
        assertEquals(BigDecimal.ZERO, TierPricing.chargeForMonth(Period.ANNUAL, a, start, YearMonth.of(2026, 4)));
        assertEquals(a, TierPricing.chargeForMonth(Period.ANNUAL, a, start, YearMonth.of(2027, 3)));
        assertEquals(BigDecimal.ZERO, TierPricing.chargeForMonth(Period.ANNUAL, a, start, YearMonth.of(2026, 2)));
        assertTrue(TierPricing.prepaid(Period.ANNUAL, start, YearMonth.of(2026, 4)));
        assertFalse(TierPricing.prepaid(Period.ANNUAL, start, YearMonth.of(2026, 3)));
        assertFalse(TierPricing.prepaid(Period.MONTHLY, start, YearMonth.of(2026, 4)));
    }
}
