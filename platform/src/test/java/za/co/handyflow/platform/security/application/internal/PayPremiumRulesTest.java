package za.co.handyflow.platform.security.application.internal;

import org.junit.jupiter.api.Test;
import za.co.handyflow.platform.shared.HandyFlowException;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.function.Predicate;

import static org.junit.jupiter.api.Assertions.*;
import static za.co.handyflow.platform.security.application.internal.PayPremiumRules.*;

class PayPremiumRulesTest {

    private static final ZoneId SAST = ZoneId.of("Africa/Johannesburg");
    private static final Settings ON = new Settings(true, new BigDecimal("1.50"), new BigDecimal("2.00"));
    private static final Predicate<LocalDate> NATIONAL = d -> isHoliday(d, Overrides.NONE);
    private static Instant at(String sast) { return LocalDate.parse(sast.substring(0, 10)).atTime(java.time.LocalTime.parse(sast.substring(11))).atZone(SAST).toInstant(); }
    private static BigDecimal bd(String s) { return new BigDecimal(s); }
    private static String show(List<Line> l) { return l == null ? "null" : l.stream().map(x -> x.type() + " " + x.hours() + " @" + x.payRateCents()).toList().toString(); }

    // ---- settings ----

    @Test
    void multipliersMustBeBetween150And400() {
        assertEquals(bd("1.50"), validate(true, bd("1.5"), bd("2")).sundayMultiplier());
        assertEquals(bd("4.00"), validate(true, bd("1.5"), bd("4.00")).holidayMultiplier());
        for (String bad : new String[]{"1.49", "4.01", "0", "-2", "10"}) {
            assertEquals("PREMIUM_MULTIPLIER", assertThrows(HandyFlowException.class, () -> validate(true, bd(bad), bd("2"))).getErrorCode(), bad);
            assertEquals("PREMIUM_MULTIPLIER", assertThrows(HandyFlowException.class, () -> validate(true, bd("2"), bd(bad))).getErrorCode(), bad);
        }
    }

    @Test
    void aMissingOrTooPreciseMultiplierIsRefused() {
        assertThrows(HandyFlowException.class, () -> validate(true, null, bd("2")));
        assertThrows(HandyFlowException.class, () -> validate(true, bd("2"), null));
        assertThrows(HandyFlowException.class, () -> validate(true, bd("1.505"), bd("2")));
        assertEquals(bd("1.50"), validate(true, bd("1.500"), bd("2")).sundayMultiplier()); // trailing zeros are fine
    }

    @Test
    void theMultipliersAreCheckedEvenWhenSwitchedOff() {
        assertThrows(HandyFlowException.class, () -> validate(false, bd("1.2"), bd("2")));
        assertFalse(validate(false, bd("1.5"), bd("2")).enabled());
    }

    // ---- days ----

    @Test
    void holidayBeatsSundayAndSundayBeatsNormal() {
        assertEquals(Kind.HOLIDAY, kindOf(LocalDate.parse("2026-08-09"), NATIONAL));   // a Sunday and Women's Day
        assertEquals(Kind.SUNDAY, kindOf(LocalDate.parse("2026-11-22"), NATIONAL));
        assertEquals(Kind.NORMAL, kindOf(LocalDate.parse("2026-11-23"), NATIONAL));
        assertEquals(Kind.HOLIDAY, kindOf(LocalDate.parse("2026-08-10"), NATIONAL));   // the observed Monday
    }

    @Test
    void aCompanyCanAddAndRemoveDays() {
        Overrides o = new Overrides(Map.of(LocalDate.parse("2026-12-30"), "Proclaimed"), Set.of(LocalDate.parse("2026-04-03")));
        assertTrue(isHoliday(LocalDate.parse("2026-12-30"), o));
        assertFalse(isHoliday(LocalDate.parse("2026-04-03"), o));     // Good Friday removed
        assertTrue(isHoliday(LocalDate.parse("2026-04-06"), o));      // Family Day still there
        assertTrue(isHoliday(LocalDate.parse("2026-04-03"), Overrides.NONE));
    }

    // ---- cutting a shift ----

    @Test
    void anOrdinaryWeekdayShiftHasNoPremiumLines() {
        assertNull(lines(at("2026-11-23T06:00"), at("2026-11-23T18:00"), SAST, 9, 10000, ON, NATIONAL));
        assertNull(lines(at("2026-11-20T22:00"), at("2026-11-21T08:00"), SAST, 9, 10000, ON, NATIONAL)); // Friday night into Saturday
    }

