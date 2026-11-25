package za.co.handyflow.platform.complianceservices.domain.model;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.YearMonth;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * How the practice dashboard groups what is already recorded. Pure, so the buckets and the pipeline order can be tested.
 * The dashboard reads and counts; it records nothing and scores nothing.
 */
public final class DashboardRules {

    private DashboardRules() {}

    public static final int WEEKS = 8;
    public static final int MONTHS = 6;
    public static final List<String> QUOTE_ORDER = List.of("DRAFT", "PENDING_APPROVAL", "APPROVED", "SENT", "EXPIRED", "ACCEPTED", "DECLINED", "WITHDRAWN");
    public static final List<String> TENDER_ORDER = List.of("DRAFT", "IN_PREPARATION", "INTERNAL_REVIEW", "READY_TO_SUBMIT", "SUBMITTED", "CLARIFICATION",
            "SHORTLISTED", "NEGOTIATION", "AWARDED", "UNSUCCESSFUL", "WITHDRAWN");

    public record Week(LocalDate start, int count) {}
    public record Month(String month, BigDecimal amount, int count) {}
    public record Stage(String status, int count, BigDecimal value) {}
    public record Dated(LocalDate date, BigDecimal amount) {}
    public record Behind(BigDecimal amount, int count) {}

    /** Items due this week, next week and so on. Anything before today is counted apart as overdue, and anything past the last week is left out. */
    public static List<Week> weeks(LocalDate today, List<LocalDate> dates) {
        int[] counts = new int[WEEKS];
        for (LocalDate d : dates) {
            if (d == null || d.isBefore(today)) continue;
            long week = java.time.temporal.ChronoUnit.DAYS.between(today, d) / 7;
            if (week < WEEKS) counts[(int) week]++;
        }
        List<Week> out = new ArrayList<>();
        for (int i = 0; i < WEEKS; i++) out.add(new Week(today.plusDays(7L * i), counts[i]));
        return out;
    }

    public static int overdue(LocalDate today, List<LocalDate> dates) {
        return (int) dates.stream().filter(d -> d != null && d.isBefore(today)).count();
    }

    /** Amounts by the month they fall due, starting with this month. Earlier amounts are not mixed in; see {@link #behind}. */
    public static List<Month> months(LocalDate today, List<Dated> items) {
        YearMonth first = YearMonth.from(today);
        Map<YearMonth, BigDecimal> sums = new LinkedHashMap<>();
        Map<YearMonth, Integer> counts = new LinkedHashMap<>();
        for (int i = 0; i < MONTHS; i++) { sums.put(first.plusMonths(i), BigDecimal.ZERO); counts.put(first.plusMonths(i), 0); }
        for (Dated d : items) {
            if (d.date() == null) continue;
            YearMonth m = YearMonth.from(d.date());
            if (!sums.containsKey(m)) continue;
            sums.merge(m, d.amount() == null ? BigDecimal.ZERO : d.amount(), BigDecimal::add);
            counts.merge(m, 1, Integer::sum);
        }
        List<Month> out = new ArrayList<>();
        sums.forEach((m, v) -> out.add(new Month(m.toString(), v, counts.get(m))));
        return out;
    }

    /** Amounts that fell due in an earlier month and are still waiting. */
    public static Behind behind(LocalDate today, List<Dated> items) {
        YearMonth first = YearMonth.from(today);
        BigDecimal sum = BigDecimal.ZERO;
        int n = 0;
        for (Dated d : items) {
            if (d.date() == null || !YearMonth.from(d.date()).isBefore(first)) continue;
            sum = sum.add(d.amount() == null ? BigDecimal.ZERO : d.amount());
            n++;
        }
        return new Behind(sum, n);
    }

    /** A quote's pipeline status: a sent quote past its date has lapsed, whatever the stored status says. */
    public static String quoteStage(String status, LocalDate validUntil, LocalDate today) {
        return "SENT".equals(status) && validUntil != null && validUntil.isBefore(today) ? "EXPIRED" : status;
    }

    /** Counts and values by stage in the given order. Stages with nothing in them are left out; unknown stages follow at the end. */
    public static List<Stage> pipeline(List<String> order, List<String> statuses, List<BigDecimal> values) {
        Map<String, int[]> counts = new LinkedHashMap<>();
        Map<String, BigDecimal> sums = new LinkedHashMap<>();
        for (int i = 0; i < statuses.size(); i++) {
            String s = statuses.get(i);
            counts.computeIfAbsent(s, k -> new int[1])[0]++;
            sums.merge(s, values.get(i) == null ? BigDecimal.ZERO : values.get(i), BigDecimal::add);
        }
        List<String> keys = new ArrayList<>(order.stream().filter(counts::containsKey).toList());
        counts.keySet().stream().filter(k -> !order.contains(k)).sorted().forEach(keys::add);
        return keys.stream().map(k -> new Stage(k, counts.get(k)[0], sums.get(k))).toList();
    }
}
