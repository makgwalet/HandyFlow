package za.co.handyflow.platform.shared;

import org.junit.jupiter.api.Test;

import java.lang.reflect.Proxy;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;

/** The payroll bureau's calculator: the year's medical credit and retirement cap, who gets a medical credit, and how age is taken. */
class SarsPayrollCalculatorTest {

    private static BigDecimal d(String v) { return new BigDecimal(v); }

    private static SarsTaxTable bracket(int year, String from, String to, String base, String rate) {
        SarsTaxTable t = new SarsTaxTable();
        t.id = UUID.randomUUID(); t.taxYear = year; t.incomeFrom = d(from); t.incomeTo = to == null ? null : d(to); t.baseTax = d(base); t.marginalRate = d(rate);
        return t;
    }

    private static SarsTaxRebate rebate(int year, String type, String amount) {
        SarsTaxRebate r = new SarsTaxRebate();
        r.id = UUID.randomUUID(); r.taxYear = year; r.rebateType = type; r.amount = d(amount);
        return r;
    }

    private final List<SarsTaxTable> tables = new ArrayList<>();
    private final List<SarsTaxRebate> rebates = new ArrayList<>();

    private SarsPayrollCalculator calc() {
        SarsTaxTableRepository tr = (SarsTaxTableRepository) Proxy.newProxyInstance(getClass().getClassLoader(), new Class<?>[]{SarsTaxTableRepository.class},
                (p, m, a) -> "findByTaxYear".equals(m.getName()) ? tables.stream().filter(t -> t.getTaxYear() == (int) a[0]).toList() : null);
        SarsTaxRebateRepository rr = (SarsTaxRebateRepository) Proxy.newProxyInstance(getClass().getClassLoader(), new Class<?>[]{SarsTaxRebateRepository.class},
                (p, m, a) -> "findByTaxYear".equals(m.getName()) ? rebates.stream().filter(t -> t.getTaxYear() == (int) a[0]).toList() : null);
        return new SarsPayrollCalculator(tr, rr);
    }

    /** The 2027 table, as migration V485 loads it. */
    private void table2027() {
        tables.add(bracket(2027, "0", "245100", "0", "18"));
        tables.add(bracket(2027, "245100", "383100", "44118", "26"));
        tables.add(bracket(2027, "383100", "530200", "79998", "31"));
        tables.add(bracket(2027, "530200", "695800", "125599", "36"));
        tables.add(bracket(2027, "695800", "887000", "185215", "39"));
        tables.add(bracket(2027, "887000", "1878600", "259783", "41"));
        tables.add(bracket(2027, "1878600", null, "666339", "45"));
    }

    private SarsPayrollResult run(String salary, String medical, String pension, LocalDate dob, int year) {
        return calc().calculate(d(salary), BigDecimal.ZERO, d(pension), d(medical), dob, year, d("1000000"));
    }

    @Test
    void theMedicalCreditIsTheYearsAndOnlyForSomeoneWhoPaysAScheme() {
        table2027();
        SarsPayrollResult with = run("40000", "2000", "0", LocalDate.of(1990, 1, 1), 2027);
        SarsPayrollResult without = run("40000", "0", "0", LocalDate.of(1990, 1, 1), 2027);
        assertEquals(0, d("4512.00").compareTo(with.medicalTaxCredit()));     // R376 x 12, not the old R364
        assertEquals(0, BigDecimal.ZERO.compareTo(without.medicalTaxCredit()));
        // 480 000 taxable: 79 998 + 31% of 96 900 = 110 037; less rebate 17 820 = 92 217
        assertEquals(0, d("7684.75").compareTo(without.payeAmount()));        // 92 217 / 12
        assertEquals(0, d("7308.75").compareTo(with.payeAmount()));           // less 376 a month
    }

    @Test
    void theRetirementDeductionIsCappedAtTheYearsAmount() {
        table2027();
        // 200 000 a month: 2.4m a year. 27.5% would be 660 000; the 2027 cap is 430 000 (it was 350 000).
        SarsPayrollResult r = run("200000", "0", "100000", LocalDate.of(1990, 1, 1), 2027);
        assertEquals(0, d("1970000.00").compareTo(r.taxableIncome()));        // 2 400 000 - 430 000
    }

    @Test
    void ageIsTakenAtTheLastDayOfTheTaxYearNotFromTheClock() {
        table2027();
        // Born 1 Feb 1962: 65 on 1 Feb 2027, inside the 2027 tax year, so the secondary rebate applies for the whole year.
        SarsPayrollResult turns65 = run("40000", "0", "0", LocalDate.of(1962, 2, 1), 2027);
        assertEquals(0, d("9765.00").compareTo(turns65.secondaryRebate()));
        // Born 1 Mar 1962: 65 only on 1 Mar 2027, after the year ends.
        SarsPayrollResult notYet = run("40000", "0", "0", LocalDate.of(1962, 3, 1), 2027);
        assertEquals(0, BigDecimal.ZERO.compareTo(notYet.secondaryRebate()));
        // 75 in the year gives the tertiary rebate as well.
        SarsPayrollResult old = run("40000", "0", "0", LocalDate.of(1952, 2, 28), 2027);
        assertEquals(0, d("3249.00").compareTo(old.tertiaryRebate()));
    }

    @Test
    void aRebateRowForTheYearWinsOverTheBuiltInFigures() {
        table2027();
        rebates.add(rebate(2027, "PRIMARY", "18000.00"));
        assertEquals(0, d("18000.00").compareTo(run("40000", "0", "0", LocalDate.of(1990, 1, 1), 2027).primaryRebate()));
    }

    @Test
    void theBuiltInRebatesFollowTheYear() {
        tables.add(bracket(2026, "0", null, "0", "18"));
        assertEquals(0, d("17235.00").compareTo(run("40000", "0", "0", null, 2026).primaryRebate()));
        table2027();
        assertEquals(0, d("17820.00").compareTo(run("40000", "0", "0", null, 2027).primaryRebate()));
    }

    @Test
    void aYearNotKnownYetUsesTheLatestKnownYear() {
        assertEquals(0, d("376.00").compareTo(SarsPayrollCalculator.figuresFor(2028).mtcTaxpayer()));
        assertEquals(0, d("364.00").compareTo(SarsPayrollCalculator.figuresFor(2020).mtcTaxpayer()));
    }

    @Test
    void uifAndSdlFollowTheCeilingAndTheThreshold() {
        table2027();
        SarsPayrollResult r = run("40000", "0", "0", null, 2027);
        assertEquals(0, d("177.12").compareTo(r.uifEmployee()));
        assertEquals(0, d("400.00").compareTo(r.sdlAmount()));
        SarsPayrollResult small = calc().calculate(d("40000"), null, null, null, null, 2027, d("400000"));
        assertEquals(0, BigDecimal.ZERO.compareTo(small.sdlAmount()));
    }

    @Test
    void withoutATaxTableTheTaxIsNilButTheDeductionsAreKept() {
        SarsPayrollResult r = run("40000", "2000", "1000", null, 2031);
        assertEquals(0, BigDecimal.ZERO.compareTo(r.payeAmount()));
        assertEquals(0, d("2000").compareTo(r.medicalAid()));
        assertEquals(0, d("1000").compareTo(r.pension()));
    }
}
