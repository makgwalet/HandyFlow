package za.co.handyflow.platform.accounting.domain.rules;

import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;
import static za.co.handyflow.platform.accounting.domain.rules.PositionReportRules.*;

/** A small company across two financial years, worked out by hand. */
class PositionReportRulesTest {

    private static final UUID BANK = UUID.randomUUID(), EQUIPMENT = UUID.randomUUID(), LOAN = UUID.randomUUID(),
            CAPITAL = UUID.randomUUID(), RETAINED = UUID.randomUUID(), CURRENT = UUID.randomUUID(),
            SALES = UUID.randomUUID(), RENT = UUID.randomUUID();

    private static final LocalDate FY25 = LocalDate.of(2025, 1, 1), FY26 = LocalDate.of(2026, 1, 1);

    private static BigDecimal d(String s) { return new BigDecimal(s); }

    private static void eq(String expected, BigDecimal actual) {
        assertTrue(d(expected).compareTo(actual) == 0, "expected " + expected + " but was " + actual);
    }

    private static List<Acct> chart() {
        return List.of(
                new Acct(BANK, "1000", "Bank", "ASSET", BigDecimal.ZERO),
                new Acct(EQUIPMENT, "1500", "Equipment", "ASSET", BigDecimal.ZERO),
                new Acct(LOAN, "2500", "Loan", "LIABILITY", BigDecimal.ZERO),
                new Acct(CAPITAL, "3010", "Share capital", "EQUITY", BigDecimal.ZERO),
                new Acct(RETAINED, "3020", "Retained Earnings", "EQUITY", BigDecimal.ZERO),
                new Acct(CURRENT, "3040", "Current Year Earnings", "EQUITY", BigDecimal.ZERO),
                new Acct(SALES, "4000", "Sales", "INCOME", BigDecimal.ZERO),
                new Acct(RENT, "5000", "Rent", "EXPENSE", BigDecimal.ZERO));
    }

    private static Move dr(UUID a, LocalDate on, String amount) { return new Move(a, on, d(amount), BigDecimal.ZERO); }
    private static Move cr(UUID a, LocalDate on, String amount) { return new Move(a, on, BigDecimal.ZERO, d(amount)); }

    /**
     * 2025: capital 100000 banked; equipment 20000 paid cash; sales 50000; rent 30000  (profit 20000).
     * 2026: loan 10000 received; sales 40000; rent 10000  (profit 30000).
     */
    private static List<Move> twoYears() {
        LocalDate a = LocalDate.of(2025, 1, 10), b = LocalDate.of(2025, 3, 5), c = LocalDate.of(2025, 6, 30),
                e = LocalDate.of(2025, 12, 31), f = LocalDate.of(2026, 1, 1), g = LocalDate.of(2026, 5, 2), h = LocalDate.of(2026, 12, 31);
        List<Move> m = new ArrayList<>();
        m.add(dr(BANK, a, "100000")); m.add(cr(CAPITAL, a, "100000"));
        m.add(dr(EQUIPMENT, b, "20000")); m.add(cr(BANK, b, "20000"));
        m.add(dr(BANK, c, "50000")); m.add(cr(SALES, c, "50000"));
        m.add(dr(RENT, e, "30000")); m.add(cr(BANK, e, "30000"));
        m.add(dr(BANK, f, "10000")); m.add(cr(LOAN, f, "10000"));
        m.add(dr(BANK, g, "40000")); m.add(cr(SALES, g, "40000"));
        m.add(dr(RENT, h, "10000")); m.add(cr(BANK, h, "10000"));
        return m;
    }

    private static Line line(List<Line> lines, String code) {
        return lines.stream().filter(l -> l.code().equals(code)).findFirst().orElse(null);
    }

    @Test void balanceSheetCarriesLastYearsPositionForward() {
        Sheet s = balanceSheet(compute(chart(), twoYears(), FY26, LocalDate.of(2026, 12, 31)));
        eq("140000", line(s.assets(), "1000").amount());       // 100000 - 20000 + 50000 - 30000 + 10000 + 40000 - 10000
        eq("20000", line(s.assets(), "1500").amount());
        eq("160000", s.totalAssets());
        eq("10000", s.totalLiabilities());
        eq("100000", line(s.equity(), "3010").amount());
        eq("20000", line(s.equity(), "3020").amount());         // 2025 profit, rolled into retained earnings
        eq("30000", line(s.equity(), "3040").amount());         // 2026 profit only
        eq("160000", s.totalLiabilities().add(s.totalEquity())); // balances, and not by coincidence
        assertTrue(s.totalAssets().compareTo(s.totalLiabilities().add(s.totalEquity())) == 0);
    }

    @Test void balanceSheetAtEndOfFirstYearHasNoRetainedEarningsYet() {
        Sheet s = balanceSheet(compute(chart(), twoYears(), FY25, LocalDate.of(2025, 12, 31)));
        eq("100000", line(s.assets(), "1000").amount());        // 100000 - 20000 + 50000 - 30000
        eq("120000", s.totalAssets());
        assertNull(line(s.equity(), "3020"));
        eq("20000", line(s.equity(), "3040").amount());
        eq("0", s.totalLiabilities());
        eq("120000", s.totalEquity());
    }

