package za.co.handyflow.platform.hr.domain.model;

import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.time.DayOfWeek;
import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalTime;
import java.util.EnumSet;
import java.util.List;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class AttendanceRulesTest {

    private static final Set<DayOfWeek> WEEKDAYS = EnumSet.range(DayOfWeek.MONDAY, DayOfWeek.FRIDAY);
    private static final AttendanceRules.Schedule STD = new AttendanceRules.Schedule(WEEKDAYS, LocalTime.of(8, 0), LocalTime.of(17, 0), 60);
    private static final WorkingTimeRules.Values RULES = WorkingTimeRules.on(LocalDate.of(2026, 11, 2));
    private static final LocalDate MON = LocalDate.of(2026, 11, 2);
    private static final LocalDate TODAY = LocalDate.of(2026, 11, 20);

    private static Instant at(LocalDate d, int h, int m) { return d.atTime(h, m).atZone(AttendanceRules.ZONE).toInstant(); }

    private static AttendanceRules.Day day(LocalDate d, int worked, Instant firstIn) {
        return AttendanceRules.day(d, STD, false, false, worked, firstIn, false, TODAY, RULES);
    }

    @Test
    void aStandardWeekIsFortyHours() {
        assertThat(STD.dailyMinutes()).isEqualTo(480);
        assertThat(STD.weeklyMinutes()).isEqualTo(2400);
        assertThat(AttendanceRules.scheduleWarnings(STD, RULES)).isEmpty();
    }

    @Test
    void daysParseAndPrintBack() {
        assertThat(AttendanceRules.parseDays("MON, tue,WED")).hasSize(3);
        assertThat(AttendanceRules.parseDays("")).isEmpty();
        assertThat(AttendanceRules.daysCsv(WEEKDAYS)).isEqualTo("MON,TUE,WED,THU,FRI");
    }

    @Test
    void catchesScheduleMistakes() {
        assertThat(AttendanceRules.scheduleProblem("", WEEKDAYS, LocalTime.of(8, 0), LocalTime.of(17, 0), 60)).isNotNull();
        assertThat(AttendanceRules.scheduleProblem("S", Set.of(), LocalTime.of(8, 0), LocalTime.of(17, 0), 60)).contains("working day");
        assertThat(AttendanceRules.scheduleProblem("S", WEEKDAYS, LocalTime.of(17, 0), LocalTime.of(8, 0), 60)).contains("after the start");
        assertThat(AttendanceRules.scheduleProblem("S", WEEKDAYS, LocalTime.of(8, 0), LocalTime.of(9, 0), 90)).contains("whole day");
        assertThat(AttendanceRules.scheduleProblem("S", WEEKDAYS, LocalTime.of(8, 0), LocalTime.of(17, 0), 60)).isNull();
    }

    @Test
    void warnsAboveTheActsOrdinaryHours() {
        var long6 = new AttendanceRules.Schedule(EnumSet.range(DayOfWeek.MONDAY, DayOfWeek.SATURDAY), LocalTime.of(7, 0), LocalTime.of(16, 0), 30);
        var warnings = AttendanceRules.scheduleWarnings(long6, RULES);
        assertThat(warnings.stream().anyMatch(w -> w.contains("a week"))).isTrue();
        assertThat(warnings.stream().anyMatch(w -> w.contains("a day"))).isTrue();
    }

    @Test
    void entriesMustBeSensible() {
        Instant now = at(TODAY, 12, 0);
        assertThat(AttendanceRules.entryProblem(at(MON, 8, 0), at(MON, 17, 0), now)).isNull();
        assertThat(AttendanceRules.entryProblem(at(MON, 8, 0), null, now)).isNull();
        assertThat(AttendanceRules.entryProblem(at(MON, 17, 0), at(MON, 8, 0), now)).contains("after the time in");
        assertThat(AttendanceRules.entryProblem(at(TODAY.plusDays(1), 8, 0), null, now)).contains("future");
        assertThat(AttendanceRules.entryProblem(at(MON, 8, 0), at(MON.plusDays(1), 8, 0), now)).contains("16 hours");
        assertThat(AttendanceRules.entryProblem(null, null, now)).isNotNull();
    }

    @Test
    void oneLongPunchLosesTheBreakButShortOnesAndSplitDaysDoNot() {
        assertThat(AttendanceRules.workedMinutes(List.of(new AttendanceRules.Punch(at(MON, 8, 0), at(MON, 17, 0))), 60, RULES)).isEqualTo(480);
        assertThat(AttendanceRules.workedMinutes(List.of(new AttendanceRules.Punch(at(MON, 8, 0), at(MON, 12, 0))), 60, RULES)).isEqualTo(240);
        assertThat(AttendanceRules.workedMinutes(List.of(
                new AttendanceRules.Punch(at(MON, 8, 0), at(MON, 12, 0)), new AttendanceRules.Punch(at(MON, 13, 0), at(MON, 17, 0))), 60, RULES)).isEqualTo(480);
        assertThat(AttendanceRules.workedMinutes(List.of(new AttendanceRules.Punch(at(MON, 8, 0), null)), 60, RULES)).isEqualTo(0);
    }

    @Test
    void aFullDayIsPresentWithNoOvertime() {
        var d = day(MON, 480, at(MON, 7, 58));
        assertThat(d.status()).isEqualTo("PRESENT");
        assertThat(d.overtimeMinutes()).isEqualTo(0);
        assertThat(d.lateMinutes()).isEqualTo(0);
        assertThat(d.shortMinutes()).isEqualTo(0);
    }

    @Test
    void lateBeyondTheGraceIsLateAndShortIsRecorded() {
        var d = day(MON, 420, at(MON, 9, 0));
        assertThat(d.status()).isEqualTo("LATE");
        assertThat(d.lateMinutes()).isEqualTo(60);
        assertThat(d.shortMinutes()).isEqualTo(60);
        assertThat(day(MON, 480, at(MON, 8, 5)).status()).isEqualTo("PRESENT");
        assertThat(day(MON, 480, at(MON, 8, 6)).status()).isEqualTo("LATE");
    }

    @Test
    void aPastWorkingDayWithNothingIsAbsentButTodayIsStillPending() {
        assertThat(day(MON, 0, null).status()).isEqualTo("ABSENT");
        assertThat(AttendanceRules.day(TODAY, STD, false, false, 0, null, false, TODAY, RULES).status()).isEqualTo("PENDING");
    }

    @Test
    void leaveHolidaysAndRestDaysAreNotAbsence() {
        assertThat(AttendanceRules.day(MON, STD, false, true, 0, null, false, TODAY, RULES).status()).isEqualTo("ON_LEAVE");
        assertThat(AttendanceRules.day(MON, STD, true, false, 0, null, false, TODAY, RULES).status()).isEqualTo("PUBLIC_HOLIDAY");
        assertThat(day(LocalDate.of(2026, 11, 7), 0, null).status()).isEqualTo("REST_DAY");
        assertThat(AttendanceRules.day(MON, STD, false, true, 0, null, false, TODAY, RULES).expectedMinutes()).isEqualTo(0);
    }

    @Test
    void anOpenPunchFromAPastDayIsIncomplete() {
        var d = AttendanceRules.day(MON, STD, false, false, 0, at(MON, 8, 0), true, TODAY, RULES);
        assertThat(d.status()).isEqualTo("INCOMPLETE");
        assertThat(d.flags().stream().anyMatch(f -> f.contains("never out"))).isTrue();
    }

    @Test
    void timeBeyondTheScheduleOnAWorkingDayIsOvertimeAtTheStandardRate() {
        var d = day(MON, 600, at(MON, 8, 0));
        assertThat(d.overtimeMinutes()).isEqualTo(120);
        assertThat(d.overtimeKind()).isEqualTo("OVERTIME");
    }

    @Test
    void anUnscheduledSaturdayIsAllOvertimeAndSundayIsAtTheSundayRate() {
        var sat = day(LocalDate.of(2026, 11, 7), 240, at(LocalDate.of(2026, 11, 7), 8, 0));
        assertThat(sat.overtimeMinutes()).isEqualTo(240);
        assertThat(sat.overtimeKind()).isEqualTo("OVERTIME");
        var sun = day(LocalDate.of(2026, 11, 8), 240, at(LocalDate.of(2026, 11, 8), 8, 0));
        assertThat(sun.overtimeKind()).isEqualTo("SUNDAY");
        assertThat(AttendanceRules.multiplier("SUNDAY", RULES)).isEqualByComparingTo("2.0");
        assertThat(AttendanceRules.multiplier("OVERTIME", RULES)).isEqualByComparingTo("1.5");
    }

    @Test
    void holidayWorkIsRecordedButItsPayIsLeftToHr() {
        var d = AttendanceRules.day(MON, STD, true, false, 300, at(MON, 8, 0), false, TODAY, RULES);
        assertThat(d.overtimeKind()).isEqualTo("PUBLIC_HOLIDAY");
        assertThat(d.flags().stream().anyMatch(f -> f.contains("s 18"))).isTrue();
    }

    @Test
    void sundayAsAnOrdinaryDayIsFlaggedNotPremiumPaid() {
        var sunSched = new AttendanceRules.Schedule(EnumSet.of(DayOfWeek.SUNDAY), LocalTime.of(8, 0), LocalTime.of(16, 0), 30);
        LocalDate sun = LocalDate.of(2026, 11, 8);
        var d = AttendanceRules.day(sun, sunSched, false, false, 540, at(sun, 8, 0), false, TODAY, RULES);
        assertThat(d.overtimeMinutes()).isEqualTo(90);
        assertThat(d.overtimeKind()).isEqualTo("OVERTIME");
        assertThat(d.flags().stream().anyMatch(f -> f.contains("s 16"))).isTrue();
    }

    @Test
    void flagsMoreThanTwelveHoursInADay() {
        assertThat(day(MON, 13 * 60, at(MON, 6, 0)).flags().stream().anyMatch(f -> f.contains("12 hours"))).isTrue();
    }

    @Test
    void flagsTooMuchOvertimeInAWeek() {
        var days = new java.util.ArrayList<AttendanceRules.Day>();
        for (int i = 0; i < 5; i++) days.add(day(MON.plusDays(i), 480 + 150, at(MON.plusDays(i), 8, 0)));
        assertThat(AttendanceRules.weekFlags(days, RULES).stream().anyMatch(f -> f.contains("s 10"))).isTrue();
        var fine = List.of(day(MON, 480 + 60, at(MON, 8, 0)));
        assertThat(AttendanceRules.weekFlags(fine, RULES)).isEmpty();
    }

    @Test
    void holidayHoursDoNotCountTowardsTheWeeklyLimits() {
        var h = AttendanceRules.day(MON, STD, true, false, 600, at(MON, 8, 0), false, TODAY, RULES);
        assertThat(AttendanceRules.weekFlags(List.of(h), RULES)).isEmpty();
    }

    @Test
    void flagsLessThanTwelveHoursRest() {
        assertThat(AttendanceRules.restProblem(at(MON, 22, 0), at(MON.plusDays(1), 6, 0), RULES)).contains("s 15");
        assertThat(AttendanceRules.restProblem(at(MON, 17, 0), at(MON.plusDays(1), 8, 0), RULES)).isNull();
        assertThat(AttendanceRules.restProblem(null, at(MON, 8, 0), RULES)).isNull();
    }

    @Test
    void hourlyRateFollowsSection35() {
        // R18,000 x 12 / 52 / 45 = R92.3077
        assertThat(AttendanceRules.hourlyRate(new BigDecimal("18000"), 45 * 60)).isEqualByComparingTo("92.3077");
        assertThat(AttendanceRules.hourlyRate(null, 2700)).isEqualByComparingTo("0");
        assertThat(AttendanceRules.hourlyRate(new BigDecimal("18000"), 0)).isEqualByComparingTo("0");
    }

    @Test
    void overtimePayIsHoursTimesRateTimesMultiplier() {
        BigDecimal hourly = AttendanceRules.hourlyRate(new BigDecimal("18000"), 45 * 60);
        // 2 hours at 1.5 = 3 x 92.3077 = 276.92
        assertThat(AttendanceRules.overtimePay(hourly, 120, new BigDecimal("1.5"))).isEqualByComparingTo("276.92");
        // 90 minutes at 2.0 = 3 x 92.3077 = 276.92
        assertThat(AttendanceRules.overtimePay(hourly, 90, new BigDecimal("2.0"))).isEqualByComparingTo("276.92");
        assertThat(AttendanceRules.overtimePay(hourly, 0, new BigDecimal("1.5"))).isEqualByComparingTo("0");
    }

    @Test
    void thresholdRulesAreVersionedAndRefuseUnknownDates() {
        assertThat(WorkingTimeRules.on(LocalDate.of(2026, 4, 30)).earningsThreshold()).isEqualByComparingTo("261748.45");
        assertThat(WorkingTimeRules.on(LocalDate.of(2026, 5, 1)).earningsThreshold()).isEqualByComparingTo("269600.90");
        assertThatThrownBy(() -> WorkingTimeRules.on(LocalDate.of(2024, 1, 1))).isInstanceOf(IllegalStateException.class).hasMessageContaining("no working-time rules");
        assertThat(WorkingTimeRules.aboveThreshold(new BigDecimal("22466"), RULES)).isFalse();
        assertThat(WorkingTimeRules.aboveThreshold(new BigDecimal("22467"), RULES)).isTrue();
        assertThat(WorkingTimeRules.aboveThreshold(null, RULES)).isFalse();
    }
}
