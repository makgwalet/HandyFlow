package za.co.handyflow.platform.fleet.application.internal;

import java.time.LocalDate;

/** Turns the fuel log's optional filters into the exact values the query takes. Pure, so it is tested without Spring. */
final class FuelLogFilter {

    static final int DEFAULT_DAYS = 30;
    static final int MAX_DAYS = 366;

    record Period(LocalDate from, LocalDate to) {}

    private FuelLogFilter() {}

    /** {@code to} defaults to today and {@code from} to 29 days before it; the window is capped at a year. */
    static Period period(LocalDate from, LocalDate to, LocalDate today) {
        LocalDate end = to != null ? to : today;
        LocalDate start = from != null ? from : end.minusDays(DEFAULT_DAYS - 1L);
        if (start.isAfter(end)) throw new IllegalArgumentException("The start date is after the end date");
        if (start.plusDays(MAX_DAYS).isBefore(end)) throw new IllegalArgumentException("Choose a period of 366 days or less");
        return new Period(start, end);
    }

    /** A LIKE pattern for the search text: '%' (match everything) when blank. The text is matched literally. */
    static String pattern(String q) {
        if (q == null || q.isBlank()) return "%";
        String t = q.trim().toLowerCase().replace("\\", "\\\\").replace("%", "\\%").replace("_", "\\_");
        return "%" + t + "%";
    }
}