    @Test void movementsAfterTheDateAreIgnored() {
        Sheet s = balanceSheet(compute(chart(), twoYears(), FY26, LocalDate.of(2026, 3, 1)));
        eq("10000", s.totalLiabilities());
        eq("0", line(s.equity(), "3040") == null ? BigDecimal.ZERO : line(s.equity(), "3040").amount());
        eq("20000", line(s.equity(), "3020").amount());
        eq("130000", s.totalAssets());  // bank 100000 + 10000 loan = 110000, plus equipment 20000
        eq("130000", s.totalLiabilities().add(s.totalEquity()));
    }

    @Test void aJournalOnTheDateIsIncludedAndOnTheDayAfterIsNot() {
        List<Move> m = List.of(dr(BANK, LocalDate.of(2026, 3, 31), "5"), cr(SALES, LocalDate.of(2026, 3, 31), "5"),
                dr(BANK, LocalDate.of(2026, 4, 1), "7"), cr(SALES, LocalDate.of(2026, 4, 1), "7"));
        Sheet s = balanceSheet(compute(chart(), m, FY26, LocalDate.of(2026, 3, 31)));
        eq("5", s.totalAssets());
        eq("5", line(s.equity(), "3040").amount());
    }

    @Test void theFirstDayOfTheYearIsThisYearAndTheDayBeforeIsLastYear() {
        List<Move> m = List.of(dr(BANK, LocalDate.of(2025, 12, 31), "3"), cr(SALES, LocalDate.of(2025, 12, 31), "3"),
                dr(BANK, LocalDate.of(2026, 1, 1), "4"), cr(SALES, LocalDate.of(2026, 1, 1), "4"));
        Sheet s = balanceSheet(compute(chart(), m, FY26, LocalDate.of(2026, 6, 30)));
        eq("3", line(s.equity(), "3020").amount());
        eq("4", line(s.equity(), "3040").amount());
    }

    @Test void aLossLastYearReducesRetainedEarnings() {
        List<Move> m = List.of(dr(RENT, LocalDate.of(2025, 5, 1), "800"), cr(BANK, LocalDate.of(2025, 5, 1), "800"));
        Sheet s = balanceSheet(compute(chart(), m, FY26, LocalDate.of(2026, 6, 30)));
        eq("-800", line(s.equity(), "3020").amount());
        eq("-800", s.totalAssets());
        eq("-800", s.totalEquity());
    }

    @Test void retainedEarningsAlreadyOnTheBooksAreAddedTo() {
        List<Acct> chart = new ArrayList<>(chart());
        chart.set(4, new Acct(RETAINED, "3020", "Retained Earnings", "EQUITY", d("-5000")));   // 5000 credit brought forward
        List<Move> m = List.of(dr(BANK, LocalDate.of(2025, 2, 1), "100"), cr(SALES, LocalDate.of(2025, 2, 1), "100"));
        Sheet s = balanceSheet(compute(chart, m, FY26, LocalDate.of(2026, 6, 30)));
        eq("5100", line(s.equity(), "3020").amount());
    }

    @Test void openingBalancesAreIncludedOnBothSides() {
        List<Acct> chart = new ArrayList<>(chart());
        chart.set(0, new Acct(BANK, "1000", "Bank", "ASSET", d("8000")));
        chart.set(3, new Acct(CAPITAL, "3010", "Share capital", "EQUITY", d("-8000")));
        Position p = compute(chart, List.of(), FY26, LocalDate.of(2026, 6, 30));
        Sheet s = balanceSheet(p);
        eq("8000", s.totalAssets());
        eq("8000", s.totalEquity());
        List<Row> tb = trialBalance(p);
        eq("8000", tb.stream().map(Row::debit).reduce(BigDecimal.ZERO, BigDecimal::add));
        eq("8000", tb.stream().map(Row::credit).reduce(BigDecimal.ZERO, BigDecimal::add));
    }

    @Test void trialBalanceBalancesAndShowsEveryAccountCumulatively() {
        List<Row> tb = trialBalance(compute(chart(), twoYears(), FY26, LocalDate.of(2026, 12, 31)));
        BigDecimal debits = tb.stream().map(Row::debit).reduce(BigDecimal.ZERO, BigDecimal::add);
        BigDecimal credits = tb.stream().map(Row::credit).reduce(BigDecimal.ZERO, BigDecimal::add);
        assertTrue(debits.compareTo(credits) == 0, debits + " vs " + credits);
        Row bank = tb.stream().filter(r -> r.code().equals("1000")).findFirst().orElseThrow();
        eq("200000", bank.debit());        // gross: 100000 + 50000 + 10000 + 40000
        eq("60000", bank.credit());        // gross: 20000 + 30000 + 10000
        Row retained = tb.stream().filter(r -> r.code().equals("3020")).findFirst().orElseThrow();
        eq("20000", retained.credit());    // 2025 profit
        eq("0", retained.debit());
        Row sales = tb.stream().filter(r -> r.code().equals("4000")).findFirst().orElseThrow();
        eq("40000", sales.credit());       // this year's sales only
        Row rent = tb.stream().filter(r -> r.code().equals("5000")).findFirst().orElseThrow();
        eq("10000", rent.debit());
    }

