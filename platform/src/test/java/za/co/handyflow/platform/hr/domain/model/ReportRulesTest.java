package za.co.handyflow.platform.hr.domain.model;

import org.junit.jupiter.api.Test;
import za.co.handyflow.platform.hr.domain.model.ReportRules.Group;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

class ReportRulesTest {

    private static Map<String, Integer> counts(Object... kv) {
        Map<String, Integer> m = new LinkedHashMap<>();
        for (int i = 0; i < kv.length; i += 2) m.put((String) kv[i], (Integer) kv[i + 1]);
        return m;
    }

    @Test
    void aPeriodMustBeChosenInOrderAndNotBeLongerThanAYear() {
        LocalDate d = LocalDate.of(2026, 1, 1);
        assertThat(ReportRules.periodProblem(null, d)).isNotNull();
        assertThat(ReportRules.periodProblem(d, null)).isNotNull();
        assertThat(ReportRules.periodProblem(d, d.minusDays(1))).contains("before");
        assertThat(ReportRules.periodProblem(d, d)).isNull();
        assertThat(ReportRules.periodProblem(d, d.plusDays(365))).isNull();       // 366 days counting both ends
        assertThat(ReportRules.periodProblem(d, d.plusDays(366))).contains("366");
    }

    @Test
    void daysCountBothEnds() {
        assertThat(ReportRules.days(LocalDate.of(2026, 1, 1), LocalDate.of(2026, 1, 1))).isEqualTo(1L);
        assertThat(ReportRules.days(LocalDate.of(2026, 1, 1), LocalDate.of(2026, 3, 31))).isEqualTo(90L);
    }

    @Test
    void percentagesAreRoundedToOneDecimalAndNeedSomethingToMeasureAgainst() {
        assertThat(ReportRules.percent(1, 3)).isEqualByComparingTo("33.3");
        assertThat(ReportRules.percent(2, 3)).isEqualByComparingTo("66.7");
        assertThat(ReportRules.percent(0, 10)).isEqualByComparingTo("0.0");
        assertThat(ReportRules.percent(5, 0)).isNull();
    }

    @Test
    void turnoverUsesTheAverageHeadcount() {
        assertThat(ReportRules.turnoverPercent(5, 100, 100)).isEqualByComparingTo("5.0");
        assertThat(ReportRules.turnoverPercent(5, 80, 120)).isEqualByComparingTo("5.0");
        assertThat(ReportRules.turnoverPercent(3, 10, 20)).isEqualByComparingTo("20.0");
        assertThat(ReportRules.turnoverPercent(0, 50, 50)).isEqualByComparingTo("0.0");
        assertThat(ReportRules.turnoverPercent(2, 0, 0)).isNull();
    }

    @Test
    void aYearlyRateScalesFromThePeriod() {
        assertThat(ReportRules.annualise(new BigDecimal("2.0"), 365)).isEqualByComparingTo("2.0");
        assertThat(ReportRules.annualise(new BigDecimal("2.0"), 91)).isEqualByComparingTo("8.0");
        assertThat(ReportRules.annualise(null, 91)).isNull();
        assertThat(ReportRules.annualise(new BigDecimal("2.0"), 0)).isNull();
    }

    @Test
    void ageBandsChangeOnTheBirthday() {
        LocalDate on = LocalDate.of(2026, 10, 22);
        assertThat(ReportRules.ageBand(LocalDate.of(2001, 10, 23), on)).isEqualTo("Under 25");    // turns 25 tomorrow
        assertThat(ReportRules.ageBand(LocalDate.of(2001, 10, 22), on)).isEqualTo("25 to 34");
        assertThat(ReportRules.ageBand(LocalDate.of(1991, 10, 22), on)).isEqualTo("35 to 44");
        assertThat(ReportRules.ageBand(LocalDate.of(1981, 10, 22), on)).isEqualTo("45 to 54");
        assertThat(ReportRules.ageBand(LocalDate.of(1971, 10, 22), on)).isEqualTo("55 and over");
        assertThat(ReportRules.ageBand(null, on)).isEqualTo("Not recorded");
    }