    @Test
    void whenSwitchedOffNothingChangesEvenOnASunday() {
        assertNull(lines(at("2026-11-22T06:00"), at("2026-11-22T18:00"), SAST, 9, 10000, Settings.OFF, NATIONAL));
        assertNull(lines(at("2026-11-22T06:00"), at("2026-11-22T18:00"), SAST, 9, 10000, null, NATIONAL));
        assertNull(lines(at("2026-11-22T06:00"), at("2026-11-22T18:00"), SAST, 9, 10000, new Settings(false, bd("3"), bd("4")), NATIONAL));
    }

    @Test
    void aWholeSundayShiftIsOneSundayLineEvenPastTheStandardDay() {
        List<Line> l = lines(at("2026-11-22T06:00"), at("2026-11-22T18:00"), SAST, 9, 10000, ON, NATIONAL);
        assertEquals("[SUNDAY 12.00 @15000]", show(l));
    }

    @Test
    void aNightShiftFromSaturdayIntoSundayIsCutAtMidnight() {
        List<Line> l = lines(at("2026-11-21T18:00"), at("2026-11-22T06:00"), SAST, 9, 10000, ON, NATIONAL);
        assertEquals("[REGULAR 6.00 @10000, SUNDAY 6.00 @15000]", show(l));
    }

    @Test
    void aNightShiftFromSundayIntoMondayKeepsOvertimeOnTheMondayPart() {
        // 22:00 Sun to 08:00 Mon: 10 hours. Standard 9 counted from the start: 2 on Sunday, 7 on Monday, then 1 hour of overtime on Monday.
        List<Line> l = lines(at("2026-11-22T22:00"), at("2026-11-23T08:00"), SAST, 9, 10000, ON, NATIONAL);
        assertEquals("[REGULAR 7.00 @10000, OVERTIME 1.00 @15000, SUNDAY 2.00 @15000]", show(l));
    }

    @Test
    void aPublicHolidayPaysTheHolidayRate() {
        List<Line> l = lines(at("2026-12-25T07:00"), at("2026-12-25T19:00"), SAST, 9, 10000, ON, NATIONAL);
        assertEquals("[PUBLIC_HOLIDAY 12.00 @20000]", show(l));
    }

    @Test
    void aHolidayOnASundayPaysTheHolidayRateNotBoth() {
        List<Line> l = lines(at("2026-08-09T08:00"), at("2026-08-09T16:00"), SAST, 9, 10000, ON, NATIONAL);
        assertEquals("[PUBLIC_HOLIDAY 8.00 @20000]", show(l));
    }

    @Test
    void theObservedMondayIsAHoliday() {
        List<Line> l = lines(at("2026-08-10T08:00"), at("2026-08-10T16:00"), SAST, 9, 10000, ON, NATIONAL);
        assertEquals("[PUBLIC_HOLIDAY 8.00 @20000]", show(l));
    }

    @Test
    void aShiftAcrossFromHolidayIntoSundayHasBothLines() {
        // 26 Dec 2026 is a Saturday and Day of Goodwill; 27 Dec is an ordinary Sunday
        List<Line> l = lines(at("2026-12-26T20:00"), at("2026-12-27T04:00"), SAST, 9, 10000, ON, NATIONAL);
        assertEquals("[SUNDAY 4.00 @15000, PUBLIC_HOLIDAY 4.00 @20000]", show(l));
    }

    @Test
    void aRemovedHolidayIsPaidAsTheDayItIs() {
        Overrides o = new Overrides(Map.of(), Set.of(LocalDate.parse("2026-12-25")));
        assertNull(lines(at("2026-12-25T07:00"), at("2026-12-25T19:00"), SAST, 9, 10000, ON, d -> isHoliday(d, o))); // Friday: ordinary
    }

    @Test
    void anAddedDayIsPaidAsAHoliday() {
        Overrides o = new Overrides(Map.of(LocalDate.parse("2026-11-24"), "Proclaimed"), Set.of());
        assertEquals("[PUBLIC_HOLIDAY 8.00 @20000]", show(lines(at("2026-11-24T08:00"), at("2026-11-24T16:00"), SAST, 9, 10000, ON, d -> isHoliday(d, o))));
    }

    @Test
    void multipliersAreUsed() {
        Settings s = new Settings(true, bd("1.75"), bd("2.50"));
        assertEquals("[SUNDAY 8.00 @17500]", show(lines(at("2026-11-22T08:00"), at("2026-11-22T16:00"), SAST, 9, 10000, s, NATIONAL)));
        assertEquals("[PUBLIC_HOLIDAY 8.00 @25000]", show(lines(at("2026-12-25T08:00"), at("2026-12-25T16:00"), SAST, 9, 10000, s, NATIONAL)));
    }

