package za.co.handyflow.platform.payrollbureau.domain.rules;

import org.junit.jupiter.api.Test;
import za.co.handyflow.platform.payrollbureau.domain.rules.EtiRules.*;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.YearMonth;

import static org.junit.jupiter.api.Assertions.*;

class EtiRulesTest {

    private static final YearMonth NOV = YearMonth.of(2026, 11);
    private static BigDecimal d(String s) { return new BigDecimal(s); }

    private static Facts f(String rem, int claimed) {
        return new Facts(EtiRules.SA_ID, LocalDate.of(2000, 5, 10), LocalDate.of(2026, 1, 5), false, false, claimed, true, d(rem), null);
    }

    private static Result calc(Facts f) { return EtiRules.calculate(f, NOV, EtiRules.FROM_2025_04_01); }

    @Test
    void belowR2500IsAShareOfPay() {
        assertEquals(d("1200.00"), calc(f("2000", 0)).incentive());
        assertEquals(d("600.00"), calc(f("2000", 12)).incentive());
    }

    @Test
    void middleBandIsFlat() {
        assertEquals(d("1500.00"), calc(f("4000", 0)).incentive());
        assertEquals(d("750.00"), calc(f("4000", 12)).incentive());
        assertEquals(d("1500.00"), calc(f("2500", 5)).incentive());
    }

    @Test
    void upperBandTapersToZero() {
        assertEquals(d("1125.00"), calc(f("6000", 0)).incentive());
        assertEquals(d("562.50"), calc(f("6000", 12)).incentive());
        assertEquals(d("1500.00"), calc(f("5500", 0)).incentive());
    }

    @Test
    void atOrAboveR7500NothingIsPaid() {
        Result r = calc(f("7500", 0));
        assertFalse(r.eligible());
        assertTrue(r.reasons().contains("REMUNERATION_ABOVE_LIMIT"));
        assertEquals(d("0"), r.incentive());
    }

    @Test
    void partMonthIsGrossedUpThenApportioned() {
        Facts p = new Facts(EtiRules.SA_ID, LocalDate.of(2000, 5, 10), LocalDate.of(2026, 1, 5), false, false, 0, true, d("2187.50"), d("140"));
        Result r = calc(p);
        assertEquals(d("2500.00"), r.remunerationUsed());
        assertEquals(d("1500.00"), r.fullMonthIncentive());
        assertEquals(d("1312.50"), r.incentive());
    }

    @Test
    void hoursOfAFullMonthOrMoreAreNotGrossedDown() {
        Facts p = new Facts(EtiRules.SA_ID, LocalDate.of(2000, 5, 10), LocalDate.of(2026, 1, 5), false, false, 0, true, d("4000"), d("180"));
        assertEquals(d("1500.00"), calc(p).incentive());
    }

    @Test
    void ageIsTestedAtTheEndOfTheMonth() {
        assertEquals(29, EtiRules.ageAtEndOfMonth(LocalDate.of(1997, 4, 1), NOV));
        assertEquals(29, EtiRules.ageAtEndOfMonth(LocalDate.of(1997, 11, 30), YearMonth.of(2026, 11)));
        assertEquals(28, EtiRules.ageAtEndOfMonth(LocalDate.of(1997, 11, 30), YearMonth.of(2026, 10)) );
        // turns 30 on 30 Nov 2026: 30 at the end of November, so out; still 29 at the end of October
        Facts turning30 = new Facts(EtiRules.SA_ID, LocalDate.of(1996, 11, 30), LocalDate.of(2026, 1, 5), false, false, 0, true, d("4000"), null);
        assertFalse(calc(turning30).eligible());
        assertTrue(EtiRules.calculate(turning30, YearMonth.of(2026, 10), EtiRules.FROM_2025_04_01).eligible());
        Facts under18 = new Facts(EtiRules.SA_ID, LocalDate.of(2008, 12, 1), LocalDate.of(2026, 1, 5), false, false, 0, true, d("4000"), null);
        assertTrue(calc(under18).reasons().contains("AGE_OUT_OF_RANGE"));
    }

    @Test
    void eachDisqualifierIsNamed() {
        Facts base = f("4000", 0);
        assertTrue(calc(new Facts(EtiRules.OTHER, base.dateOfBirth(), base.employedFrom(), false, false, 0, true, d("4000"), null)).reasons().contains("ID_NOT_QUALIFYING"));
        assertTrue(calc(new Facts(EtiRules.ASYLUM, base.dateOfBirth(), base.employedFrom(), false, false, 0, true, d("4000"), null)).eligible());
        assertTrue(calc(new Facts(EtiRules.SA_ID, base.dateOfBirth(), base.employedFrom(), true, false, 0, true, d("4000"), null)).reasons().contains("DOMESTIC_WORKER"));
        assertTrue(calc(new Facts(EtiRules.SA_ID, base.dateOfBirth(), base.employedFrom(), false, true, 0, true, d("4000"), null)).reasons().contains("CONNECTED_PERSON"));
        assertTrue(calc(new Facts(EtiRules.SA_ID, base.dateOfBirth(), base.employedFrom(), false, false, 0, false, d("4000"), null)).reasons().contains("BELOW_MINIMUM_WAGE"));
        assertTrue(calc(new Facts(EtiRules.SA_ID, base.dateOfBirth(), LocalDate.of(2013, 9, 30), false, false, 0, true, d("4000"), null)).reasons().contains("EMPLOYED_BEFORE_SCHEME"));
        assertTrue(calc(new Facts(EtiRules.SA_ID, null, base.employedFrom(), false, false, 0, true, d("4000"), null)).reasons().contains("NO_DATE_OF_BIRTH"));
    }

    @Test
    void twentyFourMonthsIsTheLimit() {
        assertTrue(calc(f("4000", 23)).eligible());
        Result r = calc(f("4000", 24));
        assertFalse(r.eligible());
        assertTrue(r.reasons().contains("MONTHS_EXHAUSTED"));
    }

    @Test
    void notBeforeTheRulesStartOrAfterTheyEnd() {
        assertTrue(EtiRules.calculate(f("4000", 0), YearMonth.of(2025, 3), EtiRules.FROM_2025_04_01).reasons().contains("RULES_NOT_IN_FORCE"));
        assertTrue(EtiRules.calculate(f("4000", 0), YearMonth.of(2029, 3), EtiRules.FROM_2025_04_01).reasons().contains("INCENTIVE_ENDED"));
        assertTrue(EtiRules.calculate(f("4000", 0), YearMonth.of(2029, 2), EtiRules.FROM_2025_04_01).eligible());
    }

    @Test
    void incentiveIsLimitedToThePayeAndTheRestCarriesForward() {
        var a = EtiRules.applyToPaye(d("1500"), d("0"), d("900"));
        assertEquals(d("900.00"), a.used());
        assertEquals(d("600.00"), a.carriedForward());
        var b = EtiRules.applyToPaye(d("1000"), d("600"), d("5000"));
        assertEquals(d("1600.00"), b.used());
        assertEquals(d("0.00"), b.carriedForward());
        var c = EtiRules.applyToPaye(d("1000"), null, d("0"));
        assertEquals(d("0.00"), c.used());
        assertEquals(d("1000.00"), c.carriedForward());
    }
}