    @Test
    void tenureBandsChangeOnTheAnniversary() {
        LocalDate on = LocalDate.of(2026, 10, 22);
        assertThat(ReportRules.tenureBand(LocalDate.of(2025, 10, 23), on)).isEqualTo("Under 1 year");
        assertThat(ReportRules.tenureBand(LocalDate.of(2025, 10, 22), on)).isEqualTo("1 to 3 years");
        assertThat(ReportRules.tenureBand(LocalDate.of(2023, 10, 22), on)).isEqualTo("3 to 5 years");
        assertThat(ReportRules.tenureBand(LocalDate.of(2021, 10, 22), on)).isEqualTo("5 to 10 years");
        assertThat(ReportRules.tenureBand(LocalDate.of(2016, 10, 22), on)).isEqualTo("10 years or more");
    }

    @Test
    void aBreakdownFollowsTheGivenOrderThenLargestFirst() {
        List<Group> g = ReportRules.groups(counts("b", 2, "z", 9, "a", 2, "Under 1 year", 4), List.of("Under 1 year", "1 to 3 years"));
        assertThat(g).extracting(Group::label).containsExactly("Under 1 year", "z", "a", "b");
        assertThat(g.stream().allMatch(x -> !x.hidden() && x.count() != null)).isTrue();
    }

    @Test
    void smallGroupsAreHidden() {
        List<Group> g = ReportRules.suppressed(counts("A", 40, "B", 3, "C", 2));
        assertThat(g).extracting(Group::label).containsExactly("A", "B", "C");
        assertThat(g.get(0).count()).isEqualTo(40);
        assertThat(g.get(1).hidden()).isTrue();
        assertThat(g.get(1).count()).isNull();
        assertThat(g.get(2).hidden()).isTrue();
    }

    @Test
    void aLoneHiddenGroupCannotBeWorkedOutFromTheTotal() {
        // 30, 12 and 3: if only the 3 were hidden it would be total minus the rest. The next smallest is hidden as well.
        List<Group> g = ReportRules.suppressed(counts("A", 30, "B", 12, "C", 3));
        assertThat(g.stream().filter(x -> !x.hidden()).map(Group::label).toList()).containsExactly("A");
        assertThat(g.stream().filter(Group::hidden).map(Group::label).toList()).containsExactly("B", "C");
        assertThat(g.stream().filter(Group::hidden).allMatch(x -> x.count() == null)).isTrue();
    }

    @Test
    void largeGroupsAreShownAndEmptyGroupsLeftOut() {
        List<Group> g = ReportRules.suppressed(counts("A", 30, "B", 12, "C", 0));
        assertThat(g.stream().map(Group::label).toList()).containsExactly("A", "B");
        assertThat(g.stream().noneMatch(Group::hidden)).isTrue();
        List<Group> four = ReportRules.suppressed(counts("A", 4));
        assertThat(four).hasSize(1);
        assertThat(four.get(0).hidden()).isTrue();
        List<Group> five = ReportRules.suppressed(counts("A", 5));
        assertThat(five.get(0).hidden()).isFalse();
        assertThat(five.get(0).count()).isEqualTo(5);
    }

    @Test
    void hoursAndEmployerCost() {
        assertThat(ReportRules.hours(90)).isEqualByComparingTo("1.5");
        assertThat(ReportRules.hours(100)).isEqualByComparingTo("1.7");
        assertThat(ReportRules.employerCost(new BigDecimal("1000.00"), new BigDecimal("10.00"), new BigDecimal("10.00"))).isEqualByComparingTo("1020.00");
        assertThat(ReportRules.employerCost(new BigDecimal("1000.00"), null, null)).isEqualByComparingTo("1000.00");
    }

    @Test
    void onlyFinishedPayRunsCount() {
        assertThat(ReportRules.countsTowardsTotals("FINALISED")).isTrue();
        assertThat(ReportRules.countsTowardsTotals("COMPLETED")).isTrue();
        for (String s : List.of("DRAFT", "PROCESSING", "PREPARED", "APPROVED", "CANCELLED")) assertThat(ReportRules.countsTowardsTotals(s)).as(s).isFalse();
    }

    @Test
    void longOpenAndStaleAreMeasuredFromTheirDates() {
        LocalDate today = LocalDate.of(2026, 10, 22);
        assertThat(ReportRules.longOpen(today.minusDays(90), today)).isFalse();
        assertThat(ReportRules.longOpen(today.minusDays(91), today)).isTrue();
        assertThat(ReportRules.longOpen(null, today)).isFalse();
        Instant now = Instant.parse("2026-10-22T10:00:00Z");
        assertThat(ReportRules.staleLeave(now.minusSeconds(14 * 86400L), now)).isFalse();
        assertThat(ReportRules.staleLeave(now.minusSeconds(15 * 86400L), now)).isTrue();
    }
}