    @Test
    void theCompanysZoneDecidesWhereMidnightIs() {
        // 23:00 Saturday to 03:00 Sunday in South Africa is 21:00 to 01:00 UTC: only one hour is Sunday there.
        Instant s = at("2026-11-21T23:00"), e = at("2026-11-22T03:00");
        assertEquals("[REGULAR 1.00 @10000, SUNDAY 3.00 @15000]", show(lines(s, e, SAST, 9, 10000, ON, NATIONAL)));
        assertEquals("[REGULAR 3.00 @10000, SUNDAY 1.00 @15000]", show(lines(s, e, ZoneId.of("UTC"), 9, 10000, ON, NATIONAL)));
    }

    // ---- the minutes ----

    @Test
    void theBucketsAlwaysAddUpToTheShift() {
        String[][] shifts = {{"2026-11-21T18:00", "2026-11-22T06:00"}, {"2026-11-22T22:00", "2026-11-23T08:00"}, {"2026-12-24T20:00", "2026-12-26T09:00"}, {"2026-11-22T00:00", "2026-11-22T00:01"}, {"2026-08-08T23:30", "2026-08-10T00:30"}};
        for (String[] s : shifts) {
            Buckets b = apportion(at(s[0]), at(s[1]), SAST, 9, NATIONAL);
            assertEquals(java.time.Duration.between(at(s[0]), at(s[1])).toMinutes(), (long) b.total(), s[0]);
        }
    }

    @Test
    void secondsAreHandledLikeTheOrdinarySplit() {
        Instant s = Instant.parse("2026-11-22T04:00:30Z"), e = Instant.parse("2026-11-22T04:30:29Z");
        assertEquals(29L, java.time.Duration.between(s, e).toMinutes());
        assertEquals(29L, (long) apportion(s, e, SAST, 9, NATIONAL).total());
    }

    @Test
    void aZeroOrBackwardsShiftHasNothing() {
        assertEquals(0L, (long) apportion(at("2026-11-22T08:00"), at("2026-11-22T08:00"), SAST, 9, NATIONAL).total());
        assertEquals(0L, (long) apportion(at("2026-11-22T09:00"), at("2026-11-22T08:00"), SAST, 9, NATIONAL).total());
        assertNull(lines(at("2026-11-22T09:00"), at("2026-11-22T08:00"), SAST, 9, 10000, ON, NATIONAL));
    }

    // ---- money ----

    @Test
    void premiumRateIsRoundedHalfUpToTheCent() {
        assertEquals(15000, premiumRateCents(10000, bd("1.50")));
        assertEquals(13125, premiumRateCents(8750, bd("1.50")));
        assertEquals(1502, premiumRateCents(1001, bd("1.50")));   // 1501.5 -> 1502
        assertEquals(18749, premiumRateCents(8333, bd("2.25")));  // 18749.25
    }

    @Test
    void hoursAreWholeMinutesToTwoDecimals() {
        assertEquals(bd("1.50"), hours(90));
        assertEquals(bd("0.33"), hours(20));
        assertEquals(bd("12.00"), hours(720));
        assertEquals(bd("0.00"), hours(0));
    }

    @Test
    void overtimeOnAPremiumDayIsPaidAtTheDayRateAndNeverLess() {
        Settings s = new Settings(true, bd("1.50"), bd("2.00"));
        // 12 hours on a holiday: the 3 hours beyond the standard day are not paid less than the other 9.
        List<Line> l = lines(at("2026-12-25T06:00"), at("2026-12-25T18:00"), SAST, 9, 10000, s, NATIONAL);
        assertEquals(1, l.size());
        assertEquals(20000, l.get(0).payRateCents());
        assertTrue(l.get(0).payRateCents() >= (int) (10000 * 1.5));
    }

    // ---- how a pay statement reads a line ----

    @Test
    void premiumAndOvertimeLinesAreReadAtTheirOwnRate() {
        for (String t : new String[]{"OVERTIME", "SUNDAY", "PUBLIC_HOLIDAY"}) assertTrue(paidAtLineRate(t), t);
        for (String t : new String[]{"REGULAR", "ALLOWANCE", "DEDUCTION", null}) assertFalse(paidAtLineRate(t), String.valueOf(t));
    }

    @Test
    void onlyOvertimeKeepsItsHoursInTheOvertimeColumn() {
        assertTrue(hoursInOvertimeColumn("OVERTIME"));
        for (String t : new String[]{"REGULAR", "SUNDAY", "PUBLIC_HOLIDAY"}) assertFalse(hoursInOvertimeColumn(t), t);
    }

    @Test
    void labelsReadWell() {
        assertEquals("SUNDAY", label("SUNDAY"));
        assertEquals("PUBLIC HOLIDAY", label("PUBLIC_HOLIDAY"));
        assertEquals("REGULAR", label("REGULAR"));
        assertEquals("", label(null));
    }
}
