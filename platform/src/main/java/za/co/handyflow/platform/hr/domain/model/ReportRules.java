package za.co.handyflow.platform.hr.domain.model;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDate;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Map;

/** The arithmetic and the privacy rules behind the HR reports. No database, no clock. */
public final class ReportRules {

    private ReportRules() {}

    /** Groups smaller than this are not shown, so nobody can be picked out of a gender, race or disability count. */
    public static final int MIN_GROUP = 5;
    public static final int MAX_DAYS = 366;
    public static final int STALE_LEAVE_DAYS = 14;
    public static final int LONG_CASE_DAYS = 90;
    public static final int WARNING_SOON_DAYS = 30;

    /** One line of a breakdown. When {@code hidden}, the count is null. */
    public record Group(String label, Integer count, boolean hidden) {}

    public static String periodProblem(LocalDate from, LocalDate to) {
        if (from == null || to == null) return "Choose the start and end of the period.";
        if (to.isBefore(from)) return "The end of the period is before the start.";
        if (ChronoUnit.DAYS.between(from, to) + 1 > MAX_DAYS) return "Choose a period of " + MAX_DAYS + " days or less.";
        return null;
    }

    public static long days(LocalDate from, LocalDate to) { return ChronoUnit.DAYS.between(from, to) + 1; }

    /** Percentage of a whole to one decimal place; null when there is no whole to measure against. */
    public static BigDecimal percent(long part, long whole) {
        if (whole <= 0) return null;
        return BigDecimal.valueOf(part * 100).divide(BigDecimal.valueOf(whole), 1, RoundingMode.HALF_UP);
    }

    /** Leavers as a percentage of the average of the opening and closing headcount. Null when nobody was employed. */
    public static BigDecimal turnoverPercent(int leavers, int opening, int closing) {
        double average = (opening + closing) / 2.0;
        if (average <= 0) return null;
        return BigDecimal.valueOf(leavers * 100.0 / average).setScale(1, RoundingMode.HALF_UP);
    }

    /** Turns a rate for a period into a rate for a year. */
    public static BigDecimal annualise(BigDecimal ratePercent, long periodDays) {
        if (ratePercent == null || periodDays <= 0) return null;
        return ratePercent.multiply(BigDecimal.valueOf(365)).divide(BigDecimal.valueOf(periodDays), 1, RoundingMode.HALF_UP);
    }

    public static String ageBand(LocalDate born, LocalDate asAt) {
        if (born == null) return "Not recorded";
        int age = (int) ChronoUnit.YEARS.between(born, asAt);
        if (age < 25) return "Under 25";
        if (age < 35) return "25 to 34";
        if (age < 45) return "35 to 44";
        if (age < 55) return "45 to 54";
        return "55 and over";
    }

    public static final List<String> AGE_BANDS = List.of("Under 25", "25 to 34", "35 to 44", "45 to 54", "55 and over", "Not recorded");
    public static final List<String> TENURE_BANDS = List.of("Under 1 year", "1 to 3 years", "3 to 5 years", "5 to 10 years", "10 years or more");

    public static String tenureBand(LocalDate start, LocalDate asAt) {
        long years = start == null ? 0 : ChronoUnit.YEARS.between(start, asAt);
        if (years < 1) return "Under 1 year";
        if (years < 3) return "1 to 3 years";
        if (years < 5) return "3 to 5 years";
        if (years < 10) return "5 to 10 years";
        return "10 years or more";
    }

    /** A breakdown in the given order (then any labels not listed, largest first). Nothing is hidden. */
    public static List<Group> groups(Map<String, Integer> counts, List<String> order) {
        List<Group> out = new ArrayList<>();
        for (String label : order) if (counts.containsKey(label)) out.add(new Group(label, counts.get(label), false));
        counts.entrySet().stream().filter(e -> !order.contains(e.getKey()))
                .sorted(Map.Entry.<String, Integer>comparingByValue().reversed().thenComparing(Map.Entry.comparingByKey()))
                .forEach(e -> out.add(new Group(e.getKey(), e.getValue(), false)));
        return out;
    }

    /**
     * A breakdown for sensitive characteristics. Groups smaller than {@link #MIN_GROUP} are hidden. If exactly one group is hidden it could be
     * worked out from the total, so the next smallest group is hidden too. Counts are never shown with a total that would give them away.
     */
    public static List<Group> suppressed(Map<String, Integer> counts) {
        List<Map.Entry<String, Integer>> sorted = counts.entrySet().stream()
                .filter(e -> e.getValue() > 0)
                .sorted(Map.Entry.<String, Integer>comparingByValue().thenComparing(Map.Entry.comparingByKey())).toList();
        long small = sorted.stream().filter(e -> e.getValue() < MIN_GROUP).count();
        int hide = (int) small;
        if (small == 1 && sorted.size() > 1) hide = 2;
        List<Group> out = new ArrayList<>();
        for (int i = 0; i < sorted.size(); i++) {
            Map.Entry<String, Integer> e = sorted.get(i);
            out.add(i < hide ? new Group(e.getKey(), null, true) : new Group(e.getKey(), e.getValue(), false));
        }
        out.sort(Comparator.comparing((Group g) -> g.hidden() ? 1 : 0).thenComparing((Group g) -> g.count() == null ? 0 : -g.count()).thenComparing(Group::label));
        return out;
    }

    /** Hours to one decimal place from minutes. */
    public static BigDecimal hours(long minutes) {
        return BigDecimal.valueOf(minutes).divide(BigDecimal.valueOf(60), 1, RoundingMode.HALF_UP);
    }

    /** What an employer pays for a payslip: earnings plus the employer's UIF and skills levy. */
    public static BigDecimal employerCost(BigDecimal earnings, BigDecimal uifEmployer, BigDecimal sdl) {
        return nz(earnings).add(nz(uifEmployer)).add(nz(sdl));
    }

    public static BigDecimal nz(BigDecimal v) { return v == null ? BigDecimal.ZERO : v; }

    /** Pay runs whose figures count towards totals. Drafts, runs still being checked and cancelled runs do not. */
    public static boolean countsTowardsTotals(String payRunStatus) {
        return PayRunWorkflowRules.FINALISED.equals(payRunStatus) || PayRunWorkflowRules.COMPLETED.equals(payRunStatus);
    }

    /** Whether an open case has been open longer than it should. */
    public static boolean longOpen(LocalDate reportedOn, LocalDate today) {
        return reportedOn != null && ChronoUnit.DAYS.between(reportedOn, today) > LONG_CASE_DAYS;
    }

    public static boolean staleLeave(java.time.Instant requested, java.time.Instant now) {
        return requested != null && ChronoUnit.DAYS.between(requested, now) > STALE_LEAVE_DAYS;
    }
}