    @Test void aJournalOnAnUnknownAccountStillShowsInTheTrialBalance() {
        UUID ghost = UUID.randomUUID();
        List<Move> m = List.of(dr(BANK, LocalDate.of(2026, 2, 1), "50"), cr(ghost, LocalDate.of(2026, 2, 1), "50"));
        List<Row> tb = trialBalance(compute(chart(), m, FY26, LocalDate.of(2026, 6, 30)));
        Row unknown = tb.stream().filter(r -> r.type().equals(UNKNOWN_TYPE)).findFirst().orElseThrow();
        eq("50", unknown.credit());
        assertTrue(unknown.name().contains(ghost.toString()));
        BigDecimal debits = tb.stream().map(Row::debit).reduce(BigDecimal.ZERO, BigDecimal::add);
        BigDecimal credits = tb.stream().map(Row::credit).reduce(BigDecimal.ZERO, BigDecimal::add);
        assertTrue(debits.compareTo(credits) == 0);
    }

    @Test void retainedEarningsRowIsAddedWhenTheAccountHasNoMovement() {
        List<Acct> noRetained = new ArrayList<>(chart());
        noRetained.remove(4);
        List<Row> tb = trialBalance(compute(noRetained, twoYears(), FY26, LocalDate.of(2026, 12, 31)));
        Row r = tb.stream().filter(x -> x.code().equals("3020")).findFirst().orElseThrow();
        eq("20000", r.credit());
        assertEquals("Retained Earnings", r.name());
    }

    @Test void priorLossShowsAsADebitOnRetainedEarnings() {
        List<Move> m = List.of(dr(RENT, LocalDate.of(2025, 5, 1), "800"), cr(BANK, LocalDate.of(2025, 5, 1), "800"));
        Row r = trialBalance(compute(chart(), m, FY26, LocalDate.of(2026, 6, 30))).stream()
                .filter(x -> x.code().equals("3020")).findFirst().orElseThrow();
        eq("800", r.debit());
        eq("0", r.credit());
    }

    @Test void accountsWithNothingToShowAreLeftOut() {
        Position p = compute(chart(), List.of(), FY26, LocalDate.of(2026, 6, 30));
        assertTrue(p.rows().isEmpty());
        assertTrue(trialBalance(p).isEmpty());
        Sheet s = balanceSheet(p);
        assertTrue(s.assets().isEmpty() && s.liabilities().isEmpty() && s.equity().isEmpty());
    }

    @Test void priorEarningsThatCancelLeaveNoRetainedEarningsRow() {
        List<Move> m = List.of(dr(BANK, LocalDate.of(2025, 5, 1), "10"), cr(SALES, LocalDate.of(2025, 5, 1), "10"),
                dr(RENT, LocalDate.of(2025, 6, 1), "10"), cr(BANK, LocalDate.of(2025, 6, 1), "10"));
        Position p = compute(chart(), m, FY26, LocalDate.of(2026, 6, 30));
        assertTrue(trialBalance(p).stream().noneMatch(r -> r.code().equals("3020")));
        assertNull(line(balanceSheet(p).equity(), "3020"));
    }

    @Test void withNoEarlierEarningsRetainedEarningsKeepsItsOwnGrossColumns() {
        List<Move> m = List.of(dr(RETAINED, LocalDate.of(2026, 2, 1), "10"), cr(RETAINED, LocalDate.of(2026, 2, 1), "30"),
                dr(BANK, LocalDate.of(2026, 2, 1), "20"));
        Row r = trialBalance(compute(chart(), m, FY26, LocalDate.of(2026, 6, 30))).stream()
                .filter(x -> x.code().equals("3020")).findFirst().orElseThrow();
        eq("10", r.debit());
        eq("30", r.credit());
    }

    @Test void theYearMustNotStartAfterTheReportDate() {
        assertThrows(IllegalArgumentException.class, () -> compute(chart(), List.of(), LocalDate.of(2026, 7, 1), LocalDate.of(2026, 6, 30)));
        assertThrows(IllegalArgumentException.class, () -> compute(chart(), List.of(), null, LocalDate.of(2026, 6, 30)));
        assertThrows(IllegalArgumentException.class, () -> compute(chart(), List.of(), FY26, null));
    }

    @Test void aOneDayYearIsAllowed() {
        LocalDate day = LocalDate.of(2026, 6, 30);
        Position p = compute(chart(), List.of(dr(BANK, day, "1"), cr(SALES, day, "1")), day, day);
        eq("1", p.currentEarningsNet().negate());
    }

    @Test void missingAmountsCountAsZero() {
        Position p = compute(chart(), List.of(new Move(BANK, LocalDate.of(2026, 2, 1), null, d("4"))), FY26, LocalDate.of(2026, 6, 30));
        eq("4", p.rows().get(0).credit());
    }
}
